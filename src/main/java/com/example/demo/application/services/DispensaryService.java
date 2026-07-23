package com.example.demo.application.services;

import java.math.BigDecimal;
import java.util.Optional;

import com.example.demo.application.exceptions.NotFoundException;
import com.example.demo.domain.models.dispensary.Dispensary;

public interface DispensaryService {

    Iterable<Dispensary> findAll();

    Dispensary create(String name, String logoImageURL, String description, String license, String licenseStatus, String phone, String email, String instagramURL, String twitterURL, String facebookURL, String websiteURL, String address, BigDecimal commission, Integer adminId, Boolean enabled);

    Optional<Dispensary> getById(Long id);

    Optional<Dispensary> deleteById(Long id) throws NotFoundException;

}
