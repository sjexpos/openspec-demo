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

import com.example.demo.domain.models.BlobUploadTarget;

/**
 * Application service orchestrating brand image upload-target creation. Resolves the brand, mints
 * an upload target through the {@code BlobStorage} port for the {@code BRAND_IMAGE} blob type,
 * and persists the {@code PENDING} brand image as an invisible server-side side effect. Returns
 * only the upload target; the persisted row is never exposed.
 */
public interface BrandImageService {

  /**
   * Registers a new image for an existing brand and returns a short-lived, credential-free upload
   * target for it.
   *
   * @param brandId brand identifier; must resolve to a non-soft-deleted brand
   * @return upload target issued by the blob-storage port
   */
  BlobUploadTarget createUploadTarget(Long brandId);
}
