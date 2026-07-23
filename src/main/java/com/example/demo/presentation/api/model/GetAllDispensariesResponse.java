package com.example.demo.presentation.api.model;

public record GetAllDispensariesResponse(
    Integer id, 
    String name, 
    String license, 
    String phone, 
    String email, 
    String instagramUrl, 
    String twitterUrl, 
    String facebookUrl, 
    String websiteUrl, 
    Double commission, 
    Integer adminId, 
    Boolean enabled, 
    String licenseStatus, 
    String address, 
    String zipCode, 
    Double longitude, 
    Double latitude) {

}
