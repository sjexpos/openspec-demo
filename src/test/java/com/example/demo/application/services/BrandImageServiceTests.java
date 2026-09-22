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

package com.example.demo.application.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.example.demo.application.exceptions.NotFoundException;
import com.example.demo.application.services.impl.BrandImageServiceImpl;
import com.example.demo.domain.models.AssetStatus;
import com.example.demo.domain.models.BlobType;
import com.example.demo.domain.models.BlobUploadTarget;
import com.example.demo.domain.models.brand.Brand;
import com.example.demo.domain.models.brand.BrandImage;
import com.example.demo.domain.models.brand.BrandType;
import com.example.demo.domain.repositories.BlobStorageException;
import java.net.URI;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.ComponentScan;

class BrandImageServiceTests extends ServiceTest {

  @TestConfiguration
  @ComponentScan(lazyInit = true)
  static class TestConfig {}

  @Autowired private BrandImageService brandImageService;

  private BrandType brandType;

  @BeforeEach
  void setUp() {
    brandType = new BrandType();
    brandType.setId(1L);
    brandType.setName("grower");
  }

  private Brand liveBrand(Long id) {
    return Brand.builder()
        .id(id)
        .name("brand-a")
        .description("Description")
        .email("brand-a@yopmail.com")
        .stateLicense("LIC")
        .brandType(brandType)
        .logoImageUrl("https://example.com/logo.png")
        .adminId(1)
        .enabled(Boolean.TRUE)
        .build();
  }

  private BlobUploadTarget uploadTarget(String key) {
    try {
      return new BlobUploadTarget(
          key,
          new URI("https://example.invalid/brands/images/placeholder?X-Amz-Signature=abc"),
          org.springframework.http.HttpMethod.PUT,
          Instant.parse("2026-09-22T10:15:30Z"));
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  private String canonicalBrandImageKey() {
    return "brands/images/9f2c4a1ed3b74e8fa1c6b0d2e5f7a913";
  }

  @Test
  @DisplayName("persists PENDING image with port-issued key when brand exists (AC2)")
  void should_persistPendingImageWithPortIssuedKey_when_brandExists() {
    // Given
    Brand brand = liveBrand(1L);
    BlobUploadTarget target = uploadTarget(canonicalBrandImageKey());
    given(brandRepository.findById(1L)).willReturn(Optional.of(brand));
    given(blobStorage.createUploadTarget(BlobType.BRAND_IMAGE)).willReturn(target);
    given(brandImageRepository.save(any(BrandImage.class))).willAnswer(inv -> inv.getArgument(0));

    // When
    brandImageService.createUploadTarget(1L);

    // Then: the row is verified through the captor since it is invisible in the response
    ArgumentCaptor<BrandImage> captor = ArgumentCaptor.forClass(BrandImage.class);
    verify(brandImageRepository).save(captor.capture());
    assertEquals(AssetStatus.PENDING, captor.getValue().getStatus());
    assertEquals(target.key(), captor.getValue().getImageKey());
  }

  @Test
  @DisplayName("returns exactly the upload target issued by the port (D2)")
  void should_returnUploadTargetIssuedByPort_when_brandExists() {
    // Given
    given(brandRepository.findById(1L)).willReturn(Optional.of(liveBrand(1L)));
    BlobUploadTarget target = uploadTarget(canonicalBrandImageKey());
    given(blobStorage.createUploadTarget(BlobType.BRAND_IMAGE)).willReturn(target);
    given(brandImageRepository.save(any(BrandImage.class))).willAnswer(inv -> inv.getArgument(0));

    // When
    BlobUploadTarget result = brandImageService.createUploadTarget(1L);

    // Then
    assertSame(target, result);
  }

  @Test
  @DisplayName("throws NotFound when brand does not exist (AC5)")
  void should_throwNotFound_when_brandDoesNotExist() {
    // Given
    given(brandRepository.findById(999L)).willReturn(Optional.empty());

    // When / Then
    NotFoundException ex =
        assertThrows(NotFoundException.class, () -> brandImageService.createUploadTarget(999L));
    assertEquals("Brand not found with ID: 999", ex.getMessage());
  }

  @Test
  @DisplayName("throws NotFound when brand is soft-deleted (AC5)")
  void should_throwNotFound_when_brandIsSoftDeleted() {
    // Given: @SQLRestriction makes soft-deleted brands invisible, mirrored by empty findById
    given(brandRepository.findById(2L)).willReturn(Optional.empty());

    // When / Then
    NotFoundException ex =
        assertThrows(NotFoundException.class, () -> brandImageService.createUploadTarget(2L));
    assertEquals("Brand not found with ID: 2", ex.getMessage());
  }

  @Test
  @DisplayName("never calls blob storage when brand does not exist (AC7 ordering)")
  void should_notCallBlobStorage_when_brandDoesNotExist() {
    // Given
    given(brandRepository.findById(999L)).willReturn(Optional.empty());

    // When
    assertThrows(NotFoundException.class, () -> brandImageService.createUploadTarget(999L));

    // Then
    verify(blobStorage, never()).createUploadTarget(any(BlobType.class));
  }

  @Test
  @DisplayName("never persists when blob storage fails (AC8)")
  void should_notPersist_when_blobStorageFails() {
    // Given
    given(brandRepository.findById(1L)).willReturn(Optional.of(liveBrand(1L)));
    given(blobStorage.createUploadTarget(BlobType.BRAND_IMAGE))
        .willThrow(new BlobStorageException("presign failed", Set.of()));

    // When
    assertThrows(BlobStorageException.class, () -> brandImageService.createUploadTarget(1L));

    // Then
    verify(brandImageRepository, never()).save(any(BrandImage.class));
  }

  @Test
  @DisplayName("propagates blob storage exception without swallowing or wrapping (AC8)")
  void should_propagateBlobStorageException_when_presignFails() {
    // Given
    given(brandRepository.findById(1L)).willReturn(Optional.of(liveBrand(1L)));
    BlobStorageException failure = new BlobStorageException("presign failed", Set.of());
    given(blobStorage.createUploadTarget(BlobType.BRAND_IMAGE)).willThrow(failure);

    // When / Then
    BlobStorageException ex =
        assertThrows(BlobStorageException.class, () -> brandImageService.createUploadTarget(1L));
    assertSame(failure, ex);
  }

  @Test
  @DisplayName("requests BRAND_IMAGE blob type when minting the key (copy-paste guard)")
  void should_requestBrandImageBlobType_when_mintingKey() {
    // Given
    given(brandRepository.findById(1L)).willReturn(Optional.of(liveBrand(1L)));
    BlobUploadTarget target = uploadTarget(canonicalBrandImageKey());
    given(blobStorage.createUploadTarget(any(BlobType.class))).willReturn(target);
    given(brandImageRepository.save(any(BrandImage.class))).willAnswer(inv -> inv.getArgument(0));

    // When
    brandImageService.createUploadTarget(1L);

    // Then
    ArgumentCaptor<BlobType> captor = ArgumentCaptor.forClass(BlobType.class);
    verify(blobStorage).createUploadTarget(captor.capture());
    assertEquals(BlobType.BRAND_IMAGE, captor.getValue());
  }

  @Test
  @DisplayName("never logs the presigned URL when the image is registered (AC11)")
  void should_neverLogPresignedUrl_when_imageIsRegistered() {
    // Given: capture the service logger output at every level
    ch.qos.logback.classic.Logger logger =
        (ch.qos.logback.classic.Logger) LoggerFactory.getLogger(BrandImageServiceImpl.class);
    ch.qos.logback.core.read.ListAppender<ch.qos.logback.classic.spi.ILoggingEvent> appender =
        new ch.qos.logback.core.read.ListAppender<>();
    appender.start();
    logger.addAppender(appender);
    given(brandRepository.findById(1L)).willReturn(Optional.of(liveBrand(1L)));
    BlobUploadTarget target = uploadTarget(canonicalBrandImageKey());
    given(blobStorage.createUploadTarget(BlobType.BRAND_IMAGE)).willReturn(target);
    given(brandImageRepository.save(any(BrandImage.class))).willAnswer(inv -> inv.getArgument(0));
    try {
      // When
      brandImageService.createUploadTarget(1L);

      // Then
      String combined =
          appender.list.stream()
              .map(ch.qos.logback.classic.spi.ILoggingEvent::getFormattedMessage)
              .reduce("", (left, right) -> left + "\n" + right);
      org.assertj.core.api.Assertions.assertThat(combined)
          .doesNotContain("X-Amz-Signature")
          .doesNotContain(target.url().toString())
          .doesNotContain("example.invalid/brands/images/placeholder");
    } finally {
      logger.detachAppender(appender);
    }
  }

  @Test
  @DisplayName(
      "non-canonical port key surfaces IllegalArgumentException, never a client error (D8)")
  void should_throwIllegalArgument_when_portIssuesNonCanonicalKey() {
    // Given: a port-issued target whose key belongs to another blob type
    given(brandRepository.findById(1L)).willReturn(Optional.of(liveBrand(1L)));
    BlobUploadTarget foreignTarget =
        uploadTarget("products/images/9f2c4a1ed3b74e8fa1c6b0d2e5f7a913");
    given(blobStorage.createUploadTarget(BlobType.BRAND_IMAGE)).willReturn(foreignTarget);

    // When / Then: unreachable programming error -> 500 via handleGeneric, no new handler
    assertThrows(IllegalArgumentException.class, () -> brandImageService.createUploadTarget(1L));
    verify(brandImageRepository, never()).save(any(BrandImage.class));
  }
}
