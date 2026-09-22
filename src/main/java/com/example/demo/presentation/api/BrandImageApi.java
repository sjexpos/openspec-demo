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

import com.example.demo.presentation.api.model.CreateBrandImageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;

@RequestMapping("/api/brands/{brandId}/images")
@Tag(name = "Brand Images", description = "Brand image asset endpoints")
@Validated
public interface BrandImageApi {

  @PostMapping
  @ResponseStatus(value = HttpStatus.CREATED)
  @Operation(
      summary = "Create a brand image upload target",
      description =
          "Registers a new image for a brand and returns a short-lived, credential-free upload"
              + " target. No request body is accepted; the blob key is generated server-side.")
  @ApiResponses({
    @ApiResponse(responseCode = "201", description = "Upload target issued successfully"),
    @ApiResponse(responseCode = "400", description = "Invalid brand identifier"),
    @ApiResponse(responseCode = "404", description = "Brand not found"),
    @ApiResponse(responseCode = "502", description = "Blob storage is currently unavailable")
  })
  ResponseEntity<DataResponse<CreateBrandImageResponse>> createUploadTarget(
      @Parameter(description = "Brand ID", required = true) @PathVariable Long brandId);
}
