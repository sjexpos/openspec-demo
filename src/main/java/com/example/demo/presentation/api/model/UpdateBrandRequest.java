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
import jakarta.validation.constraints.Email;
import lombok.Data;

@Data
public class UpdateBrandRequest {

  @Schema(name = "name", example = "Green Leaf Farms")
  private String name;

  @Schema(name = "description", example = "Premium cannabis products")
  private String description;

  @Schema(name = "email", example = "brand@yopmail.com")
  @Email(message = "must be a well-formed email address")
  private String email;

  @Schema(name = "stateLicense", example = "CAL-2026-001")
  private String stateLicense;

  @Schema(name = "brandTypeName", example = "grower")
  private String brandTypeName;

  @Schema(name = "logoImageUrl", example = "https://example.com/logo.png")
  private String logoImageUrl;

  @Schema(name = "instagramUrl", example = "https://instagram.com/greenleaf")
  private String instagramUrl;

  @Schema(name = "twitterUrl", example = "https://twitter.com/greenleaf")
  private String twitterUrl;

  @Schema(name = "facebookUrl", example = "https://facebook.com/greenleaf")
  private String facebookUrl;

  @Schema(name = "websiteUrl", example = "https://greenleaf.com")
  private String websiteUrl;

  @Schema(name = "adminId", example = "7")
  private Integer adminId;

  @Schema(name = "enabled", example = "true")
  private Boolean enabled;
}
