package com.example.demo.application.services;

import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import com.example.demo.domain.repositories.AddressRepository;
import com.example.demo.domain.repositories.DispensaryRepository;
import com.example.demo.domain.repositories.LicenseStatusRepository;

@ExtendWith(SpringExtension.class)
class ServiceTest {

    @MockitoBean
    DispensaryRepository dispensaryRepository;
    @MockitoBean
    LicenseStatusRepository licenseStatusRepository;
    @MockitoBean
    AddressRepository addressRepository;

}
