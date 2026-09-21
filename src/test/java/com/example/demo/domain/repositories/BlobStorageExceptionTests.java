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

package com.example.demo.domain.repositories;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class BlobStorageExceptionTests {

  @Test
  void should_exposeEmptyFailedKeys_when_builtWithMessageAndCause() {
    // Arrange
    RuntimeException cause = new RuntimeException("store failure");

    // Act
    BlobStorageException exception = new BlobStorageException("Could not remove blobs", cause);

    // Assert
    assertThat(exception.getMessage()).isEqualTo("Could not remove blobs");
    assertThat(exception.getCause()).isSameAs(cause);
    assertThat(exception.getFailedKeys()).isEmpty();
  }

  @Test
  void should_exposeImmutableFailedKeys_when_builtWithMessageAndFailedKeys() {
    // Arrange
    Set<String> failedKeys = new HashSet<>();
    failedKeys.add("products/images/9f2a4c1ed3b74e8fa1c6b0d2e5f7a913");

    // Act
    BlobStorageException exception = new BlobStorageException("Could not remove blobs", failedKeys);

    // Assert
    assertThat(exception.getFailedKeys()).containsExactlyElementsOf(failedKeys);
    assertThat(exception.getCause()).isNull();
  }

  @Test
  void should_exposeFailedKeysAndCause_when_builtWithMessageFailedKeysAndCause() {
    // Arrange
    RuntimeException cause = new RuntimeException("store failure");
    Set<String> failedKeys =
        Set.of(
            "products/images/9f2a4c1ed3b74e8fa1c6b0d2e5f7a913",
            "brands/images/1c7d2e3f4a5b6c7d8e9f0a1b2c3d4e5f");

    // Act
    BlobStorageException exception =
        new BlobStorageException("Could not remove blobs", failedKeys, cause);

    // Assert
    assertThat(exception.getFailedKeys()).containsExactlyInAnyOrderElementsOf(failedKeys);
    assertThat(exception.getCause()).isSameAs(cause);
  }

  @Test
  void should_storeImmutableCopy_when_failedKeysAreMutatedAfterwards() {
    // Arrange
    Set<String> failedKeys = new HashSet<>();
    failedKeys.add("products/images/9f2a4c1ed3b74e8fa1c6b0d2e5f7a913");
    BlobStorageException exception = new BlobStorageException("Could not remove blobs", failedKeys);

    // Act
    failedKeys.clear();

    // Assert
    assertThat(exception.getFailedKeys()).hasSize(1);
  }
}
