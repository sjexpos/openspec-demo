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

import com.example.demo.domain.models.BlobType;
import com.example.demo.domain.models.BlobUploadTarget;
import com.example.demo.domain.repositories.BlobStorage;
import com.example.demo.domain.repositories.BlobStorageException;
import com.example.demo.infrastructure.config.AwsS3Properties;
import java.net.URISyntaxException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.Delete;
import software.amazon.awssdk.services.s3.model.DeleteObjectsRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectsResponse;
import software.amazon.awssdk.services.s3.model.ObjectIdentifier;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

/**
 * AWS S3 implementation of the {@code BlobStorage} port. Anti-Corruption Layer: translates the AWS
 * SDK model into the ubiquitous language. The only importer of {@code software.amazon.awssdk}
 * outside {@code infrastructure/config}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class S3BlobStorageAdapter implements BlobStorage {

  private static final int MAX_KEYS_PER_DELETE_REQUEST = 1000;

  private final S3Presigner s3Presigner;

  private final S3Client s3Client;

  private final AwsS3Properties properties;

  @Override
  public BlobUploadTarget createUploadTarget(BlobType blobType) {
    Assert.notNull(blobType, "blobType must not be null");
    String key = blobType.prefix() + UUID.randomUUID().toString().replace("-", "");
    Duration ttl = this.properties.presignTtl();
    try {
      PresignedPutObjectRequest presigned =
          this.s3Presigner.presignPutObject(
              PutObjectPresignRequest.builder()
                  .signatureDuration(ttl)
                  .putObjectRequest(
                      PutObjectRequest.builder().bucket(this.properties.bucket()).key(key).build())
                  .build());
      log.info("Upload target issued for blob type {} with key {}", blobType, key);
      return new BlobUploadTarget(
          key, presigned.url().toURI(), HttpMethod.PUT, Instant.now().plus(ttl));
    } catch (SdkException | URISyntaxException ex) {
      throw new BlobStorageException("Could not issue an upload target for key " + key, ex);
    }
  }

  @Override
  public void remove(Set<String> keys) {
    Assert.notNull(keys, "keys must not be null");
    if (keys.isEmpty()) {
      return;
    }
    keys.forEach(
        key ->
            Assert.isTrue(BlobType.isCanonicalKey(key), "Key is not a managed blob key: " + key));
    List<String> pending = new ArrayList<>(keys);
    try {
      for (int from = 0; from < pending.size(); from += MAX_KEYS_PER_DELETE_REQUEST) {
        List<String> chunk =
            pending.subList(from, Math.min(from + MAX_KEYS_PER_DELETE_REQUEST, pending.size()));
        DeleteObjectsResponse response =
            this.s3Client.deleteObjects(
                DeleteObjectsRequest.builder()
                    .bucket(this.properties.bucket())
                    .delete(
                        Delete.builder()
                            .objects(
                                chunk.stream()
                                    .map(key -> ObjectIdentifier.builder().key(key).build())
                                    .toList())
                            .build())
                    .build());
        List<String> failedKeys = response.errors().stream().map(error -> error.key()).toList();
        if (!failedKeys.isEmpty()) {
          log.warn("Failed to remove blobs: {}", failedKeys);
          throw new BlobStorageException(
              "Could not remove blobs: " + failedKeys, Set.copyOf(failedKeys));
        }
      }
    } catch (SdkException ex) {
      throw new BlobStorageException("Could not remove blobs", ex);
    }
    log.info("Removed {} blobs", keys.size());
  }
}
