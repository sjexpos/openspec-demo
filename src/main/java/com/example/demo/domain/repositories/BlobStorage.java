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

import com.example.demo.domain.models.BlobType;
import com.example.demo.domain.models.BlobUploadTarget;
import java.util.Set;

/**
 * Storage-agnostic port for blob upload targets and removal. Application code depends on this
 * abstraction; infrastructure provides the implementation. No vendor-specific type appears in any
 * signature.
 */
public interface BlobStorage {

  /**
   * Issues a short-lived, pre-authorised upload target for a freshly generated key.
   *
   * @param blobType blob type the target is issued for; must not be null
   * @return upload target with a canonical key for the requested blob type
   * @throws IllegalArgumentException when {@code blobType} is null
   */
  BlobUploadTarget createUploadTarget(BlobType blobType);

  /**
   * Removes the given keys. Idempotent: unknown canonical keys are ignored. An empty set is a no-op
   * with no store call.
   *
   * @param keys canonical blob keys to remove; must not be null and every key must be canonical
   * @throws IllegalArgumentException when {@code keys} is null or any key is not canonical, before
   *     any store call
   */
  void remove(Set<String> keys);
}
