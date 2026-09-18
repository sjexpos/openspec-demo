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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.demo.domain.models.strain.Strain;
import com.example.demo.domain.repositories.StrainRepository;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class StrainRepositoryTests extends RepositoryTest {

  @Autowired private StrainRepository strainRepository;

  private Strain createStrain(String ucpc, Integer strainTypeId, Integer seedCompanyId) {
    Strain strain = new Strain();
    strain.setUcpc(ucpc);
    strain.setName("Blue Dream");
    strain.setDescription("A balanced hybrid strain");
    strain.setStrainTypeId(strainTypeId);
    strain.setSeedCompanyId(seedCompanyId);
    strain.setCalmingEnergizingValue(5);
    return this.entityManager.persistAndFlush(strain);
  }

  @Test
  void should_returnItWithAllMappedColumns_when_strainExists() {
    // Given
    Integer strainTypeId = StrainReferenceDataFixtures.seedStrainType(this.entityManager);
    Integer seedCompanyId = StrainReferenceDataFixtures.seedSeedCompany(this.entityManager);
    Strain persisted = createStrain("UCPC-001", strainTypeId, seedCompanyId);

    // When
    Optional<Strain> result = strainRepository.findById(persisted.getId());

    // Then
    assertTrue(result.isPresent());
    Strain found = result.orElseThrow();
    assertEquals("UCPC-001", found.getUcpc());
    assertEquals("Blue Dream", found.getName());
    assertEquals("A balanced hybrid strain", found.getDescription());
    assertEquals(strainTypeId, found.getStrainTypeId());
    assertEquals(seedCompanyId, found.getSeedCompanyId());
    assertEquals(5, found.getCalmingEnergizingValue());
  }

  @Test
  void should_returnEmpty_when_strainIsSoftDeleted() {
    // Given
    Integer strainTypeId = StrainReferenceDataFixtures.seedStrainType(this.entityManager);
    Integer seedCompanyId = StrainReferenceDataFixtures.seedSeedCompany(this.entityManager);
    Strain strain = createStrain("UCPC-002", strainTypeId, seedCompanyId);
    strain.setDeletedAt(LocalDateTime.now());
    strain = this.entityManager.persistAndFlush(strain);

    // Simulate a clean session, exactly as BrandRepositoryTests documents.
    this.entityManager.clear();

    // When
    Optional<Strain> result = strainRepository.findById(strain.getId());

    // Then
    assertTrue(result.isEmpty());

    long physicalCount =
        ((Number)
                this.entityManager
                    .getEntityManager()
                    .createNativeQuery("SELECT count(*) FROM strains WHERE id = :id")
                    .setParameter("id", strain.getId())
                    .getSingleResult())
            .longValue();
    assertEquals(1, physicalCount);
  }
}
