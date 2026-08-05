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

import com.example.demo.presentation.api.model.CreateBrandRequest;
import com.example.demo.presentation.api.model.CreateBrandResponse;
import com.example.demo.presentation.api.model.GetAllBrandsResponse;
import com.example.demo.presentation.api.model.GetBrandResponse;
import com.example.demo.presentation.api.model.UpdateBrandRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;

@RequestMapping("/api/brands")
@Tag(name = "Brands", description = "Brand management endpoints")
@Validated
public interface BrandApi {

  @GetMapping
  @ResponseStatus(value = HttpStatus.OK)
  @Operation(summary = "List all brands", description = "Returns a list of all brands")
  @ApiResponse(responseCode = "200", description = "Brands retrieved successfully")
  DataResponse<List<GetAllBrandsResponse>> getAll();

  @PostMapping
  @ResponseStatus(value = HttpStatus.CREATED)
  @Operation(
      summary = "Create a new brand",
      description = "Creates a new brand and returns it with the generated ID")
  @ApiResponses({
    @ApiResponse(responseCode = "201", description = "Brand created successfully"),
    @ApiResponse(responseCode = "400", description = "Invalid input")
  })
  DataResponse<CreateBrandResponse> create(@Valid @RequestBody CreateBrandRequest request);

  @GetMapping("/{id}")
  @ResponseStatus(value = HttpStatus.OK)
  @Operation(summary = "Get brand by ID", description = "Returns a single brand by its ID")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "Brand found"),
    @ApiResponse(responseCode = "404", description = "Brand not found")
  })
  DataResponse<GetBrandResponse> getById(
      @Parameter(description = "Brand ID", required = true) @PathVariable Long id);

  @PatchMapping("/{id}")
  @ResponseStatus(value = HttpStatus.OK)
  @Operation(
      summary = "Update a brand",
      description = "Partially or fully updates a brand and returns the updated brand")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "Brand updated successfully"),
    @ApiResponse(responseCode = "400", description = "Invalid input"),
    @ApiResponse(responseCode = "404", description = "Brand not found")
  })
  DataResponse<GetBrandResponse> update(
      @Parameter(description = "Brand ID", required = true) @PathVariable Long id,
      @Valid @RequestBody UpdateBrandRequest request);
}
