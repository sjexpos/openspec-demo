package com.example.demo.application.services;

import static org.mockito.BDDMockito.given;

import java.util.List;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.ComponentScan;

import com.example.demo.domain.models.dispensary.Dispensary;

class DispensaryServiceTests extends ServiceTest {

    @TestConfiguration
    @ComponentScan
    static class TestConfig {
    }

    @Autowired
    private DispensaryService dispensaryService;


    @Test
    void testFindAll() {
        // Given
        Iterable<Dispensary> expectedDispensaries = List.of();
        given(dispensaryRepository.findAllByDeletedAtIsNull()).willReturn(expectedDispensaries);

        // When
        var result = dispensaryService.findAll();

        // Then
        Assertions.assertEquals(expectedDispensaries, result);
    }
    
}
