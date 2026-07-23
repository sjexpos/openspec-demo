package com.example.demo.integration.repositories;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import org.assertj.core.util.Lists;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.example.demo.domain.models.dispensary.Address;
import com.example.demo.domain.models.dispensary.Dispensary;
import com.example.demo.domain.models.dispensary.LicenseStatus;
import com.example.demo.domain.repositories.DispensaryRepository;

class DispensaryRepositoryTests extends RepositoryTest {

    @Autowired
    private DispensaryRepository dispensaryRepository;

    private Dispensary createDispensaryWithPendingLicenseStatus() {
        LicenseStatus pendingLicenseStatus = this.entityManager.getEntityManager()
                .createQuery(
                    "SELECT ls FROM LicenseStatus ls WHERE ls.state = :state",
                    LicenseStatus.class)
                .setParameter("state", "PENDING")
                .getResultList().stream().findFirst().orElseThrow(() -> new RuntimeException("LicenseStatus not found"));
        var addr = Address.builder()
                .address("123 Test Street")
                .zipCodeId(1)
                .longitude(BigDecimal.valueOf(-74.0060))
                .latitude(BigDecimal.valueOf(40.7128))
                .build();
        this.entityManager.persistAndFlush(addr);
        var dispensary = Dispensary.builder()
                .name("Test Dispensary1")
                .phone("123-456-7890")
                .email("test@yopmail.com")
                .address(addr)
                .logoImageUrl("")
                .enabled(Boolean.TRUE)
                .licenseStatus(pendingLicenseStatus)
                .license("LICENSE123")
                .commission(BigDecimal.ZERO)
                .adminId(1)
                .build();
        return this.entityManager.persistAndFlush(dispensary);
    }

    @Test
    void testFindAllByDeletedAtIsNull() {
        // Given
        var disp1 = createDispensaryWithPendingLicenseStatus();
        var disp2 = createDispensaryWithPendingLicenseStatus();
        var disp3 = createDispensaryWithPendingLicenseStatus();

        // When
        disp2.setDeletedAt(LocalDateTime.now());
        this.entityManager.persistAndFlush(disp2);
        var dispensaries = dispensaryRepository.findAllByDeletedAtIsNull();

        // Then
        Assertions.assertNotNull(dispensaries);
        Assertions.assertEquals(2, Lists.newArrayList(dispensaries).size());
        Assertions.assertTrue(Lists.newArrayList(dispensaries).stream().allMatch(d -> d.getDeletedAt() == null));
        Assertions.assertTrue(Lists.newArrayList(dispensaries).stream().anyMatch(d -> d.getId().equals(disp1.getId())));
        Assertions.assertTrue(Lists.newArrayList(dispensaries).stream().anyMatch(d -> d.getId().equals(disp3.getId())));
        Assertions.assertFalse(Lists.newArrayList(dispensaries).stream().anyMatch(d -> d.getId().equals(disp2.getId())));
    }

}
