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

import com.example.demo.domain.models.AssetStatus;
import com.example.demo.domain.models.BaseEntity;
import com.example.demo.domain.models.BlobType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Brand image asset mapped to {@code brand_images}. Each row records the opaque canonical blob key
 * issued by the blob-storage port for the {@code BRAND_IMAGE} blob type, never a presigned URL,
 * and carries the shared asset lifecycle state. Images are created only as {@code PENDING} via
 * {@link #pending(Brand, String)}; the key is immutable and unique; state changes happen only via
 * {@link #markUploaded()} and {@link #markDeleted()}. Removal is represented solely by the {@code
 * DELETED} state: rows are never physically deleted.
 */
@Entity
@Table(name = "brand_images")
@Getter
@Setter
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BrandImage extends BaseEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @EqualsAndHashCode.Include
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "brand_id", nullable = false)
  private Brand brand;

  @Setter(AccessLevel.NONE)
  @Column(name = "image_key", nullable = false, updatable = false, unique = true)
  private String imageKey;

  @Setter(AccessLevel.NONE)
  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 16)
  private AssetStatus status;

  /**
   * Sole sanctioned creation path. Records the intent to upload before the upload is confirmed, so
   * the new image always starts in {@code PENDING}.
   */
  public static BrandImage pending(Brand brand, String imageKey) {
    if (brand == null || brand.getId() == null) {
      throw new IllegalArgumentException("Brand image requires a persisted brand");
    }
    if (!BlobType.BRAND_IMAGE.isKeyOf(imageKey)) {
      throw new IllegalArgumentException("Brand image requires a canonical BRAND_IMAGE blob key");
    }
    return BrandImage.builder().brand(brand).imageKey(imageKey).status(AssetStatus.PENDING).build();
  }

  /** Confirms the blob upload, moving the image from {@code PENDING} to {@code UPLOADED}. */
  public void markUploaded() {
    transitionTo(AssetStatus.UPLOADED);
  }

  /** Retires the image, moving it from {@code PENDING} or {@code UPLOADED} to {@code DELETED}. */
  public void markDeleted() {
    transitionTo(AssetStatus.DELETED);
  }

  private void transitionTo(AssetStatus target) {
    if (!this.status.canTransitionTo(target)) {
      throw new IllegalStateException(
          "Cannot transition brand image from " + this.status + " to " + target);
    }
    this.status = target;
  }
}
