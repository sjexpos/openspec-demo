package com.example.demo.presentation.api.model;

public record RemoveDispensaryResponse(
  Integer id,
  String name,
  String description,
  String license,
  String licenseStatus,
  String address,
  Integer adminId) {}
