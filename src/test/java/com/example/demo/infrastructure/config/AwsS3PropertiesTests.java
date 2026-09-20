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

import java.net.URI;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

class AwsS3PropertiesTests {

  @Configuration
  @EnableConfigurationProperties(AwsS3Properties.class)
  static class TestConfig {}

  private final ApplicationContextRunner runner =
      new ApplicationContextRunner().withConfiguration(AutoConfigurations.of(TestConfig.class));

  @Test
  void should_bindAllFields_when_allAwsPropertiesAreSet() {
    // Arrange
    ApplicationContextRunner configured =
        runner.withPropertyValues(
            "aws.region=eu-west-1",
            "aws.s3.endpoint=http://localhost:4566",
            "aws.s3.path-style-access=true",
            "aws.s3.bucket=develop-assets",
            "aws.s3.presign-ttl=PT15M");

    // Act + Assert
    configured.run(
        context -> {
          AwsS3Properties properties = context.getBean(AwsS3Properties.class);
          assertThat(properties.region()).isEqualTo("eu-west-1");
          assertThat(properties.s3().endpoint()).isEqualTo(URI.create("http://localhost:4566"));
          assertThat(properties.s3().pathStyleAccess()).isTrue();
          assertThat(properties.s3().bucket()).isEqualTo("develop-assets");
          assertThat(properties.s3().presignTtl()).isEqualTo(Duration.ofMinutes(15));
        });
  }

  @Test
  void should_reportEndpointOverridePresent_when_endpointIsSet() {
    // Arrange
    ApplicationContextRunner configured =
        runner.withPropertyValues(
            "aws.region=eu-west-1",
            "aws.s3.endpoint=http://localhost:4566",
            "aws.s3.path-style-access=true",
            "aws.s3.bucket=develop-assets",
            "aws.s3.presign-ttl=PT15M");

    // Act + Assert
    configured.run(
        context -> {
          AwsS3Properties properties = context.getBean(AwsS3Properties.class);
          assertThat(properties.hasEndpointOverride()).isTrue();
        });
  }

  @Test
  void should_bindEndpointAsNull_when_propertyIsEmptyString() {
    // Arrange
    ApplicationContextRunner configured =
        runner.withPropertyValues(
            "aws.region=us-east-1",
            "aws.s3.endpoint=",
            "aws.s3.path-style-access=false",
            "aws.s3.bucket=develop-assets",
            "aws.s3.presign-ttl=PT15M");

    // Act + Assert
    configured.run(
        context -> {
          AwsS3Properties properties = context.getBean(AwsS3Properties.class);
          assertThat(properties.s3().endpoint()).isNull();
          assertThat(properties.hasEndpointOverride()).isFalse();
        });
  }

  @Test
  void should_reportNoOverride_when_s3SectionIsAbsent() {
    // Arrange
    ApplicationContextRunner configured = runner.withPropertyValues("aws.region=us-east-1");

    // Act + Assert
    configured.run(
        context -> {
          AwsS3Properties properties = context.getBean(AwsS3Properties.class);
          assertThat(properties.hasEndpointOverride()).isFalse();
        });
  }
}
