package com.example.demo.domain.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.example.demo.domain.models.dispensary.Dispensary;

@Repository
public interface DispensaryRepository extends JpaRepository<Dispensary, Long> {

    Iterable<Dispensary> findAllByDeletedAtIsNull();

}
