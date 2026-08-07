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

package com.example.demo.application.services.impl;

import com.example.demo.application.exceptions.NotFoundException;
import com.example.demo.application.services.BrandService;
import com.example.demo.domain.models.brand.Brand;
import com.example.demo.domain.models.brand.BrandType;
import com.example.demo.domain.repositories.BrandRepository;
import com.example.demo.domain.repositories.BrandTypeRepository;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
public class BrandServiceImpl implements BrandService {

  private final BrandRepository brandRepository;
  private final BrandTypeRepository brandTypeRepository;

  public BrandServiceImpl(
      BrandRepository brandRepository, BrandTypeRepository brandTypeRepository) {
    this.brandRepository = brandRepository;
    this.brandTypeRepository = brandTypeRepository;
  }

  @Override
  public Iterable<Brand> findAll() {
    return this.brandRepository.findAll();
  }

  @Override
  @Transactional(rollbackFor = Exception.class, propagation = Propagation.REQUIRED)
  public Brand create(
      String name,
      String description,
      String email,
      String stateLicense,
      String brandTypeName,
      String logoImageUrl,
      String instagramUrl,
      String twitterUrl,
      String facebookUrl,
      String websiteUrl,
      Integer adminId,
      Boolean enabled) {
    BrandType brandType =
        this.brandTypeRepository
            .findByName(brandTypeName)
            .orElseThrow(() -> new NotFoundException("Invalid brand type: " + brandTypeName));
    var brand = new Brand();
    brand.setName(name);
    brand.setDescription(description);
    brand.setEmail(email);
    brand.setStateLicense(stateLicense);
    brand.setBrandType(brandType);
    brand.setLogoImageUrl(logoImageUrl);
    brand.setInstagramUrl(instagramUrl);
    brand.setTwitterUrl(twitterUrl);
    brand.setFacebookUrl(facebookUrl);
    brand.setWebsiteUrl(websiteUrl);
    brand.setAdminId(adminId);
    brand.setEnabled(enabled);
    return this.brandRepository.save(brand);
  }

  @Override
  public Optional<Brand> getById(Long id) {
    return this.brandRepository.findById(id);
  }

  @Override
  @Transactional(rollbackFor = Exception.class, propagation = Propagation.REQUIRED)
  public Brand update(
      Long id,
      String name,
      String description,
      String email,
      String stateLicense,
      String brandTypeName,
      String logoImageUrl,
      String instagramUrl,
      String twitterUrl,
      String facebookUrl,
      String websiteUrl,
      Integer adminId,
      Boolean enabled)
      throws NotFoundException {
    Brand brand =
        this.brandRepository
            .findById(id)
            .orElseThrow(() -> new NotFoundException("Brand not found with ID: " + id));
    if (brandTypeName != null) {
      BrandType brandType =
          this.brandTypeRepository
              .findByName(brandTypeName)
              .orElseThrow(() -> new NotFoundException("Invalid brand type: " + brandTypeName));
      brand.setBrandType(brandType);
    }
    if (name != null) {
      brand.setName(name);
    }
    if (description != null) {
      brand.setDescription(description);
    }
    if (email != null) {
      brand.setEmail(email);
    }
    if (stateLicense != null) {
      brand.setStateLicense(stateLicense);
    }
    if (logoImageUrl != null) {
      brand.setLogoImageUrl(logoImageUrl);
    }
    if (instagramUrl != null) {
      brand.setInstagramUrl(instagramUrl);
    }
    if (twitterUrl != null) {
      brand.setTwitterUrl(twitterUrl);
    }
    if (facebookUrl != null) {
      brand.setFacebookUrl(facebookUrl);
    }
    if (websiteUrl != null) {
      brand.setWebsiteUrl(websiteUrl);
    }
    if (adminId != null) {
      brand.setAdminId(adminId);
    }
    if (enabled != null) {
      brand.setEnabled(enabled);
    }
    return this.brandRepository.save(brand);
  }
}
