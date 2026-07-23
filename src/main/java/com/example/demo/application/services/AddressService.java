package com.example.demo.application.services;

import com.example.demo.domain.models.dispensary.Address;

public interface AddressService {

    Iterable<Address> findAll();

}
