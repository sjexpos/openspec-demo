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

package com.example.demo.presentation.api;

import com.example.demo.presentation.api.model.CreateProductRequest;
import com.example.demo.presentation.api.model.CreateProductResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;

@RequestMapping("/api/products")
@Tag(name = "Products", description = "Product catalog management endpoints")
@Validated
public interface ProductApi {

  @PostMapping
  @ResponseStatus(value = HttpStatus.CREATED)
  @Operation(
      summary = "Create a new product",
      description = "Creates a new catalog product and returns it with the generated ID")
  @ApiResponses({
    @ApiResponse(responseCode = "201", description = "Product created successfully"),
    @ApiResponse(responseCode = "400", description = "Invalid input"),
    @ApiResponse(
        responseCode = "404",
        description =
            "A referenced collection, category, subcategory, brand, strain or unit "
                + "could not be resolved"),
    @ApiResponse(
        responseCode = "409",
        description =
            "The ocpc is already taken by a live product, or the subcategory does not "
                + "belong to the supplied category")
  })
  DataResponse<CreateProductResponse> create(@Valid @RequestBody CreateProductRequest request);
}
