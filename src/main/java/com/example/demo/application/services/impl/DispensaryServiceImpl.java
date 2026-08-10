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
import com.example.demo.application.services.DispensaryService;
import com.example.demo.domain.models.dispensary.Address;
import com.example.demo.domain.models.dispensary.Dispensary;
import com.example.demo.domain.models.dispensary.LicenseStatus;
import com.example.demo.domain.repositories.AddressRepository;
import com.example.demo.domain.repositories.DispensaryRepository;
import com.example.demo.domain.repositories.LicenseStatusRepository;
import java.math.BigDecimal;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DispensaryServiceImpl implements DispensaryService {

  private DispensaryRepository dispensaryRepository;
  private LicenseStatusRepository licenseStatusRepository;
  private AddressRepository addressRepository;

  public DispensaryServiceImpl(
      DispensaryRepository dispensaryRepository,
      LicenseStatusRepository licenseStatusRepository,
      AddressRepository addressRepository) {
    this.dispensaryRepository = dispensaryRepository;
    this.licenseStatusRepository = licenseStatusRepository;
    this.addressRepository = addressRepository;
  }

  public Iterable<Dispensary> findAll() {
    return this.dispensaryRepository.findAll();
  }

  @Override
  @Transactional(rollbackFor = Exception.class, propagation = Propagation.REQUIRED)
  public Dispensary create(
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
      Boolean enabled) {
    LicenseStatus licenseStatusEntity =
        this.licenseStatusRepository
            .findByState(licenseStatus)
            .orElseThrow(() -> new NotFoundException("Invalid license status: " + licenseStatus));
    var addressEntity = new Address();
    addressEntity.setAddress(address);
    addressEntity.setZipCodeId(0);
    addressEntity.setLongitude(BigDecimal.ZERO);
    addressEntity.setLatitude(BigDecimal.ZERO);
    this.addressRepository.save(addressEntity);
    var dispensary = new Dispensary();
    dispensary.setLicenseStatus(licenseStatusEntity);
    dispensary.setName(name);
    dispensary.setLogoImageUrl(logoImageURL);
    dispensary.setDescription(description);
    dispensary.setLicense(license);
    dispensary.setPhone(phone);
    dispensary.setEmail(email);
    dispensary.setInstagramUrl(instagramURL);
    dispensary.setTwitterUrl(twitterURL);
    dispensary.setFacebookUrl(facebookURL);
    dispensary.setWebsiteUrl(websiteURL);
    dispensary.setAddress(addressEntity);
    dispensary.setCommission(commission);
    dispensary.setAdminId(adminId);
    dispensary.setEnabled(enabled);
    return this.dispensaryRepository.save(dispensary);
  }

  @Override
  public Optional<Dispensary> getById(Long id) {
    return this.dispensaryRepository
        .findById(id)
        .filter(dispensary -> dispensary.getDeletedAt() == null);
  }

  @Override
  @Transactional(rollbackFor = Exception.class, propagation = Propagation.REQUIRED)
  public Optional<Dispensary> deleteById(Long id) throws NotFoundException {
    var dispensary =
        this.dispensaryRepository
            .findById(id)
            .filter(d -> d.getDeletedAt() == null)
            .orElseThrow(() -> new NotFoundException("Dispensary not found"));
    dispensary.setDeletedAt(java.time.LocalDateTime.now());
    dispensary = this.dispensaryRepository.save(dispensary);
    return Optional.of(dispensary);
  }
}
