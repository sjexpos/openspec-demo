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

package com.example.demo.integration.repositories;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.demo.domain.models.AssetStatus;
import com.example.demo.domain.models.brand.Brand;
import com.example.demo.domain.models.brand.BrandImage;
import com.example.demo.domain.models.brand.BrandType;
import com.example.demo.domain.repositories.BrandImageRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

class BrandImageRepositoryTests extends RepositoryTest {

  @Autowired private BrandImageRepository brandImageRepository;

  private Brand persistedBrand() {
    BrandType brandType = new BrandType();
    brandType.setName("grower");
    brandType = this.entityManager.persistAndFlush(brandType);
    Brand brand =
        Brand.builder()
            .name("Test brand")
            .description("Test brand description")
            .email("brand-test@yopmail.com")
            .stateLicense("LIC-TEST")
            .brandType(brandType)
            .logoImageUrl("https://example.com/logo.png")
            .adminId(1)
            .enabled(Boolean.TRUE)
            .build();
    return this.entityManager.persistAndFlush(brand);
  }

  private String canonicalKey(String suffix) {
    return "brands/images/" + suffix;
  }

  @Test
  void should_persistPendingImage_when_brandAndCanonicalKeyAreGiven() {
    // Arrange: a persisted brand and a canonical brand image key
    Brand brand = persistedBrand();

    // Act
    BrandImage saved =
        brandImageRepository.saveAndFlush(
            BrandImage.pending(brand, canonicalKey("9f2a4c1ed3b74e8fa1c6b0d2e5f7a913")));
    this.entityManager.clear();
    BrandImage reloaded = brandImageRepository.findById(saved.getId()).orElseThrow();

    // Assert
    assertThat(reloaded.getStatus()).isEqualTo(AssetStatus.PENDING);
    assertThat(reloaded.getImageKey()).isEqualTo(canonicalKey("9f2a4c1ed3b74e8fa1c6b0d2e5f7a913"));
    assertThat(reloaded.getCreatedAt()).isNotNull();
    assertThat(reloaded.getModifiedAt()).isNotNull();
  }

  @Test
  void should_storeStatusAsText_when_imageRowIsReadNatively() {
    // Arrange: a persisted pending brand image
    Brand brand = persistedBrand();
    BrandImage saved =
        brandImageRepository.saveAndFlush(
            BrandImage.pending(brand, canonicalKey("9f2a4c1ed3b74e8fa1c6b0d2e5f7a913")));

    // Act
    String storedStatus =
        (String)
            this.entityManager
                .getEntityManager()
                .createNativeQuery("SELECT status FROM brand_images WHERE id = :id")
                .setParameter("id", saved.getId())
                .getSingleResult();

    // Assert
    assertThat(storedStatus).isEqualTo("PENDING");
  }

  @Test
  void should_rejectWrite_when_statusValueIsOutsideTheLifecycle() {
    // Arrange: a persisted brand
    Brand brand = persistedBrand();

    // Act + Assert
    assertThatThrownBy(
            () ->
                this.entityManager
                    .getEntityManager()
                    .createNativeQuery(
                        "INSERT INTO brand_images (brand_id, image_key, status, created_at)"
                            + " VALUES (:brandId, :imageKey, 'ARCHIVED', now())")
                    .setParameter("brandId", brand.getId())
                    .setParameter("imageKey", canonicalKey("9f2a4c1ed3b74e8fa1c6b0d2e5f7a913"))
                    .executeUpdate())
        .isInstanceOf(Exception.class);
  }

  @Test
  void should_rejectWrite_when_imageKeyIsDuplicated() {
    // Arrange: a recorded brand image key
    Brand brand = persistedBrand();
    brandImageRepository.saveAndFlush(
        BrandImage.pending(brand, canonicalKey("9f2a4c1ed3b74e8fa1c6b0d2e5f7a913")));

    // Act + Assert
    assertThatThrownBy(
            () ->
                brandImageRepository.saveAndFlush(
                    BrandImage.pending(brand, canonicalKey("9f2a4c1ed3b74e8fa1c6b0d2e5f7a913"))))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void should_rejectWrite_when_brandReferenceDoesNotExist() {
    // Arrange: a brand identifier with no brand row
    Brand ghostBrand = Brand.builder().id(999999L).name("Ghost brand").build();

    // Act + Assert
    assertThatThrownBy(
            () ->
                brandImageRepository.saveAndFlush(
                    BrandImage.pending(
                        ghostBrand, canonicalKey("9f2a4c1ed3b74e8fa1c6b0d2e5f7a913"))))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void should_rejectWrite_when_statusIsNull() {
    // Arrange: a brand image assembled without a lifecycle state
    Brand brand = persistedBrand();
    BrandImage withoutStatus =
        BrandImage.builder()
            .brand(brand)
            .imageKey(canonicalKey("9f2a4c1ed3b74e8fa1c6b0d2e5f7a913"))
            .build();

    // Act + Assert
    assertThatThrownBy(() -> brandImageRepository.saveAndFlush(withoutStatus))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void should_filterByBrandAndStatus_when_oneRowPerStatusExists() {
    // Arrange: one brand image per lifecycle state for the same brand
    Brand brand = persistedBrand();
    BrandImage pending =
        brandImageRepository.save(
            BrandImage.pending(brand, canonicalKey("9f2a4c1ed3b74e8fa1c6b0d2e5f7a913")));
    BrandImage uploaded =
        brandImageRepository.save(
            BrandImage.pending(brand, canonicalKey("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa")));
    uploaded.markUploaded();
    BrandImage deleted =
        brandImageRepository.save(
            BrandImage.pending(brand, canonicalKey("bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb")));
    deleted.markDeleted();
    brandImageRepository.flush();

    // Act
    List<BrandImage> visible =
        brandImageRepository.findByBrandIdAndStatus(brand.getId(), AssetStatus.UPLOADED);
    List<BrandImage> intents =
        brandImageRepository.findByBrandIdAndStatus(brand.getId(), AssetStatus.PENDING);
    List<BrandImage> tombstones =
        brandImageRepository.findByBrandIdAndStatus(brand.getId(), AssetStatus.DELETED);

    // Assert
    assertThat(visible).hasSize(1);
    assertThat(visible.get(0).getId()).isEqualTo(uploaded.getId());
    assertThat(intents).hasSize(1);
    assertThat(intents.get(0).getId()).isEqualTo(pending.getId());
    assertThat(tombstones).hasSize(1);
    assertThat(tombstones.get(0).getId()).isEqualTo(deleted.getId());
  }

  @Test
  void should_returnImage_when_imageKeyMatches() {
    // Arrange: a recorded brand image
    Brand brand = persistedBrand();
    BrandImage saved =
        brandImageRepository.saveAndFlush(
            BrandImage.pending(brand, canonicalKey("9f2a4c1ed3b74e8fa1c6b0d2e5f7a913")));

    // Act
    Optional<BrandImage> hit =
        brandImageRepository.findByImageKey(canonicalKey("9f2a4c1ed3b74e8fa1c6b0d2e5f7a913"));
    Optional<BrandImage> miss =
        brandImageRepository.findByImageKey(canonicalKey("cccccccccccccccccccccccccccccccc"));

    // Assert
    assertThat(hit).isPresent();
    assertThat(hit.orElseThrow().getId()).isEqualTo(saved.getId());
    assertThat(miss).isEmpty();
  }

  @Test
  void should_reportKeyPresence_when_imageKeyIsChecked() {
    // Arrange: a recorded brand image
    Brand brand = persistedBrand();
    brandImageRepository.saveAndFlush(
        BrandImage.pending(brand, canonicalKey("9f2a4c1ed3b74e8fa1c6b0d2e5f7a913")));

    // Act + Assert
    assertThat(
            brandImageRepository.existsByImageKey(canonicalKey("9f2a4c1ed3b74e8fa1c6b0d2e5f7a913")))
        .isTrue();
    assertThat(
            brandImageRepository.existsByImageKey(canonicalKey("cccccccccccccccccccccccccccccccc")))
        .isFalse();
  }

  @Test
  void should_roundTripTransition_when_pendingImageIsConfirmed() {
    // Arrange: a persisted pending brand image
    Brand brand = persistedBrand();
    BrandImage saved =
        brandImageRepository.saveAndFlush(
            BrandImage.pending(brand, canonicalKey("9f2a4c1ed3b74e8fa1c6b0d2e5f7a913")));

    // Act
    saved.markUploaded();
    brandImageRepository.saveAndFlush(saved);
    this.entityManager.clear();
    BrandImage reloaded = brandImageRepository.findById(saved.getId()).orElseThrow();

    // Assert
    assertThat(reloaded.getStatus()).isEqualTo(AssetStatus.UPLOADED);
    assertThat(reloaded.getModifiedAt()).isNotNull();
  }

  @Test
  void should_keepRow_when_imageIsMovedToDeleted() {
    // Arrange: a persisted brand image
    Brand brand = persistedBrand();
    BrandImage saved =
        brandImageRepository.saveAndFlush(
            BrandImage.pending(brand, canonicalKey("9f2a4c1ed3b74e8fa1c6b0d2e5f7a913")));

    // Act: retire the image, never physically remove it
    saved.markDeleted();
    brandImageRepository.saveAndFlush(saved);
    this.entityManager.flush();
    this.entityManager.clear();

    // Assert: the row remains physically present as a tombstone
    Long physicalCount =
        ((Number)
                this.entityManager
                    .getEntityManager()
                    .createNativeQuery("SELECT COUNT(*) FROM brand_images WHERE id = :id")
                    .setParameter("id", saved.getId())
                    .getSingleResult())
            .longValue();
    assertThat(physicalCount).isEqualTo(1L);
    Optional<BrandImage> tombstone = brandImageRepository.findById(saved.getId());
    assertThat(tombstone).isPresent();
    assertThat(tombstone.orElseThrow().getStatus()).isEqualTo(AssetStatus.DELETED);
  }
}
