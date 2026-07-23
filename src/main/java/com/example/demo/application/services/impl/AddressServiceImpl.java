package com.example.demo.application.services.impl;

import org.springframework.stereotype.Service;

import com.example.demo.application.services.AddressService;
import com.example.demo.domain.models.dispensary.Address;
import com.example.demo.domain.repositories.AddressRepository;

@Service
public class AddressServiceImpl implements AddressService {

    private AddressRepository repository;

    public AddressServiceImpl(AddressRepository repository) {
        this.repository = repository;
    }

    public Iterable<Address> findAll() {
        return this.repository.findAll();
    }

}
