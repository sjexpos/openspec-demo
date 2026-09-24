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

import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

class AwsSqsPropertiesTests {

  @Configuration
  @EnableConfigurationProperties(AwsSqsProperties.class)
  static class TestConfig {}

  private final ApplicationContextRunner runner =
      new ApplicationContextRunner().withConfiguration(AutoConfigurations.of(TestConfig.class));

  @Test
  void should_bindGapKnobs_when_ymlBlockIsPresent() {
    // Arrange
    ApplicationContextRunner configured =
        runner.withPropertyValues(
            "aws.sqs.assets-events-queue=develop-assets-events-queue",
            "aws.sqs.acknowledgement-interval=PT3S",
            "aws.sqs.acknowledgement-threshold=10",
            "aws.sqs.api-call-timeout=PT1.5S");

    // Act + Assert
    configured.run(
        context -> {
          AwsSqsProperties properties = context.getBean(AwsSqsProperties.class);
          assertThat(properties.assetsEventsQueue()).isEqualTo("develop-assets-events-queue");
          assertThat(properties.acknowledgementInterval()).isEqualTo(Duration.ofSeconds(3));
          assertThat(properties.acknowledgementThreshold()).isEqualTo(10);
          assertThat(properties.apiCallTimeout()).isEqualTo(Duration.ofMillis(1500));
        });
  }

  @Test
  void should_rejectInvalidThreshold_when_ackThresholdIsNotPositive() {
    // Arrange: non-positive batch-ack size must be rejected at startup binding
    ApplicationContextRunner configured =
        runner.withPropertyValues(
            "aws.sqs.assets-events-queue=develop-assets-events-queue",
            "aws.sqs.acknowledgement-interval=PT3S",
            "aws.sqs.acknowledgement-threshold=0",
            "aws.sqs.api-call-timeout=PT1.5S");

    // Act + Assert
    configured.run(
        context -> {
          assertThat(context.getStartupFailure())
              .isNotNull()
              .hasStackTraceContaining("acknowledgementThreshold");
        });
  }
}
