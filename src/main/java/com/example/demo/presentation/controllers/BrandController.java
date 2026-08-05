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

import com.example.demo.application.exceptions.NotFoundException;
import com.example.demo.application.services.BrandService;
import com.example.demo.domain.models.brand.Brand;
import com.example.demo.presentation.api.BrandApi;
import com.example.demo.presentation.api.DataResponse;
import com.example.demo.presentation.api.model.CreateBrandRequest;
import com.example.demo.presentation.api.model.CreateBrandResponse;
import com.example.demo.presentation.api.model.GetAllBrandsResponse;
import com.example.demo.presentation.api.model.GetBrandResponse;
import com.example.demo.presentation.api.model.UpdateBrandRequest;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class BrandController implements BrandApi {

  private final BrandService brandService;

  public BrandController(BrandService brandService) {
    this.brandService = brandService;
  }

  @Override
  public DataResponse<List<GetAllBrandsResponse>> getAll() {
    return StreamSupport.stream(this.brandService.findAll().spliterator(), false)
        .map(
            brand ->
                new GetAllBrandsResponse(
                    brand.getId(),
                    brand.getName(),
                    brand.getDescription(),
                    brand.getEmail(),
                    brand.getStateLicense(),
                    brand.getBrandType().getName(),
                    brand.getLogoImageUrl(),
                    brand.getEnabled()))
        .collect(Collectors.collectingAndThen(Collectors.toList(), DataResponse::new));
  }

  @Override
  public DataResponse<CreateBrandResponse> create(CreateBrandRequest request) {
    Brand brand =
        this.brandService.create(
            request.getName(),
            request.getDescription(),
            request.getEmail(),
            request.getStateLicense(),
            request.getBrandTypeName(),
            request.getLogoImageUrl(),
            request.getInstagramUrl(),
            request.getTwitterUrl(),
            request.getFacebookUrl(),
            request.getWebsiteUrl(),
            request.getAdminId(),
            request.getEnabled());
    return new DataResponse<>(
        new CreateBrandResponse(
            brand.getId(),
            brand.getName(),
            brand.getDescription(),
            brand.getEmail(),
            brand.getStateLicense(),
            brand.getBrandType().getName(),
            brand.getLogoImageUrl(),
            brand.getInstagramUrl(),
            brand.getTwitterUrl(),
            brand.getFacebookUrl(),
            brand.getWebsiteUrl(),
            brand.getAdminId(),
            brand.getEnabled()));
  }

  @Override
  public DataResponse<GetBrandResponse> getById(Long id) {
    Brand brand =
        this.brandService
            .getById(id)
            .orElseThrow(() -> new NotFoundException("Brand not found with ID: " + id));
    return new DataResponse<>(toGetBrandResponse(brand));
  }

  @Override
  public DataResponse<GetBrandResponse> update(Long id, UpdateBrandRequest request) {
    Brand brand =
        this.brandService.update(
            id,
            request.getName(),
            request.getDescription(),
            request.getEmail(),
            request.getStateLicense(),
            request.getBrandTypeName(),
            request.getLogoImageUrl(),
            request.getInstagramUrl(),
            request.getTwitterUrl(),
            request.getFacebookUrl(),
            request.getWebsiteUrl(),
            request.getAdminId(),
            request.getEnabled());
    return new DataResponse<>(toGetBrandResponse(brand));
  }

  private GetBrandResponse toGetBrandResponse(Brand brand) {
    return new GetBrandResponse(
        brand.getId(),
        brand.getName(),
        brand.getDescription(),
        brand.getEmail(),
        brand.getStateLicense(),
        brand.getBrandType().getName(),
        brand.getLogoImageUrl(),
        brand.getInstagramUrl(),
        brand.getTwitterUrl(),
        brand.getFacebookUrl(),
        brand.getWebsiteUrl(),
        brand.getAdminId(),
        brand.getEnabled());
  }
}
