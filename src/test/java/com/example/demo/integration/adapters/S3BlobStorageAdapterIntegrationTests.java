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

package com.example.demo.integration.adapters;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.demo.domain.models.BlobType;
import com.example.demo.domain.models.BlobUploadTarget;
import com.example.demo.domain.repositories.BlobStorage;
import com.example.demo.infrastructure.config.AwsS3Properties;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junitpioneer.jupiter.SetEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.S3Object;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@Import(FlywayAutoConfiguration.class)
@SetEnvironmentVariable(key = "AWS_ACCESS_KEY_ID", value = "test")
@SetEnvironmentVariable(key = "AWS_SECRET_ACCESS_KEY", value = "test")
class S3BlobStorageAdapterIntegrationTests {

  @Autowired private BlobStorage blobStorage;

  @Autowired private S3Client s3Client;

  @Autowired private AwsS3Properties properties;

  private final HttpClient httpClient = HttpClient.newHttpClient();

  private final List<String> issuedKeys = new ArrayList<>();

  @AfterEach
  void cleanUp() {
    if (!issuedKeys.isEmpty()) {
      blobStorage.remove(new LinkedHashSet<>(issuedKeys));
      issuedKeys.clear();
    }
  }

  @Test
  void should_storeObjectAtIssuedKey_when_presignedUrlIsUploadedWithoutCredentials()
      throws Exception {
    // Arrange
    BlobUploadTarget target = issueTarget(BlobType.PRODUCT_IMAGE);
    byte[] content = "kan-10 credential-free upload".getBytes(StandardCharsets.UTF_8);

    // Act: PUT over plain HTTP with no AWS credentials
    HttpResponse<Void> response =
        httpClient.send(
            HttpRequest.newBuilder(target.url())
                .PUT(HttpRequest.BodyPublishers.ofByteArray(content))
                .build(),
            HttpResponse.BodyHandlers.discarding());

    // Assert
    assertThat(response.statusCode()).isEqualTo(200);
    var head =
        s3Client.headObject(
            HeadObjectRequest.builder().bucket(properties.bucket()).key(target.key()).build());
    assertThat(head.contentLength()).isEqualTo(content.length);
  }

  @Test
  void should_roundTripContent_when_objectIsUploadedThroughPresignedUrl() throws Exception {
    // Arrange
    BlobUploadTarget target = issueTarget(BlobType.BRAND_IMAGE);
    byte[] content = "kan-10 round-trip".getBytes(StandardCharsets.UTF_8);
    httpClient.send(
        HttpRequest.newBuilder(target.url())
            .PUT(HttpRequest.BodyPublishers.ofByteArray(content))
            .build(),
        HttpResponse.BodyHandlers.discarding());

    // Act
    String readBack;
    try (ResponseInputStream<GetObjectResponse> object =
        s3Client.getObject(
            GetObjectRequest.builder().bucket(properties.bucket()).key(target.key()).build())) {
      readBack = new String(object.readAllBytes(), StandardCharsets.UTF_8);
    }

    // Assert
    assertThat(readBack).isEqualTo(new String(content, StandardCharsets.UTF_8));
  }

  @Test
  void should_deleteObject_when_removeIsCalled() throws Exception {
    // Arrange
    BlobUploadTarget target = issueTarget(BlobType.STRAIN_IMAGE);
    byte[] content = "kan-10 to delete".getBytes(StandardCharsets.UTF_8);
    httpClient.send(
        HttpRequest.newBuilder(target.url())
            .PUT(HttpRequest.BodyPublishers.ofByteArray(content))
            .build(),
        HttpResponse.BodyHandlers.discarding());

    // Act
    blobStorage.remove(new LinkedHashSet<>(List.of(target.key())));

    // Assert
    assertThatThrownBy(
            () ->
                s3Client.headObject(
                    HeadObjectRequest.builder()
                        .bucket(properties.bucket())
                        .key(target.key())
                        .build()))
        .isInstanceOf(NoSuchKeyException.class);
  }

  @Test
  void should_succeed_when_removingUnknownKey() {
    // Arrange: a canonical key that was never uploaded
    BlobUploadTarget target = issueTarget(BlobType.PRODUCT_VIDEO);

    // Act + Assert: idempotent removal completes without error
    blobStorage.remove(new LinkedHashSet<>(List.of(target.key())));
  }

  @Test
  void should_deleteAllObjects_when_removeIsCalledWithMultipleKeys() throws Exception {
    // Arrange: three uploaded blobs
    List<String> keys = new ArrayList<>();
    for (BlobType blobType :
        List.of(BlobType.BRAND_IMAGE, BlobType.BRAND_VIDEO, BlobType.STRAIN_IMAGE)) {
      BlobUploadTarget target = issueTarget(blobType);
      byte[] content = ("kan-10 batch " + blobType).getBytes(StandardCharsets.UTF_8);
      httpClient.send(
          HttpRequest.newBuilder(target.url())
              .PUT(HttpRequest.BodyPublishers.ofByteArray(content))
              .build(),
          HttpResponse.BodyHandlers.discarding());
      keys.add(target.key());
    }

    // Act
    blobStorage.remove(new LinkedHashSet<>(keys));

    // Assert: every object is gone
    for (String key : keys) {
      assertThatThrownBy(
              () ->
                  s3Client.headObject(
                      HeadObjectRequest.builder().bucket(properties.bucket()).key(key).build()))
          .isInstanceOf(NoSuchKeyException.class);
    }
  }

  @Test
  void should_isolateBlobTypes_when_keysAreListedByPrefix() throws Exception {
    // Arrange: one uploaded blob per blob type
    List<BlobUploadTarget> targets = new ArrayList<>();
    for (BlobType blobType : BlobType.values()) {
      BlobUploadTarget target = issueTarget(blobType);
      byte[] content = ("kan-10 isolate " + blobType).getBytes(StandardCharsets.UTF_8);
      httpClient.send(
          HttpRequest.newBuilder(target.url())
              .PUT(HttpRequest.BodyPublishers.ofByteArray(content))
              .build(),
          HttpResponse.BodyHandlers.discarding());
      targets.add(target);
    }

    // Act + Assert: each prefix listing returns exactly the key issued for that type
    for (BlobUploadTarget target : targets) {
      List<S3Object> listed =
          s3Client
              .listObjectsV2(
                  ListObjectsV2Request.builder()
                      .bucket(properties.bucket())
                      .prefix(prefixOf(target))
                      .build())
              .contents();
      assertThat(listed).extracting(S3Object::key).containsExactly(target.key());
    }
  }

  private BlobUploadTarget issueTarget(BlobType blobType) {
    BlobUploadTarget target = blobStorage.createUploadTarget(blobType);
    issuedKeys.add(target.key());
    return target;
  }

  private static String prefixOf(BlobUploadTarget target) {
    for (BlobType blobType : BlobType.values()) {
      if (target.key().startsWith(blobType.prefix())) {
        return blobType.prefix();
      }
    }
    throw new IllegalStateException("Issued key matches no blob type prefix: " + target.key());
  }
}
