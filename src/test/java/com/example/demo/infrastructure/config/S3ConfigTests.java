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
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

class S3ConfigTests {

  private AwsS3Properties propertiesWithEndpoint() {
    return new AwsS3Properties(
        "us-east-1",
        new AwsS3Properties.S3(
            URI.create("http://localhost:4566"), true, "develop-assets", Duration.ofMinutes(15)));
  }

  private AwsS3Properties propertiesWithoutEndpoint() {
    return new AwsS3Properties(
        "us-east-1", new AwsS3Properties.S3(null, false, "develop-assets", Duration.ofMinutes(15)));
  }

  @Test
  void should_buildS3Client_when_propertiesAreValid() {
    // Arrange
    S3Config config = new S3Config(propertiesWithoutEndpoint());

    // Act
    S3Client client = config.s3Client();

    // Assert
    assertThat(client).isNotNull();
    assertThat(client.serviceClientConfiguration().region()).isEqualTo(Region.US_EAST_1);
    client.close();
  }

  @Test
  void should_applyEndpointOverride_when_endpointIsConfigured() {
    // Arrange
    S3Config config = new S3Config(propertiesWithEndpoint());

    // Act
    S3Client client = config.s3Client();

    // Assert
    assertThat(client.serviceClientConfiguration().endpointOverride())
        .isPresent()
        .hasValue(URI.create("http://localhost:4566"));
    client.close();
  }

  @Test
  void should_notApplyEndpointOverride_when_endpointIsNull() {
    // Arrange
    S3Config config = new S3Config(propertiesWithoutEndpoint());

    // Act
    S3Client client = config.s3Client();

    // Assert
    assertThat(client.serviceClientConfiguration().endpointOverride()).isEmpty();
    client.close();
  }

  @Test
  void should_useConfiguredRegion_when_regionIsNotDefault() {
    // Arrange
    AwsS3Properties customRegion =
        new AwsS3Properties(
            "eu-west-1",
            new AwsS3Properties.S3(null, false, "develop-assets", Duration.ofMinutes(15)));
    S3Config config = new S3Config(customRegion);

    // Act
    S3Client client = config.s3Client();

    // Assert
    assertThat(client.serviceClientConfiguration().region()).isEqualTo(Region.EU_WEST_1);
    client.close();
  }

  @Test
  void should_buildS3Presigner_when_endpointIsConfigured() throws Exception {
    // Arrange: presigning is a local HMAC operation but still resolves credentials
    // from the default chain, so expose test credentials via system properties
    // (first provider in the chain). No network call is performed.
    System.setProperty("aws.accessKeyId", "test");
    System.setProperty("aws.secretAccessKey", "test");
    try {
      S3Config config = new S3Config(propertiesWithEndpoint());

      // Act
      S3Presigner presigner = config.s3Presigner();
      GetObjectPresignRequest presignRequest =
          GetObjectPresignRequest.builder()
              .signatureDuration(Duration.ofMinutes(15))
              .getObjectRequest(
                  GetObjectRequest.builder()
                      .bucket("develop-assets")
                      .key("products/kan-14-test.txt")
                      .build())
              .build();
      URI presignedUrl = presigner.presignGetObject(presignRequest).url().toURI();

      // Assert
      assertThat(presigner).isNotNull();
      assertThat(presignedUrl.getHost()).isEqualTo("localhost");
      assertThat(presignedUrl.getPort()).isEqualTo(4566);
      assertThat(presignedUrl.getPath()).startsWith("/develop-assets/");
      presigner.close();
    } finally {
      System.clearProperty("aws.accessKeyId");
      System.clearProperty("aws.secretAccessKey");
    }
  }

  @Test
  void should_notApplyEndpointOverride_when_presignerEndpointIsNull() throws Exception {
    // Arrange
    System.setProperty("aws.accessKeyId", "test");
    System.setProperty("aws.secretAccessKey", "test");
    try {
      S3Config config = new S3Config(propertiesWithoutEndpoint());

      // Act
      S3Presigner presigner = config.s3Presigner();
      GetObjectPresignRequest presignRequest =
          GetObjectPresignRequest.builder()
              .signatureDuration(Duration.ofMinutes(15))
              .getObjectRequest(
                  GetObjectRequest.builder()
                      .bucket("develop-assets")
                      .key("products/kan-14-test.txt")
                      .build())
              .build();
      URI presignedUrl = presigner.presignGetObject(presignRequest).url().toURI();

      // Assert: standard AWS host for the region, not localhost
      assertThat(presignedUrl.getHost()).doesNotContain("localhost");
      assertThat(presignedUrl.getHost()).contains("amazonaws.com");
      presigner.close();
    } finally {
      System.clearProperty("aws.accessKeyId");
      System.clearProperty("aws.secretAccessKey");
    }
  }
}
