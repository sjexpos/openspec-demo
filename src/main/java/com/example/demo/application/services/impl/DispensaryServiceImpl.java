package com.example.demo.application.services.impl;

import java.math.BigDecimal;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.example.demo.application.exceptions.NotFoundException;
import com.example.demo.application.services.DispensaryService;
import com.example.demo.domain.models.dispensary.Address;
import com.example.demo.domain.models.dispensary.Dispensary;
import com.example.demo.domain.models.dispensary.LicenseStatus;
import com.example.demo.domain.repositories.AddressRepository;
import com.example.demo.domain.repositories.DispensaryRepository;
import com.example.demo.domain.repositories.LicenseStatusRepository;

@Service
public class DispensaryServiceImpl implements DispensaryService {

    private DispensaryRepository dispensaryRepository;
    private LicenseStatusRepository licenseStatusRepository;
    private AddressRepository addressRepository;

    public DispensaryServiceImpl(DispensaryRepository dispensaryRepository, LicenseStatusRepository licenseStatusRepository, AddressRepository addressRepository) {
        this.dispensaryRepository = dispensaryRepository;
        this.licenseStatusRepository = licenseStatusRepository;
        this.addressRepository = addressRepository;
    }

    public Iterable<Dispensary> findAll() {
        return this.dispensaryRepository.findAllByDeletedAtIsNull();
    }

    @Override
    @Transactional(rollbackFor = Exception.class, propagation = Propagation.REQUIRED)
    public Dispensary create(String name, String logoImageURL, String description, String license, String licenseStatus,
            String phone, String email, String instagramURL, String twitterURL, String facebookURL, String websiteURL,
            String address, BigDecimal commission, Integer adminId, Boolean enabled) {
        LicenseStatus licenseStatusEntity = this.licenseStatusRepository.findByState(licenseStatus)
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
        return this.dispensaryRepository.findById(id)
            .filter(dispensary -> dispensary.getDeletedAt() == null);
    }

    @Override
    @Transactional(rollbackFor = Exception.class, propagation = Propagation.REQUIRED)
    public Optional<Dispensary> deleteById(Long id) throws NotFoundException {
        var dispensary = this.dispensaryRepository.findById(id)
            .filter(d -> d.getDeletedAt() == null)
            .orElseThrow(() -> new NotFoundException("Dispensary not found"));
        dispensary.setDeletedAt(java.time.LocalDateTime.now());
        dispensary = this.dispensaryRepository.save(dispensary);
        return Optional.of(dispensary);
    }

}
