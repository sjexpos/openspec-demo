package com.example.demo.presentation.api.model;

import java.math.BigDecimal;

public record CreateDispensaryResponse(
  Integer id,
  String name,
  String logoImageURL,
  String description,
  String license,
  String licenseStatus,
  String phone,
  String email,
  String instagramURL,
  String twitterURL,
  String facebookURL,
  String websiteURL,
  String address,
  BigDecimal commission,
  Integer adminId,
  Boolean enabled) {}
