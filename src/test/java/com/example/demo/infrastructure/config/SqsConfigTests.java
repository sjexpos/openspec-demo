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

package com.example.demo.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.amazonaws.services.lambda.runtime.events.S3Event;
import com.example.demo.infrastructure.messaging.sqs.SqsAcknowledgementLoggingCallback;
import io.awspring.cloud.autoconfigure.AwsClientCustomizer;
import io.awspring.cloud.autoconfigure.sqs.SqsProperties;
import io.awspring.cloud.sqs.config.SqsMessageListenerContainerFactory;
import io.awspring.cloud.sqs.listener.SqsContainerOptions;
import io.awspring.cloud.sqs.listener.SqsMessageListenerContainer;
import io.awspring.cloud.sqs.listener.acknowledgement.AcknowledgementOrdering;
import io.awspring.cloud.sqs.listener.acknowledgement.AcknowledgementResultCallback;
import io.awspring.cloud.sqs.listener.acknowledgement.handler.AcknowledgementMode;
import io.micrometer.observation.ObservationRegistry;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sqs.SqsAsyncClient;
import software.amazon.awssdk.services.sqs.SqsAsyncClientBuilder;

/**
 * Unit tests for {@code SqsConfig}: no Spring context, no network. The starter auto-configures the
 * client; these tests prove the customizer and the factory option contract.
 */
class SqsConfigTests {

  private static final Duration API_CALL_TIMEOUT = Duration.ofMillis(1500);

  private SqsConfig configWith(AwsSqsProperties properties) {
    return new SqsConfig(
        properties, new SqsProperties(), io.micrometer.observation.ObservationRegistry.NOOP);
  }

  private AwsSqsProperties gapRecord() {
    return new AwsSqsProperties(
        "develop-assets-events-queue", Duration.ofSeconds(3), 10, API_CALL_TIMEOUT);
  }

  @Test
  void should_applyApiCallTimeout_when_customizerRuns() {
    // Arrange
    SqsConfig config = configWith(gapRecord());
    SqsAsyncClientBuilder builder =
        SqsAsyncClient.builder()
            .region(Region.US_EAST_1)
            .credentialsProvider(
                StaticCredentialsProvider.create(AwsBasicCredentials.create("test", "test")));
    AwsClientCustomizer<SqsAsyncClientBuilder> customizer = config.sqsApiTimeoutCustomizer();

    // Act
    customizer.customize(builder);

    // Assert
    try (SqsAsyncClient client = builder.build()) {
      assertThat(client.serviceClientConfiguration().overrideConfiguration().apiCallTimeout())
          .hasValue(API_CALL_TIMEOUT);
    }
  }

  private SqsProperties libraryProperties() {
    SqsProperties properties = new SqsProperties();
    SqsProperties.Listener listener = new SqsProperties.Listener();
    listener.setMaxConcurrentMessages(10);
    listener.setMaxMessagesPerPoll(10);
    listener.setPollTimeout(Duration.ofSeconds(20));
    listener.setMaxDelayBetweenPolls(Duration.ofSeconds(10));
    listener.setAutoStartup(true);
    properties.setListener(listener);
    properties.setObservationEnabled(true);
    return properties;
  }

  private SqsAsyncClient sqsClient() {
    return SqsAsyncClient.builder()
        .region(Region.US_EAST_1)
        .credentialsProvider(
            StaticCredentialsProvider.create(AwsBasicCredentials.create("test", "test")))
        .build();
  }

  private SqsContainerOptions optionsOf(SqsMessageListenerContainerFactory<S3Event> factory) {
    SqsMessageListenerContainer<S3Event> container = factory.createContainer("test-queue");
    return container.getContainerOptions();
  }

  @Test
  void should_buildFactoryWithOrderedOnSuccess_when_propertiesAreValid() {
    // Arrange
    SqsConfig config = new SqsConfig(gapRecord(), libraryProperties(), ObservationRegistry.NOOP);
    AcknowledgementResultCallback<S3Event> callback = new SqsAcknowledgementLoggingCallback();

    // Act
    SqsMessageListenerContainerFactory<S3Event> factory;
    try (SqsAsyncClient client = sqsClient()) {
      factory =
          config.sqsListenerContainerFactory(client, config.sqsListenerTaskExecutor(), callback);
    }

    // Assert
    SqsContainerOptions options = optionsOf(factory);
    assertThat(options.getAcknowledgementMode()).isEqualTo(AcknowledgementMode.ON_SUCCESS);
    assertThat(options.getAcknowledgementOrdering()).isEqualTo(AcknowledgementOrdering.ORDERED);
    assertThat(options.getAcknowledgementInterval()).isEqualTo(Duration.ofSeconds(3));
    assertThat(options.getAcknowledgementThreshold()).isEqualTo(10);
    assertThat(options.getMaxConcurrentMessages()).isEqualTo(10);
    assertThat(options.getMaxMessagesPerPoll()).isEqualTo(10);
    assertThat(options.getPollTimeout()).isEqualTo(Duration.ofSeconds(20));
    assertThat(options.getMaxDelayBetweenPolls()).isEqualTo(Duration.ofSeconds(10));
  }

  @Test
  void should_wireBackpressureAndCallbackAndExecutor_when_factoryIsBuilt() {
    // Arrange
    SqsConfig config = new SqsConfig(gapRecord(), libraryProperties(), ObservationRegistry.NOOP);
    AcknowledgementResultCallback<S3Event> callback = new SqsAcknowledgementLoggingCallback();
    org.springframework.core.task.TaskExecutor executor = config.sqsListenerTaskExecutor();

    // Act
    SqsMessageListenerContainerFactory<S3Event> factory;
    try (SqsAsyncClient client = sqsClient()) {
      factory = config.sqsListenerContainerFactory(client, executor, callback);
    }

    // Assert
    SqsContainerOptions options = optionsOf(factory);
    assertThat(options.getBackPressureHandlerFactory()).isNotNull();
    assertThat(options.getBackPressureMode())
        .isEqualTo(io.awspring.cloud.sqs.listener.BackPressureMode.AUTO);
    assertThat(options.getBackPressureHandlerFactory().createBackPressureHandler(options))
        .isInstanceOf(
            io.awspring.cloud.sqs.listener.backpressure.BatchAwareBackPressureHandler.class);
    assertThat(options.getComponentsTaskExecutor()).isSameAs(executor);
    assertThat(options.getMessageConverter()).isNotNull();
    SqsMessageListenerContainer<S3Event> container = factory.createContainer("test-queue");
    assertThat(container.getAcknowledgementResultCallback()).isNotNull();
  }

  @Test
  void should_useNoopRegistry_when_libraryObservationIsDisabled() {
    // Arrange
    SqsProperties properties = libraryProperties();
    properties.setObservationEnabled(false);
    SqsConfig config = new SqsConfig(gapRecord(), properties, ObservationRegistry.NOOP);
    AcknowledgementResultCallback<S3Event> callback = new SqsAcknowledgementLoggingCallback();

    // Act
    SqsMessageListenerContainerFactory<S3Event> factory;
    try (SqsAsyncClient client = sqsClient()) {
      factory =
          config.sqsListenerContainerFactory(client, config.sqsListenerTaskExecutor(), callback);
    }

    // Assert
    assertThat(optionsOf(factory).getObservationRegistry()).isSameAs(ObservationRegistry.NOOP);
  }

  @Test
  void should_useMicrometerRegistry_when_libraryObservationIsEnabled() { // Arrange
    ObservationRegistry registry = ObservationRegistry.create();
    SqsConfig config = new SqsConfig(gapRecord(), libraryProperties(), registry);
    AcknowledgementResultCallback<S3Event> callback = new SqsAcknowledgementLoggingCallback();

    // Act
    SqsMessageListenerContainerFactory<S3Event> factory;
    try (SqsAsyncClient client = sqsClient()) {
      factory =
          config.sqsListenerContainerFactory(client, config.sqsListenerTaskExecutor(), callback);
    }

    // Assert
    assertThat(optionsOf(factory).getObservationRegistry()).isSameAs(registry);
  }

  @Test
  void should_wireQueueAndListener_when_containerIsBuilt() {
    // Arrange
    SqsConfig config = new SqsConfig(gapRecord(), libraryProperties(), ObservationRegistry.NOOP);
    com.example.demo.infrastructure.messaging.sqs.AssetEventsListener listener =
        org.mockito.Mockito.mock(
            com.example.demo.infrastructure.messaging.sqs.AssetEventsListener.class);
    SqsMessageListenerContainerFactory<S3Event> factory;
    try (SqsAsyncClient client = sqsClient()) {
      factory =
          config.sqsListenerContainerFactory(
              client, config.sqsListenerTaskExecutor(), new SqsAcknowledgementLoggingCallback());
    }

    // Act
    SqsMessageListenerContainer<S3Event> container = config.assetEventsContainer(factory, listener);

    // Assert
    assertThat(container).isNotNull();
    assertThat(container.getQueueNames()).containsExactly("develop-assets-events-queue");
    assertThat(container.getPayloadDeserializationType()).isEqualTo(S3Event.class);
    S3Event event = new S3Event(java.util.List.of());
    io.awspring.cloud.sqs.listener.AsyncMessageListener<S3Event> asyncListener =
        container.getMessageListener();
    ((io.awspring.cloud.sqs.listener.TaskExecutorAware) asyncListener)
        .setTaskExecutor(config.sqsListenerTaskExecutor());
    asyncListener
        .onMessage(new org.springframework.messaging.support.GenericMessage<>(event))
        .join();
    org.mockito.Mockito.verify(listener).onAssetEvent(event);
  }

  @Test
  void should_useBuilderDefaults_when_libraryListenerValuesAreAbsent() {
    // Arrange: library listener knobs unset (nulls), gap knobs always present
    SqsConfig config = new SqsConfig(gapRecord(), new SqsProperties(), ObservationRegistry.NOOP);

    // Act
    SqsMessageListenerContainerFactory<S3Event> factory;
    try (SqsAsyncClient client = sqsClient()) {
      factory =
          config.sqsListenerContainerFactory(
              client, config.sqsListenerTaskExecutor(), new SqsAcknowledgementLoggingCallback());
    }

    // Assert: factory still builds and honours the gap contract
    SqsContainerOptions options = optionsOf(factory);
    assertThat(options.getAcknowledgementMode()).isEqualTo(AcknowledgementMode.ON_SUCCESS);
    assertThat(options.getAcknowledgementOrdering()).isEqualTo(AcknowledgementOrdering.ORDERED);
    assertThat(options.getAcknowledgementInterval()).isEqualTo(Duration.ofSeconds(3));
    assertThat(options.getAcknowledgementThreshold()).isEqualTo(10);
  }
}
