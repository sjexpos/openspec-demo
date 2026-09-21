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

package com.example.demo.domain.models;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class BlobTypeTests {

  @ParameterizedTest
  @EnumSource(BlobType.class)
  void should_exposeDistinctPrefix_when_eachBlobTypeIsInspected(BlobType blobType) {
    // Arrange: all six blob types

    // Act
    Set<String> prefixes =
        Arrays.stream(BlobType.values()).map(BlobType::prefix).collect(Collectors.toSet());

    // Assert
    assertThat(prefixes).hasSize(BlobType.values().length);
    assertThat(new HashSet<>(prefixes)).hasSize(prefixes.size());
    assertThat(blobType.prefix()).endsWith("/");
  }

  @ParameterizedTest
  @EnumSource(BlobType.class)
  void should_acceptKey_when_keyIsCanonical(BlobType blobType) {
    // Arrange: a canonical key built from the requested prefix plus 32 lowercase hex chars
    String key = blobType.prefix() + "9f2a4c1ed3b74e8fa1c6b0d2e5f7a913";

    // Act
    boolean canonical = BlobType.isCanonicalKey(key);

    // Assert
    assertThat(canonical).isTrue();
  }

  @Test
  void should_startWithProductsPrefix_when_blobTypeIsProductImageOrVideo() {
    // Arrange: product vs non-product blob types

    // Act + Assert
    assertThat(BlobType.PRODUCT_IMAGE.prefix()).startsWith("products/");
    assertThat(BlobType.PRODUCT_VIDEO.prefix()).startsWith("products/");
    assertThat(BlobType.BRAND_IMAGE.prefix()).doesNotStartWith("products/");
    assertThat(BlobType.BRAND_VIDEO.prefix()).doesNotStartWith("products/");
    assertThat(BlobType.STRAIN_IMAGE.prefix()).doesNotStartWith("products/");
    assertThat(BlobType.STRAIN_VIDEO.prefix()).doesNotStartWith("products/");
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "dispensaries/images/9f2a4c1ed3b74e8fa1c6b0d2e5f7a913",
        "brands/9f2a4c1ed3b74e8fa1c6b0d2e5f7a913",
        "products/9f2a4c1ed3b74e8fa1c6b0d2e5f7a913",
        "flyway/9f2a4c1ed3b74e8fa1c6b0d2e5f7a913"
      })
  void should_rejectKey_when_prefixIsUnknown(String key) {
    // Arrange: keys with prefixes outside the six managed blob types

    // Act
    boolean canonical = BlobType.isCanonicalKey(key);

    // Assert
    assertThat(canonical).isFalse();
  }

  static Stream<String> nonHex32Keys() {
    return Stream.of(
        "products/images/9f2a4c1ed3b74e8fa1c6b0d2e5f7a91",
        "products/images/9f2a4c1ed3b74e8fa1c6b0d2e5f7a9130",
        "products/images/9F2A4C1ED3B74E8FA1C6B0D2E5F7A913",
        "products/images/9f2a4c1ed3b74e8fa1c6b0d2/5f7a913",
        "products/images/logo.jpg");
  }

  @ParameterizedTest
  @MethodSource("nonHex32Keys")
  void should_rejectKey_when_randomPartIsNotHex32(String key) {
    // Arrange: keys with a known prefix but a malformed random part

    // Act
    boolean canonical = BlobType.isCanonicalKey(key);

    // Assert
    assertThat(canonical).isFalse();
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {" ", "   "})
  void should_rejectKey_when_keyIsNullOrBlank(String key) {
    // Arrange: null, empty or blank keys

    // Act
    boolean canonical = BlobType.isCanonicalKey(key);

    // Assert
    assertThat(canonical).isFalse();
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "products/images/../../secret",
        "products/images/9f2a4c1e/../3b74e8fa1c6b0d2e5f7a913",
        "brands/images/../../../etc/passwd"
      })
  void should_rejectKey_when_keyAttemptsPathTraversal(String key) {
    // Arrange: keys attempting path traversal

    // Act
    boolean canonical = BlobType.isCanonicalKey(key);

    // Assert
    assertThat(canonical).isFalse();
  }
}
