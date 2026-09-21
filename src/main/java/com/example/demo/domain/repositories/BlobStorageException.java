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

import java.util.Set;

/**
 * Domain error reported by the {@code BlobStorage} port. Carries the keys affected by a partial
 * batch failure; empty when the failure is not a partial batch failure. Never exposes a
 * vendor-specific type.
 */
public class BlobStorageException extends RuntimeException {

  private final Set<String> failedKeys;

  public BlobStorageException(String message, Throwable cause) {
    super(message, cause);
    this.failedKeys = Set.of();
  }

  public BlobStorageException(String message, Set<String> failedKeys) {
    super(message);
    this.failedKeys = Set.copyOf(failedKeys);
  }

  public BlobStorageException(String message, Set<String> failedKeys, Throwable cause) {
    super(message, cause);
    this.failedKeys = Set.copyOf(failedKeys);
  }

  /** Keys that failed in a partial batch failure; empty otherwise. Immutable. */
  public Set<String> getFailedKeys() {
    return this.failedKeys;
  }
}
