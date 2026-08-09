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

package com.example.demo.integration.repositories;

import com.example.demo.domain.models.dispensary.Address;
import com.example.demo.domain.models.dispensary.Dispensary;
import com.example.demo.domain.models.dispensary.LicenseStatus;
import com.example.demo.domain.repositories.DispensaryRepository;
import java.math.BigDecimal;
import org.assertj.core.util.Lists;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class DispensaryRepositoryTests extends RepositoryTest {

  @Autowired private DispensaryRepository dispensaryRepository;

  private Dispensary createDispensaryWithPendingLicenseStatus() {
    LicenseStatus pendingLicenseStatus =
        this.entityManager
            .getEntityManager()
            .createQuery(
                "SELECT ls FROM LicenseStatus ls WHERE ls.state = :state", LicenseStatus.class)
            .setParameter("state", "PENDING")
            .getResultList()
            .stream()
            .findFirst()
            .orElseThrow(() -> new RuntimeException("LicenseStatus not found"));
    var addr =
        Address.builder()
            .address("123 Test Street")
            .zipCodeId(1)
            .longitude(BigDecimal.valueOf(-74.0060))
            .latitude(BigDecimal.valueOf(40.7128))
            .build();
    this.entityManager.persistAndFlush(addr);
    var dispensary =
        Dispensary.builder()
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

    // When — invoke repository delete to trigger @SQLDelete
    dispensaryRepository.delete(disp2);
    this.entityManager.flush();
    this.entityManager.clear();

    // Then — row must remain physically present (proving soft-delete, not hard-delete)
    long physicalCount =
        (Long)
            this.entityManager
                .getEntityManager()
                .createNativeQuery(
                    "SELECT COUNT(d) FROM dispensaries d WHERE d.id = :id", Long.class)
                .setParameter("id", disp2.getId())
                .getSingleResult();
    Assertions.assertEquals(1, physicalCount);

    // And — entity must be hidden from normal repository queries (proving @SQLRestriction filters
    // it out)
    var dispensaries = dispensaryRepository.findAll();
    Assertions.assertNotNull(dispensaries);
    Assertions.assertEquals(2, Lists.newArrayList(dispensaries).size());
    Assertions.assertTrue(
        Lists.newArrayList(dispensaries).stream().allMatch(d -> d.getDeletedAt() == null));
    Assertions.assertTrue(
        Lists.newArrayList(dispensaries).stream().anyMatch(d -> d.getId().equals(disp1.getId())));
    Assertions.assertTrue(
        Lists.newArrayList(dispensaries).stream().anyMatch(d -> d.getId().equals(disp3.getId())));
    Assertions.assertFalse(
        Lists.newArrayList(dispensaries).stream().anyMatch(d -> d.getId().equals(disp2.getId())));
  }
}
