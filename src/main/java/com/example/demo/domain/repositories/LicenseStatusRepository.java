package com.example.demo.domain.repositories;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.example.demo.domain.models.dispensary.LicenseStatus;

@Repository
public interface LicenseStatusRepository extends JpaRepository<LicenseStatus, Long>{

    Optional<LicenseStatus> findByState(String state);

}
