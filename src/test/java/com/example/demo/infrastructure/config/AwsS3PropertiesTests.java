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

import io.awspring.cloud.autoconfigure.s3.properties.S3Properties;
import java.net.URI;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

class AwsS3PropertiesTests {

  @Configuration
  @EnableConfigurationProperties({AwsS3Properties.class, S3Properties.class})
  static class TestConfig {}

  private final ApplicationContextRunner runner =
      new ApplicationContextRunner().withConfiguration(AutoConfigurations.of(TestConfig.class));

  @Test
  void should_bindBucketAndTtl_when_slimKeysAreSet() {
    // Arrange
    ApplicationContextRunner configured =
        runner.withPropertyValues("aws.s3.bucket=develop-assets", "aws.s3.presign-ttl=PT15M");

    // Act + Assert
    configured.run(
        context -> {
          AwsS3Properties properties = context.getBean(AwsS3Properties.class);
          assertThat(properties.bucket()).isEqualTo("develop-assets");
          assertThat(properties.presignTtl()).isEqualTo(Duration.ofMinutes(15));
        });
  }

  @Test
  void should_ignoreLegacyKeys_when_regionAndEndpointAreSet() {
    // Arrange: legacy keys are not a source of truth anymore; they bind nowhere
    ApplicationContextRunner configured =
        runner.withPropertyValues(
            "aws.region=eu-west-1", "aws.s3.bucket=develop-assets", "aws.s3.presign-ttl=PT15M");

    // Act + Assert: slim record binds fine, legacy region drives nothing
    configured.run(
        context -> {
          AwsS3Properties properties = context.getBean(AwsS3Properties.class);
          assertThat(properties.bucket()).isEqualTo("develop-assets");
          assertThat(properties.presignTtl()).isEqualTo(Duration.ofMinutes(15));
        });
  }

  @Test
  void should_bindEndpointFromLibrary_when_serviceKeyIsSet() {
    // Arrange: endpoint override now travels through the library keys
    ApplicationContextRunner configured =
        runner.withPropertyValues(
            "spring.cloud.aws.s3.endpoint=http://localhost:4566",
            "aws.s3.bucket=develop-assets",
            "aws.s3.presign-ttl=PT15M");

    // Act + Assert
    configured.run(
        context -> {
          S3Properties properties = context.getBean(S3Properties.class);
          assertThat(properties.getEndpoint()).isEqualTo(URI.create("http://localhost:4566"));
        });
  }

  @Test
  void should_bindEndpointAsNull_when_libraryKeyIsEmptyString() {
    // Arrange: empty override means real AWS resolution
    ApplicationContextRunner configured =
        runner.withPropertyValues(
            "spring.cloud.aws.s3.endpoint=",
            "aws.s3.bucket=develop-assets",
            "aws.s3.presign-ttl=PT15M");

    // Act + Assert
    configured.run(
        context -> {
          S3Properties properties = context.getBean(S3Properties.class);
          assertThat(properties.getEndpoint()).isNull();
        });
  }

  @Test
  void should_failValidation_when_bucketIsBlank() {
    // Arrange: blank bucket name must be rejected at startup binding
    ApplicationContextRunner configured =
        runner.withPropertyValues("aws.s3.bucket=", "aws.s3.presign-ttl=PT15M");

    // Act + Assert
    configured.run(
        context -> {
          assertThat(context.getStartupFailure()).isNotNull().hasStackTraceContaining("bucket");
        });
  }
}
