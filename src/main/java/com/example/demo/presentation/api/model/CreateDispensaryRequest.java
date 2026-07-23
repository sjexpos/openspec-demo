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
