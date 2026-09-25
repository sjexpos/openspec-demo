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

import com.amazonaws.services.lambda.runtime.events.S3Event;
import com.example.demo.infrastructure.messaging.sqs.AssetEventsListener;
import com.example.demo.infrastructure.messaging.sqs.S3EventMessageConverter;
import io.awspring.cloud.autoconfigure.AwsClientCustomizer;
import io.awspring.cloud.autoconfigure.sqs.SqsProperties;
import io.awspring.cloud.sqs.config.SqsMessageListenerContainerFactory;
import io.awspring.cloud.sqs.listener.BackPressureMode;
import io.awspring.cloud.sqs.listener.SqsMessageListenerContainer;
import io.awspring.cloud.sqs.listener.acknowledgement.AcknowledgementOrdering;
import io.awspring.cloud.sqs.listener.acknowledgement.AcknowledgementResultCallback;
import io.awspring.cloud.sqs.listener.acknowledgement.handler.AcknowledgementMode;
import io.awspring.cloud.sqs.listener.backpressure.BackPressureHandlerFactories;
import io.micrometer.observation.ObservationRegistry;
import java.util.List;
import java.util.concurrent.Executors;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskExecutor;
import org.springframework.core.task.support.TaskExecutorAdapter;
import software.amazon.awssdk.services.sqs.SqsAsyncClient;
import software.amazon.awssdk.services.sqs.SqsAsyncClientBuilder;

/**
 * SQS listener wiring. The starter auto-configures the {@code SqsAsyncClient} from {@code
 * spring.cloud.aws.*}; this config only customizes it and builds the listener container (beans
 * arrive in Phase 3 tasks, one at a time). No manual {@code SqsAsyncClient} bean exists here on
 * purpose: customizers are the supported extension point.
 */
@Configuration
@EnableConfigurationProperties(AwsSqsProperties.class)
@RequiredArgsConstructor
public class SqsConfig {

  private final AwsSqsProperties properties;

  private final SqsProperties sqsProperties;

  private final ObservationRegistry observationRegistry;

  @Bean
  public AwsClientCustomizer<SqsAsyncClientBuilder> sqsApiTimeoutCustomizer() {
    return builder ->
        builder.overrideConfiguration(
            override -> override.apiCallTimeout(this.properties.apiCallTimeout()));
  }

  @Bean
  public TaskExecutor sqsListenerTaskExecutor() {
    return new TaskExecutorAdapter(Executors.newVirtualThreadPerTaskExecutor());
  }

  @Bean
  public SqsMessageListenerContainerFactory<S3Event> sqsListenerContainerFactory(
      SqsAsyncClient sqsAsyncClient,
      TaskExecutor sqsListenerTaskExecutor,
      AcknowledgementResultCallback<S3Event> acknowledgementResultCallback) {
    return SqsMessageListenerContainerFactory.<S3Event>builder()
        .sqsAsyncClient(sqsAsyncClient)
        .acknowledgementResultCallback(acknowledgementResultCallback)
        .configure(options -> applyContainerOptions(options, sqsListenerTaskExecutor))
        .build();
  }

  private void applyContainerOptions(
      io.awspring.cloud.sqs.listener.SqsContainerOptionsBuilder options,
      TaskExecutor sqsListenerTaskExecutor) {
    SqsProperties.Listener listener = this.sqsProperties.getListener();
    options
        .acknowledgementMode(AcknowledgementMode.ON_SUCCESS)
        .acknowledgementOrdering(AcknowledgementOrdering.ORDERED)
        .acknowledgementInterval(this.properties.acknowledgementInterval())
        .acknowledgementThreshold(this.properties.acknowledgementThreshold())
        .backPressureHandlerFactory(
            BackPressureHandlerFactories.adaptiveThroughputBackPressureHandler())
        .backPressureMode(BackPressureMode.AUTO)
        .componentsTaskExecutor(sqsListenerTaskExecutor)
        .acknowledgementResultTaskExecutor(sqsListenerTaskExecutor)
        .messageConverter(new S3EventMessageConverter())
        .observationRegistry(
            Boolean.TRUE.equals(this.sqsProperties.isObservationEnabled())
                ? this.observationRegistry
                : ObservationRegistry.NOOP);
    if (listener.getMaxConcurrentMessages() != null) {
      options.maxConcurrentMessages(listener.getMaxConcurrentMessages());
    }
    if (listener.getMaxMessagesPerPoll() != null) {
      options.maxMessagesPerPoll(listener.getMaxMessagesPerPoll());
    }
    if (listener.getPollTimeout() != null) {
      options.pollTimeout(listener.getPollTimeout());
    }
    if (listener.getMaxDelayBetweenPolls() != null) {
      options.maxDelayBetweenPolls(listener.getMaxDelayBetweenPolls());
    }
    if (listener.getAutoStartup() != null) {
      options.autoStartup(listener.getAutoStartup());
    }
  }

  @Bean
  public SqsMessageListenerContainer<S3Event> assetEventsContainer(
      SqsMessageListenerContainerFactory<S3Event> factory, AssetEventsListener listener) {
    SqsMessageListenerContainer<S3Event> container = factory.createContainer("asset-events");
    container.setQueueNames(List.of(this.properties.assetsEventsQueue()));
    container.setPayloadDeserializationType(S3Event.class);
    container.setMessageListener(message -> listener.onAssetEvent(message.getPayload()));
    return container;
  }
}
