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

package com.example.demo.presentation.controllers;

import com.example.demo.application.services.BrandImageService;
import com.example.demo.domain.models.BlobUploadTarget;
import com.example.demo.presentation.api.BrandImageApi;
import com.example.demo.presentation.api.DataResponse;
import com.example.demo.presentation.api.model.CreateBrandImageResponse;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

/**
 * Bearer-capability handling for brand image upload targets. Maps the port-issued {@code
 * BlobUploadTarget} to the three-string response DTO and marks the response non-cacheable. Never
 * touches the {@code BrandImage} entity or its lazy {@code Brand} proxy, never sets a {@code
 * Location} header, and never logs the URL.
 */
@RestController
public class BrandImageController implements BrandImageApi {

  private final BrandImageService brandImageService;

  public BrandImageController(BrandImageService brandImageService) {
    this.brandImageService = brandImageService;
  }

  @Override
  public ResponseEntity<DataResponse<CreateBrandImageResponse>> createUploadTarget(Long brandId) {
    BlobUploadTarget target = this.brandImageService.createUploadTarget(brandId);
    CreateBrandImageResponse body =
        new CreateBrandImageResponse(
            target.url().toString(), target.method().name(), target.expiresAt().toString());
    return ResponseEntity.status(HttpStatus.CREATED)
        .cacheControl(CacheControl.noStore())
        .body(new DataResponse<>(body));
  }
}
