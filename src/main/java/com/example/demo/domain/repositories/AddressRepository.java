package com.example.demo.domain.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.example.demo.domain.models.dispensary.Address;

@Repository
public interface AddressRepository extends JpaRepository<Address, Long> {

}
