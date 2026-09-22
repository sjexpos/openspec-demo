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

package com.example.demo.application.services.impl;

import com.example.demo.application.exceptions.NotFoundException;
import com.example.demo.application.services.BrandImageService;
import com.example.demo.domain.models.BlobType;
import com.example.demo.domain.models.BlobUploadTarget;
import com.example.demo.domain.models.brand.Brand;
import com.example.demo.domain.models.brand.BrandImage;
import com.example.demo.domain.repositories.BlobStorage;
import com.example.demo.domain.repositories.BrandImageRepository;
import com.example.demo.domain.repositories.BrandRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Orchestrates brand image upload-target creation: resolve the brand first, mint the upload target
 * through the {@code BlobStorage} port, then persist the {@code PENDING} brand image. Returns only
 * the upload target; the persisted row is an invisible server-side side effect.
 *
 * <p>Presigning inside {@code @Transactional} is an explicit, justified exception to the usual
 * rule: {@code S3Presigner.presignPutObject} is a verified offline SigV4 computation with no
 * network call, so no database connection is held during external I/O. If the port ever gains
 * network I/O on this path, the presign must move outside the transaction.
 */
@Slf4j
@Service
public class BrandImageServiceImpl implements BrandImageService {

  private final BrandRepository brandRepository;
  private final BrandImageRepository brandImageRepository;
  private final BlobStorage blobStorage;

  public BrandImageServiceImpl(
      BrandRepository brandRepository,
      BrandImageRepository brandImageRepository,
      BlobStorage blobStorage) {
    this.brandRepository = brandRepository;
    this.brandImageRepository = brandImageRepository;
    this.blobStorage = blobStorage;
  }

  /** Returns only the upload target; the persisted PENDING row is an invisible side effect. */
  @Override
  @Transactional(rollbackFor = Exception.class, propagation = Propagation.REQUIRED)
  public BlobUploadTarget createUploadTarget(Long brandId) {
    // 1. Resolve first. @SQLRestriction("deleted_at IS NULL") makes soft-deleted brands
    // invisible, so this single call covers both "missing" and "soft-deleted". Doing this
    // before step 2 means no key is minted for a bad request.
    Brand brand =
        this.brandRepository
            .findById(brandId)
            .orElseThrow(() -> new NotFoundException("Brand not found with ID: " + brandId));

    // 2. Mint the key. Local SigV4 computation, no network call, no object created.
    // Throws BlobStorageException -> 502, transaction rolls back, nothing persisted.
    BlobUploadTarget target = this.blobStorage.createUploadTarget(BlobType.BRAND_IMAGE);

    // 3. Record the intent. The entity factory re-validates key ownership
    // (BRAND_IMAGE.isKeyOf) and forces PENDING; the service never sets the status itself.
    BrandImage saved = this.brandImageRepository.save(BrandImage.pending(brand, target.key()));

    // 4. Log the key only. NEVER the URL.
    log.info(
        "Brand image {} registered as PENDING for brand {} with key {}",
        saved.getId(),
        brandId,
        saved.getImageKey());

    // The saved row is a side effect the caller never sees: only the target is returned.
    return target;
  }
}
