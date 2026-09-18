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

import com.example.demo.application.services.ProductService;
import com.example.demo.application.services.model.CreateProductCommand;
import com.example.demo.domain.models.product.Product;
import com.example.demo.presentation.api.DataResponse;
import com.example.demo.presentation.api.ProductApi;
import com.example.demo.presentation.api.model.CreateProductRequest;
import com.example.demo.presentation.api.model.CreateProductResponse;
import org.springframework.web.bind.annotation.RestController;

// Mapping-only controller (SRP): builds the command from the request, delegates every invariant
// and reference resolution to ProductService, and maps the persisted entity to the response DTO.
// No business logic lives here.
@RestController
public class ProductController implements ProductApi {

  private final ProductService productService;

  // Constructor injection against the ProductService interface (DIP): this controller never
  // depends on ProductServiceImpl or on any repository.
  public ProductController(ProductService productService) {
    this.productService = productService;
  }

  @Override
  public DataResponse<CreateProductResponse> create(CreateProductRequest request) {
    CreateProductCommand command =
        CreateProductCommand.builder()
            .ocpc(request.getOcpc())
            .title(request.getTitle())
            .description(request.getDescription())
            .collectionId(request.getCollectionId())
            .categoryId(request.getCategoryId())
            .subcategoryId(request.getSubcategoryId())
            .brandId(request.getBrandId())
            .strainId(request.getStrainId())
            .formatValue(request.getFormatValue())
            .formatUnitId(request.getFormatUnitId())
            .contentValue(request.getContentValue())
            .contentUnitId(request.getContentUnitId())
            .isCoreProduct(request.getIsCoreProduct())
            .approved(request.getApproved())
            .thc(request.getThc())
            .cbd(request.getCbd())
            .enabled(request.getEnabled())
            .build();

    Product product = this.productService.create(command);
    return new DataResponse<>(toCreateProductResponse(product));
  }

  private CreateProductResponse toCreateProductResponse(Product product) {
    return new CreateProductResponse(
        product.getId(),
        product.getOcpc(),
        product.getTitle(),
        product.getDescription(),
        product.getCollection().getId(),
        product.getCategory().getId(),
        product.getSubcategory().getId(),
        product.getBrand().getId(),
        product.getStrain().getId(),
        product.getFormatValue(),
        product.getFormatUnit().getId(),
        product.getContentValue(),
        product.getContentUnit().getId(),
        product.getIsCoreProduct(),
        product.getApproved(),
        product.getThc(),
        product.getCbd(),
        product.getEnabled());
  }
}
