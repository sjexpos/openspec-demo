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

package com.example.demo.presentation.api.model;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

// Request DTO for POST /api/products (D8). @NotBlank is used instead of @NotEmpty because the
// spec requires rejecting a required field that is "absent, null or blank" ("   " must fail).
// No audit or lifecycle metadata is accepted here — the strongest possible form of the spec's
// "SHALL NOT accept audit or lifecycle metadata" requirement.
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Data
public class CreateProductRequest {

  @Schema(name = "ocpc", example = "OCPC-12345")
  @NotBlank(message = "ocpc must not be blank")
  @Size(max = 64, message = "ocpc must not exceed 64 characters")
  private String ocpc;

  @Schema(name = "title", example = "Blue Dream 1g Pre-Roll")
  @NotBlank(message = "title must not be blank")
  @Size(max = 255, message = "title must not exceed 255 characters")
  private String title;

  @Schema(name = "description", example = "A balanced hybrid pre-roll")
  private String description;

  @Schema(name = "collectionId", example = "1")
  @NotNull(message = "collectionId must not be null") @Positive(message = "collectionId must be positive") private Long collectionId;

  @Schema(name = "categoryId", example = "2")
  @NotNull(message = "categoryId must not be null") @Positive(message = "categoryId must be positive") private Long categoryId;

  @Schema(name = "subcategoryId", example = "3")
  @NotNull(message = "subcategoryId must not be null") @Positive(message = "subcategoryId must be positive") private Long subcategoryId;

  @Schema(name = "brandId", example = "4")
  @NotNull(message = "brandId must not be null") @Positive(message = "brandId must be positive") private Long brandId;

  @Schema(name = "strainId", example = "5")
  @NotNull(message = "strainId must not be null") @Positive(message = "strainId must be positive") private Long strainId;

  @Schema(name = "formatValue", example = "1")
  @NotNull(message = "formatValue must not be null") @Positive(message = "formatValue must be positive") private Integer formatValue;

  @Schema(name = "formatUnitId", example = "6")
  @NotNull(message = "formatUnitId must not be null") @Positive(message = "formatUnitId must be positive") private Long formatUnitId;

  @Schema(name = "contentValue", example = "1")
  @NotNull(message = "contentValue must not be null") @Positive(message = "contentValue must be positive") private Integer contentValue;

  @Schema(name = "contentUnitId", example = "7")
  @NotNull(message = "contentUnitId must not be null") @Positive(message = "contentUnitId must be positive") private Long contentUnitId;

  @Schema(name = "isCoreProduct", example = "true")
  private Boolean isCoreProduct;

  @Schema(name = "approved", example = "true")
  private Boolean approved;

  @Schema(name = "thc", example = "18")
  @PositiveOrZero(message = "thc must not be negative")
  private Integer thc;

  @Schema(name = "cbd", example = "1")
  @PositiveOrZero(message = "cbd must not be negative")
  private Integer cbd;

  @Schema(name = "enabled", example = "true")
  private Boolean enabled;
}
