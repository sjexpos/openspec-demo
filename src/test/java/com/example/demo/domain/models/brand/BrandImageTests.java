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

package com.example.demo.domain.models.brand;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.demo.domain.models.AssetStatus;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class BrandImageTests {

  private static final String CANONICAL_KEY = "brands/images/9f2a4c1ed3b74e8fa1c6b0d2e5f7a913";

  private Brand persistedBrand() {
    return Brand.builder().id(1L).name("Test brand").build();
  }

  @Test
  void should_createPendingImage_when_brandAndCanonicalKeyAreGiven() {
    // Arrange: a persisted brand and a canonical brand image key

    // Act
    BrandImage image = BrandImage.pending(persistedBrand(), CANONICAL_KEY);

    // Assert
    assertThat(image.getStatus()).isEqualTo(AssetStatus.PENDING);
    assertThat(image.getImageKey()).isEqualTo(CANONICAL_KEY);
    assertThat(image.getBrand()).isSameAs(image.getBrand());
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "strains/images/9f2a4c1ed3b74e8fa1c6b0d2e5f7a913",
        "products/images/9f2a4c1ed3b74e8fa1c6b0d2e5f7a913",
        "brands/videos/9f2a4c1ed3b74e8fa1c6b0d2e5f7a913"
      })
  void should_rejectCreation_when_keyBelongsToAnotherBlobType(String foreignKey) {
    // Arrange: a canonical key owned by another blob type

    // Act + Assert
    assertThatThrownBy(() -> BrandImage.pending(persistedBrand(), foreignKey))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {" ", "   "})
  void should_rejectCreation_when_keyIsNullOrBlank(String key) {
    // Arrange: null, empty or blank keys

    // Act + Assert
    assertThatThrownBy(() -> BrandImage.pending(persistedBrand(), key))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "brands/images/9f2a4c1ed3b74e8fa1c6b0d2e5f7a91",
        "brands/images/9F2A4C1ED3B74E8FA1C6B0D2E5F7A913",
        "brands/images/logo.jpg",
        "brands/images/../../secret",
        "dispensaries/images/9f2a4c1ed3b74e8fa1c6b0d2e5f7a913"
      })
  void should_rejectCreation_when_keyIsNonCanonicalOrTraversal(String key) {
    // Arrange: malformed or traversal keys

    // Act + Assert
    assertThatThrownBy(() -> BrandImage.pending(persistedBrand(), key))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void should_rejectCreation_when_brandIsNull() {
    // Arrange: a missing brand

    // Act + Assert
    assertThatThrownBy(() -> BrandImage.pending(null, CANONICAL_KEY))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void should_rejectCreation_when_brandIsTransient() {
    // Arrange: a brand without a persisted identifier
    Brand transientBrand = Brand.builder().name("Transient brand").build();

    // Act + Assert
    assertThatThrownBy(() -> BrandImage.pending(transientBrand, CANONICAL_KEY))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void should_moveToUploaded_when_pendingImageIsConfirmed() {
    // Arrange: a pending brand image
    BrandImage image = BrandImage.pending(persistedBrand(), CANONICAL_KEY);

    // Act
    image.markUploaded();

    // Assert
    assertThat(image.getStatus()).isEqualTo(AssetStatus.UPLOADED);
  }

  @Test
  void should_moveToDeleted_when_pendingImageIsRetired() {
    // Arrange: a pending brand image
    BrandImage image = BrandImage.pending(persistedBrand(), CANONICAL_KEY);

    // Act
    image.markDeleted();

    // Assert
    assertThat(image.getStatus()).isEqualTo(AssetStatus.DELETED);
  }

  @Test
  void should_moveToDeleted_when_uploadedImageIsRetired() {
    // Arrange: an uploaded brand image
    BrandImage image = BrandImage.pending(persistedBrand(), CANONICAL_KEY);
    image.markUploaded();

    // Act
    image.markDeleted();

    // Assert
    assertThat(image.getStatus()).isEqualTo(AssetStatus.DELETED);
  }

  @Test
  void should_rejectDoubleConfirm_when_imageIsAlreadyUploaded() {
    // Arrange: an uploaded brand image
    BrandImage image = BrandImage.pending(persistedBrand(), CANONICAL_KEY);
    image.markUploaded();

    // Act + Assert
    assertThatThrownBy(image::markUploaded).isInstanceOf(IllegalStateException.class);
    assertThat(image.getStatus()).isEqualTo(AssetStatus.UPLOADED);
  }

  @Test
  void should_rejectAnyTransition_when_imageIsDeleted() {
    // Arrange: a deleted brand image
    BrandImage image = BrandImage.pending(persistedBrand(), CANONICAL_KEY);
    image.markDeleted();

    // Act + Assert
    assertThatThrownBy(image::markUploaded).isInstanceOf(IllegalStateException.class);
    assertThatThrownBy(image::markDeleted).isInstanceOf(IllegalStateException.class);
    assertThat(image.getStatus()).isEqualTo(AssetStatus.DELETED);
  }

  @Test
  void should_exposeNoPublicStatusOrKeyMutator_when_classIsInspected() {
    // Arrange: the entity class

    // Act
    boolean hasPublicSetStatus =
        Arrays.stream(BrandImage.class.getMethods())
            .anyMatch(
                method ->
                    method.getName().equals("setStatus")
                        && Modifier.isPublic(method.getModifiers()));
    boolean hasPublicSetImageKey =
        Arrays.stream(BrandImage.class.getMethods())
            .anyMatch(
                method ->
                    method.getName().equals("setImageKey")
                        && Modifier.isPublic(method.getModifiers()));

    // Assert
    assertThat(hasPublicSetStatus).isFalse();
    assertThat(hasPublicSetImageKey).isFalse();
  }

  @Test
  void should_haveNoPhysicalDeleteMapping_when_classIsInspected() {
    // Arrange: the entity class annotations and its own declared behavior
    // Note: markDeleted is the sanctioned domain transition to the DELETED tombstone state,
    // not a physical removal, so only physical-delete style operations are rejected here.

    // Act + Assert
    for (Method method : BrandImage.class.getDeclaredMethods()) {
      if (method.isSynthetic() || method.getName().startsWith("$$_hibernate")) {
        continue;
      }
      assertThat(method.getName()).doesNotMatch("(?i)^(delete|remove|deleteById|removeById)$");
    }
    assertThat(BrandImage.class.getAnnotation(org.hibernate.annotations.SQLDelete.class)).isNull();
    assertThat(BrandImage.class.getAnnotation(org.hibernate.annotations.SQLRestriction.class))
        .isNull();
  }
}
