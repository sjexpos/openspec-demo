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

package com.example.demo.integration.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.demo.infrastructure.config.AwsS3Properties;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junitpioneer.jupiter.SetEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@Import(FlywayAutoConfiguration.class)
@SetEnvironmentVariable(key = "AWS_ACCESS_KEY_ID", value = "test")
@SetEnvironmentVariable(key = "AWS_SECRET_ACCESS_KEY", value = "test")
class S3ConfigIntegrationTests {

  @Autowired private ApplicationContext context;

  @Autowired private S3Client s3Client;

  @Autowired private S3Presigner s3Presigner;

  @Autowired private AwsS3Properties properties;

  private static final String TEST_KEY = "products/kan-14-test.txt";

  @AfterEach
  void cleanUp() {
    s3Client.deleteObject(
        DeleteObjectRequest.builder().bucket(properties.bucket()).key(TEST_KEY).build());
  }

  @Test
  void should_injectS3ClientAndPresigner_when_contextStarts() {
    // Arrange: beans autowired by Spring

    // Act: resolve beans from the context

    // Assert
    assertThat(s3Client).isNotNull();
    assertThat(s3Presigner).isNotNull();
    assertThat(context.getBeansOfType(S3Client.class)).hasSize(1);
    assertThat(context.getBeansOfType(S3Presigner.class)).hasSize(1);
  }

  @Test
  void should_listAssetsBucket_when_localstackIsRunning() {
    // Arrange: LocalStack bootstrap creates the develop-assets bucket

    // Act
    var buckets = s3Client.listBuckets().buckets();

    // Assert
    assertThat(buckets).extracting("name").contains("develop-assets");
  }

  @Test
  void should_roundTripObject_when_putAndGetAreInvoked() throws Exception {
    // Arrange
    String content = "kan-14 round-trip";

    // Act
    s3Client.putObject(
        PutObjectRequest.builder().bucket(properties.bucket()).key(TEST_KEY).build(),
        RequestBody.fromString(content, StandardCharsets.UTF_8));
    String readBack;
    try (ResponseInputStream<GetObjectResponse> response =
        s3Client.getObject(
            GetObjectRequest.builder().bucket(properties.bucket()).key(TEST_KEY).build())) {
      readBack = new String(response.readAllBytes(), StandardCharsets.UTF_8);
    }

    // Assert
    assertThat(readBack).isEqualTo(content);
  }

  @Test
  void should_returnDownloadableUrl_when_objectIsPresigned() throws Exception {
    // Arrange
    String content = "kan-14 presigned";
    s3Client.putObject(
        PutObjectRequest.builder().bucket(properties.bucket()).key(TEST_KEY).build(),
        RequestBody.fromString(content, StandardCharsets.UTF_8));
    var presignRequest =
        software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest.builder()
            .signatureDuration(properties.presignTtl())
            .getObjectRequest(
                GetObjectRequest.builder().bucket(properties.bucket()).key(TEST_KEY).build())
            .build();

    // Act: presign, then fetch over plain HTTP with no credentials
    var presignedUrl = s3Presigner.presignGetObject(presignRequest).url().toURI();
    var httpClient = java.net.http.HttpClient.newHttpClient();
    var httpResponse =
        httpClient.send(
            java.net.http.HttpRequest.newBuilder(presignedUrl).GET().build(),
            java.net.http.HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

    // Assert
    assertThat(presignedUrl.getHost()).isEqualTo("localhost");
    assertThat(httpResponse.statusCode()).isEqualTo(200);
    assertThat(httpResponse.body()).isEqualTo(content);
  }
}
