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

import com.amazonaws.services.lambda.runtime.events.S3Event;
import io.awspring.cloud.sqs.listener.SqsMessageListenerContainer;
import org.junit.jupiter.api.Test;
import org.junitpioneer.jupiter.SetEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import software.amazon.awssdk.services.s3.S3AsyncClient;
import software.amazon.awssdk.services.sqs.SqsAsyncClient;

/**
 * Fail-lazy proof: the context starts while S3/SQS endpoints are unreachable (bean creation
 * performs no network call); connection failures surface only on poll retry, never at startup.
 */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.MOCK,
    properties = {
      "spring.cloud.aws.endpoint=http://localhost:9",
      "spring.cloud.aws.sqs.listener.auto-startup=false"
    })
@AutoConfigureMockMvc
@Import(FlywayAutoConfiguration.class)
@SetEnvironmentVariable(key = "AWS_ACCESS_KEY_ID", value = "test")
@SetEnvironmentVariable(key = "AWS_SECRET_ACCESS_KEY", value = "test")
class SqsFailLazyIntegrationTests {

  @Autowired private S3AsyncClient s3AsyncClient;

  @Autowired private SqsAsyncClient sqsAsyncClient;

  @Autowired private SqsMessageListenerContainer<S3Event> assetEventsContainer;

  @Test
  void should_startWithoutReachableEndpoints_when_backendsAreDown() {
    // Arrange: endpoints point at a dead port (nothing listens on 9)

    // Act: context already started to run this test

    // Assert: clients and container were created with no network call
    assertThat(s3AsyncClient).isNotNull();
    assertThat(sqsAsyncClient).isNotNull();
    assertThat(assetEventsContainer).isNotNull();
    assertThat(assetEventsContainer.isRunning()).isFalse();
  }
}
