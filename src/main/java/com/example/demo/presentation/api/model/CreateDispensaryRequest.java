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
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Builder
@AllArgsConstructor
@NoArgsConstructor
@Data
public class CreateDispensaryRequest {

  @Schema(name = "name", example = " ")
  @NotNull(message = "name must not be empty") private String name;

  @Schema(name = "logoImageURL", example = " ")
  private String logoImageURL;

  @Schema(name = "description", example = " ")
  private String description;

  @Schema(name = "license", example = " ")
  private String license;

  @Schema(
      name = "licenseStatus",
      allowableValues = {"PENDING", "APPROVED", "REJECTED"})
  @NotEmpty(message = "licenseStatus must not be empty")
  private String licenseStatus;

  @Schema(name = "phone", example = " ")
  private String phone;

  @Schema(name = "email", example = " ")
  @Email
  @NotNull(message = "email must not be empty") private String email;

  @Schema(name = "instagramURL", example = " ")
  private String instagramURL;

  @Schema(name = "twitterURL", example = " ")
  private String twitterURL;

  @Schema(name = "facebookURL", example = " ")
  private String facebookURL;

  @Schema(name = "websiteURL", example = " ")
  private String websiteURL;

  @Schema(name = "address", example = " ", required = true)
  @NotEmpty(message = "address must not be empty")
  private String address;

  @Schema(name = "commission", example = " ")
  private BigDecimal commission;

  @Schema(name = "adminId", example = " ")
  private Integer adminId;

  @Schema(name = "enabled", example = " ")
  private Boolean enabled;
}
