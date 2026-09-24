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

import io.awspring.cloud.autoconfigure.sqs.SqsProperties;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

/**
 * Migration guards: listener tuning and observability come from the library {@code SqsProperties}
 * ({@code spring.cloud.aws.sqs.*}), never from custom {@code aws.*} keys. No network is involved.
 */
class SqsPropertiesBindingTests {

  @Configuration
  @EnableConfigurationProperties(SqsProperties.class)
  static class TestConfig {}

  private final ApplicationContextRunner runner =
      new ApplicationContextRunner().withConfiguration(AutoConfigurations.of(TestConfig.class));

  @Test
  void should_readListenerTuningFromLibrary_when_springCloudKeysAreSet() {
    // Arrange
    ApplicationContextRunner configured =
        runner.withPropertyValues(
            "spring.cloud.aws.sqs.listener.max-concurrent-messages=2",
            "spring.cloud.aws.sqs.listener.max-messages-per-poll=10",
            "spring.cloud.aws.sqs.listener.poll-timeout=PT5S",
            "spring.cloud.aws.sqs.listener.max-delay-between-polls=PT2S",
            "spring.cloud.aws.sqs.listener.auto-startup=false");

    // Act + Assert
    configured.run(
        context -> {
          SqsProperties properties = context.getBean(SqsProperties.class);
          assertThat(properties.getListener().getMaxConcurrentMessages()).isEqualTo(2);
          assertThat(properties.getListener().getMaxMessagesPerPoll()).isEqualTo(10);
          assertThat(properties.getListener().getPollTimeout()).isEqualTo(Duration.ofSeconds(5));
          assertThat(properties.getListener().getMaxDelayBetweenPolls())
              .isEqualTo(Duration.ofSeconds(2));
          assertThat(properties.getListener().getAutoStartup()).isFalse();
        });
  }

  @Test
  void should_leaveCredentialsUnset_when_noStaticKeysAreConfigured() {
    // Arrange: no credential properties anywhere
    ApplicationContextRunner configured =
        runner.withPropertyValues("spring.cloud.aws.region.static=us-east-1");

    // Act + Assert: the SDK default chain resolves credentials, never static config
    configured.run(
        context -> {
          assertThat(
                  context.getEnvironment().getProperty("spring.cloud.aws.credentials.access-key"))
              .isNull();
          assertThat(
                  context.getEnvironment().getProperty("spring.cloud.aws.credentials.secret-key"))
              .isNull();
        });
  }

  @Test
  void should_defaultObservationDisabled_when_libraryFlagIsAbsent() {
    // Arrange: observation flag not set at all

    // Act + Assert: effective contract is disabled (library default is false)
    runner.run(
        context -> {
          SqsProperties properties = context.getBean(SqsProperties.class);
          assertThat(Boolean.TRUE.equals(properties.isObservationEnabled())).isFalse();
        });
  }
}
