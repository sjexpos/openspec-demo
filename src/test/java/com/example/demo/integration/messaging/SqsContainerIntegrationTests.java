/**********
 This project is free software; you can redistribute it and/or modify it under
 the terms of the GNU General Public License as published by the
 Free Software Foundation; either version 3.0 of the License, or (at your
 option) any later version. (See <https://www.gnu.org/licenses/gpl-3.0.html>.)

 This project is distributed in the hope that it will be useful, but WITHOUT
 ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 FOR A PARTICULAR PURPOSE.  See the GNU General Public License for
 more details.

 You should have received a copy of the GNU General Public License
 along with this project; if not, write to the Free Software Foundation, Inc.,
 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301  USA
 **********/
// Copyright (c) 2026-2027 Sergio Exposito.  All rights reserved.              

package com.example.demo.integration.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.amazonaws.services.lambda.runtime.events.S3Event;
import com.example.demo.infrastructure.messaging.sqs.AssetEventsListener;
import io.awspring.cloud.sqs.listener.SqsMessageListenerContainer;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junitpioneer.jupiter.SetEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import software.amazon.awssdk.core.async.AsyncRequestBody;
import software.amazon.awssdk.services.s3.S3AsyncClient;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.sqs.SqsAsyncClient;
import software.amazon.awssdk.services.sqs.model.PurgeQueueRequest;

/**
 * Proves the managed listener foundation against LocalStack: starter-owned async clients, the
 * always-created container, and the S3 to SQS to {@code S3Event} round trip. No business or
 * database assertions (KAN-13 owns them).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@Import(FlywayAutoConfiguration.class)
@SetEnvironmentVariable(key = "AWS_ACCESS_KEY_ID", value = "test")
@SetEnvironmentVariable(key = "AWS_SECRET_ACCESS_KEY", value = "test")
class SqsContainerIntegrationTests {

  private static final String BUCKET = "develop-assets";

  private static final String QUEUE = "develop-assets-events-queue";

  private static final String OBJECT_KEY = "brands/images/0123456789abcdef0123456789abcdef";

  @Autowired private ApplicationContext context;

  @Autowired private S3AsyncClient s3AsyncClient;

  @Autowired private SqsAsyncClient sqsAsyncClient;

  @Autowired private SqsMessageListenerContainer<S3Event> assetEventsContainer;

  @MockitoSpyBean private AssetEventsListener listener;

  @AfterEach
  void stopContainer() throws Exception {
    assetEventsContainer.stop();
    clearInvocations(listener);
    s3AsyncClient
        .deleteObject(
            software.amazon.awssdk.services.s3.model.DeleteObjectRequest.builder()
                .bucket(BUCKET)
                .key(OBJECT_KEY)
                .build())
        .get();
  }

  @Test
  void should_injectAsyncClientsAndContainer_when_contextStarts() {
    // Arrange: beans autowired by Spring

    // Act: resolve beans from the context

    // Assert
    assertThat(context.getBeansOfType(S3AsyncClient.class)).hasSize(1);
    assertThat(context.getBeansOfType(SqsAsyncClient.class)).hasSize(1);
    assertThat(assetEventsContainer).isNotNull();
    assertThat(assetEventsContainer.isRunning()).isFalse();
  }

  @Test
  void should_deliverS3EventToListener_when_objectIsUploaded() throws Exception {
    // Arrange: clean queue so only this upload is observed
    purgeQueue();
    clearInvocations(listener);

    // Act: upload triggers the bucket notification fan-out to SQS
    s3AsyncClient
        .putObject(
            PutObjectRequest.builder().bucket(BUCKET).key(OBJECT_KEY).build(),
            AsyncRequestBody.fromString("kan-16 round-trip"))
        .get();
    assetEventsContainer.start();

    // Assert: the seam listener receives the S3Event with bucket and decoded key
    verify(listener, org.mockito.Mockito.timeout(30000).times(1))
        .onAssetEvent(
            argThat(
                event -> {
                  if (event.getRecords().size() != 1) {
                    return false;
                  }
                  var record = event.getRecords().get(0);
                  String decoded =
                      URLDecoder.decode(
                          record.getS3().getObject().getKey(), StandardCharsets.UTF_8);
                  return BUCKET.equals(record.getS3().getBucket().getName())
                      && OBJECT_KEY.equals(decoded);
                }));
  }

  @Test
  void should_startEmpty_when_queueHasNoMessages() throws Exception {
    // Arrange: clean queue, nothing is ever published
    purgeQueue();
    clearInvocations(listener);

    // Act
    assetEventsContainer.start();
    assertThat(assetEventsContainer.isRunning()).isTrue();
    org.awaitility.Awaitility.await()
        .during(Duration.ofSeconds(7))
        .atMost(Duration.ofSeconds(10))
        .until(() -> true);

    // Assert: container polls an empty queue without poison or invocations
    assertThat(assetEventsContainer.isRunning()).isTrue();
    verify(listener, never()).onAssetEvent(org.mockito.ArgumentMatchers.any());
  }

  private void purgeQueue() throws Exception {
    String queueUrl =
        sqsAsyncClient.getQueueUrl(builder -> builder.queueName(QUEUE)).get().queueUrl();
    sqsAsyncClient.purgeQueue(PurgeQueueRequest.builder().queueUrl(queueUrl).build()).get();
    // Purge is eventually consistent on LocalStack; give it a beat.
    org.awaitility.Awaitility.await()
        .atMost(Duration.ofSeconds(10))
        .until(() -> queueDepth(queueUrl) == 0);
  }

  private int queueDepth(String queueUrl) throws Exception {
    var attributes =
        sqsAsyncClient
            .getQueueAttributes(
                builder ->
                    builder
                        .queueUrl(queueUrl)
                        .attributeNames(
                            software.amazon.awssdk.services.sqs.model.QueueAttributeName
                                .APPROXIMATE_NUMBER_OF_MESSAGES))
            .get()
            .attributes();
    return Integer.parseInt(
        attributes.getOrDefault(
            software.amazon.awssdk.services.sqs.model.QueueAttributeName
                .APPROXIMATE_NUMBER_OF_MESSAGES,
            "0"));
  }
}
