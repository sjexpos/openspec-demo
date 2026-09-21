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

package com.example.demo.infrastructure.adapters.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.example.demo.domain.models.BlobType;
import com.example.demo.domain.models.BlobUploadTarget;
import com.example.demo.domain.repositories.BlobStorageException;
import com.example.demo.infrastructure.config.AwsS3Properties;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpMethod;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectsRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectsResponse;
import software.amazon.awssdk.services.s3.model.ObjectIdentifier;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Error;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

@ExtendWith(MockitoExtension.class)
class S3BlobStorageAdapterTests {

  private static final String BUCKET = "develop-assets";

  private static final Duration TTL = Duration.ofMinutes(15);

  @Mock private S3Presigner s3Presigner;

  @Mock private S3Client s3Client;

  @Mock private PresignedPutObjectRequest presignedRequest;

  private AwsS3Properties properties;

  private S3BlobStorageAdapter adapter;

  @BeforeEach
  void setUp() {
    properties = new AwsS3Properties("us-east-1", new AwsS3Properties.S3(null, false, BUCKET, TTL));
    adapter = new S3BlobStorageAdapter(s3Presigner, s3Client, properties);
  }

  @ParameterizedTest
  @EnumSource(BlobType.class)
  void should_returnCanonicalKeyAndPutMethod_when_uploadTargetIsRequested(BlobType blobType)
      throws Exception {
    // Arrange
    given(s3Presigner.presignPutObject(any(PutObjectPresignRequest.class)))
        .willReturn(presignedRequest);
    given(presignedRequest.url()).willReturn(new URI("http://localhost:4566/upload").toURL());

    // Act
    BlobUploadTarget target = adapter.createUploadTarget(blobType);

    // Assert
    assertThat(BlobType.isCanonicalKey(target.key())).isTrue();
    assertThat(target.key()).startsWith(blobType.prefix());
    assertThat(target.method()).isEqualTo(HttpMethod.PUT);
  }

  @Test
  void should_returnDifferentKeys_when_uploadTargetIsRequestedTwice() throws Exception {
    // Arrange
    given(s3Presigner.presignPutObject(any(PutObjectPresignRequest.class)))
        .willReturn(presignedRequest);
    given(presignedRequest.url()).willReturn(new URI("http://localhost:4566/upload").toURL());

    // Act
    BlobUploadTarget first = adapter.createUploadTarget(BlobType.PRODUCT_IMAGE);
    BlobUploadTarget second = adapter.createUploadTarget(BlobType.PRODUCT_IMAGE);

    // Assert
    assertThat(first.key()).isNotEqualTo(second.key());
  }

  @Test
  void should_presignAgainstConfiguredBucketAndTtl_when_uploadTargetIsRequested() throws Exception {
    // Arrange
    given(s3Presigner.presignPutObject(any(PutObjectPresignRequest.class)))
        .willReturn(presignedRequest);
    given(presignedRequest.url()).willReturn(new URI("http://localhost:4566/upload").toURL());

    // Act
    BlobUploadTarget target = adapter.createUploadTarget(BlobType.BRAND_IMAGE);

    // Assert
    ArgumentCaptor<PutObjectPresignRequest> captor =
        ArgumentCaptor.forClass(PutObjectPresignRequest.class);
    verify(s3Presigner).presignPutObject(captor.capture());
    PutObjectPresignRequest presignRequest = captor.getValue();
    assertThat(presignRequest.signatureDuration()).isEqualTo(TTL);
    PutObjectRequest putRequest = presignRequest.putObjectRequest();
    assertThat(putRequest.bucket()).isEqualTo(BUCKET);
    assertThat(putRequest.key()).isEqualTo(target.key());
  }

  @Test
  void should_setExpiresAtToNowPlusTtl_when_uploadTargetIsRequested() throws Exception {
    // Arrange
    given(s3Presigner.presignPutObject(any(PutObjectPresignRequest.class)))
        .willReturn(presignedRequest);
    given(presignedRequest.url()).willReturn(new URI("http://localhost:4566/upload").toURL());
    Instant before = Instant.now();

    // Act
    BlobUploadTarget target = adapter.createUploadTarget(BlobType.STRAIN_IMAGE);

    // Assert
    Instant after = Instant.now();
    assertThat(target.expiresAt())
        .isAfterOrEqualTo(before.plus(TTL).minusSeconds(30))
        .isBeforeOrEqualTo(after.plus(TTL).plusSeconds(30));
  }

  @Test
  void should_throwIllegalArgument_when_blobTypeIsNull() {
    // Arrange: null blob type

    // Act + Assert
    assertThatThrownBy(() -> adapter.createUploadTarget(null))
        .isInstanceOf(IllegalArgumentException.class);
    verifyNoInteractions(s3Presigner);
  }

  @Test
  void should_throwBlobStorageException_when_presignerFails() {
    // Arrange
    given(s3Presigner.presignPutObject(any(PutObjectPresignRequest.class)))
        .willThrow(SdkClientException.create("presign failure"));

    // Act + Assert
    assertThatThrownBy(() -> adapter.createUploadTarget(BlobType.BRAND_IMAGE))
        .isInstanceOf(BlobStorageException.class)
        .hasCauseInstanceOf(SdkClientException.class);
  }

  @Test
  void should_sendSingleDeleteRequest_when_keyCountIsAtLimit() {
    // Arrange: exactly 1000 canonical keys (S3 DeleteObjects hard limit)
    Set<String> keys = canonicalKeys(1000, 0);
    given(s3Client.deleteObjects(any(DeleteObjectsRequest.class)))
        .willReturn(DeleteObjectsResponse.builder().build());

    // Act
    adapter.remove(keys);

    // Assert
    ArgumentCaptor<DeleteObjectsRequest> captor =
        ArgumentCaptor.forClass(DeleteObjectsRequest.class);
    verify(s3Client, times(1)).deleteObjects(captor.capture());
    List<String> deleted =
        captor.getValue().delete().objects().stream().map(ObjectIdentifier::key).toList();
    assertThat(deleted).containsExactlyInAnyOrderElementsOf(keys);
  }

  @Test
  void should_chunkDeleteRequests_when_keyCountExceedsLimit() {
    // Arrange: 1001 canonical keys must be split into 1000 + 1
    Set<String> keys = canonicalKeys(1001, 0);
    given(s3Client.deleteObjects(any(DeleteObjectsRequest.class)))
        .willReturn(DeleteObjectsResponse.builder().build());

    // Act
    adapter.remove(keys);

    // Assert
    ArgumentCaptor<DeleteObjectsRequest> captor =
        ArgumentCaptor.forClass(DeleteObjectsRequest.class);
    verify(s3Client, times(2)).deleteObjects(captor.capture());
    assertThat(captor.getAllValues().get(0).delete().objects()).hasSize(1000);
    assertThat(captor.getAllValues().get(1).delete().objects()).hasSize(1);
    List<String> deleted =
        captor.getAllValues().stream()
            .flatMap(request -> request.delete().objects().stream())
            .map(ObjectIdentifier::key)
            .toList();
    assertThat(deleted).containsExactlyInAnyOrderElementsOf(keys);
  }

  @Test
  void should_throwBlobStorageExceptionWithFailedKeys_when_deleteResponseContainsErrors() {
    // Arrange: the store reports a per-key failure for part of the batch
    String failedKey = BlobType.BRAND_VIDEO.prefix() + "9f2a4c1ed3b74e8fa1c6b0d2e5f7a913";
    String okKey = BlobType.BRAND_VIDEO.prefix() + "1c7d2e3f4a5b6c7d8e9f0a1b2c3d4e5f";
    given(s3Client.deleteObjects(any(DeleteObjectsRequest.class)))
        .willReturn(
            DeleteObjectsResponse.builder()
                .errors(S3Error.builder().key(failedKey).code("AccessDenied").build())
                .build());

    // Act + Assert
    assertThatThrownBy(() -> adapter.remove(Set.of(failedKey, okKey)))
        .isInstanceOf(BlobStorageException.class)
        .satisfies(
            thrown ->
                assertThat(((BlobStorageException) thrown).getFailedKeys())
                    .containsExactly(failedKey));
  }

  @Test
  void should_throwBlobStorageException_when_s3ClientThrows() {
    // Arrange: the SDK client fails the whole batch
    Set<String> keys = canonicalKeys(2);
    SdkClientException cause = SdkClientException.create("connection failure");
    given(s3Client.deleteObjects(any(DeleteObjectsRequest.class))).willThrow(cause);

    // Act + Assert
    assertThatThrownBy(() -> adapter.remove(keys))
        .isInstanceOf(BlobStorageException.class)
        .hasCause(cause);
  }

  @Test
  void should_notLogPresignedUrl_when_uploadTargetIsIssued() throws Exception {
    // Arrange: capture the adapter logger output; the presigned URL carries signature material
    ch.qos.logback.classic.Logger logger =
        (ch.qos.logback.classic.Logger) LoggerFactory.getLogger(S3BlobStorageAdapter.class);
    ch.qos.logback.core.read.ListAppender<ch.qos.logback.classic.spi.ILoggingEvent> appender =
        new ch.qos.logback.core.read.ListAppender<>();
    appender.start();
    logger.addAppender(appender);
    String signatureMaterial = "X-Amz-Signature=abcdef1234567890";
    String presignedUrl =
        "http://localhost:4566/develop-assets/products/images/9f2a4c1ed3b74e8fa1c6b0d2e5f7a913"
            + "?X-Amz-Algorithm=AWS4-HMAC-SHA256&"
            + signatureMaterial;
    given(s3Presigner.presignPutObject(any(PutObjectPresignRequest.class)))
        .willReturn(presignedRequest);
    given(presignedRequest.url()).willReturn(new URI(presignedUrl).toURL());
    try {
      // Act
      BlobUploadTarget target = adapter.createUploadTarget(BlobType.PRODUCT_IMAGE);

      // Assert
      List<String> messages =
          appender.list.stream()
              .map(ch.qos.logback.classic.spi.ILoggingEvent::getFormattedMessage)
              .toList();
      assertThat(messages).isNotEmpty();
      assertThat(messages).anySatisfy(message -> assertThat(message).contains(target.key()));
      assertThat(String.join("\n", messages))
          .doesNotContain("X-Amz-Signature")
          .doesNotContain(presignedUrl)
          .doesNotContain(signatureMaterial);
    } finally {
      logger.detachAppender(appender);
    }
  }

  @Test
  void should_notCallS3_when_keySetIsEmpty() {
    // Arrange: empty key set

    // Act
    adapter.remove(Set.of());

    // Assert
    verifyNoInteractions(s3Client);
  }

  @Test
  void should_throwIllegalArgument_when_keySetIsNull() {
    // Arrange: null key set

    // Act + Assert
    assertThatThrownBy(() -> adapter.remove(null)).isInstanceOf(IllegalArgumentException.class);
    verifyNoInteractions(s3Client);
  }

  @Test
  void should_throwIllegalArgumentAndNotCallS3_when_anyKeyIsNotCanonical() {
    // Arrange: one valid canonical key mixed with one invalid key
    Set<String> keys =
        Set.of(
            BlobType.PRODUCT_IMAGE.prefix() + "9f2a4c1ed3b74e8fa1c6b0d2e5f7a913",
            "flyway/9f2a4c1ed3b74e8fa1c6b0d2e5f7a913");

    // Act + Assert
    assertThatThrownBy(() -> adapter.remove(keys)).isInstanceOf(IllegalArgumentException.class);
    verify(s3Client, never()).deleteObjects(any(DeleteObjectsRequest.class));
    verifyNoInteractions(s3Client);
  }

  private static Set<String> canonicalKeys(int count, int offset) {
    Set<String> keys = new LinkedHashSet<>();
    for (int index = offset; index < offset + count; index++) {
      keys.add(BlobType.PRODUCT_IMAGE.prefix() + String.format("%032x", index));
    }
    return keys;
  }

  private static Set<String> canonicalKeys(int count) {
    return canonicalKeys(count, 0);
  }
}
