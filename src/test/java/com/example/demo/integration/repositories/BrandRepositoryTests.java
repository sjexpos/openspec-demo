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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.demo.domain.models.brand.Brand;
import com.example.demo.domain.models.brand.BrandType;
import com.example.demo.domain.repositories.BrandRepository;
import com.example.demo.domain.repositories.BrandTypeRepository;
import java.time.LocalDateTime;
import java.util.Optional;
import org.assertj.core.util.Lists;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class BrandRepositoryTests extends RepositoryTest {

  @Autowired private BrandRepository brandRepository;

  @Autowired private BrandTypeRepository brandTypeRepository;

  private BrandType createBrandType(String name) {
    BrandType brandType = new BrandType();
    brandType.setName(name);
    return this.entityManager.persistAndFlush(brandType);
  }

  private Brand createBrand(String name) {
    BrandType brandType = createBrandType("grower");
    return createBrand(name, brandType);
  }

  private Brand createBrand(String name, BrandType brandType) {
    Brand brand =
        Brand.builder()
            .name(name)
            .description("Test brand description")
            .email("brand-" + name + "@yopmail.com")
            .stateLicense("LIC-" + name)
            .brandType(brandType)
            .logoImageUrl("https://example.com/logo.png")
            .adminId(1)
            .enabled(Boolean.TRUE)
            .build();
    return this.entityManager.persistAndFlush(brand);
  }

  @Test
  void sanitizedBrands_whenDeleted_excluded() {
    // Given
    Brand deprecatedBrand = createBrand("deprecated");
    Brand currentBrand = createBrand("current");
    deprecatedBrand.setDeletedAt(LocalDateTime.now());
    this.entityManager.persistAndFlush(deprecatedBrand);

    // When
    Iterable<Brand> brands = brandRepository.findAll();

    // Then
    assertNotNull(brands);
    assertEquals(1, Lists.newArrayList(brands).size());
    assertTrue(brands.iterator().next().getDeletedAt() == null);
    assertTrue(
        Lists.newArrayList(brands).stream().anyMatch(b -> b.getId().equals(currentBrand.getId())));
    assertFalse(
        Lists.newArrayList(brands).stream()
            .anyMatch(b -> b.getId().equals(deprecatedBrand.getId())));
  }

  @Test
  void findByIdAndDeletedAtIsNull_when_live_returnsBrand() {
    // Given
    Brand brand = createBrand("live");

    // When
    Optional<Brand> result = brandRepository.findById(brand.getId());

    // Then
    assertTrue(result.isPresent());
    assertEquals(brand.getId(), result.orElseThrow().getId());
  }

  @Test
  void findByIdAndDeletedAtIsNull_when_softDeleted_returnsEmpty() {
    // Given
    Brand brand = createBrand("deleted");
    brand.setDeletedAt(LocalDateTime.now());
    brand = this.entityManager.persistAndFlush(brand);

    // Simulate a clean session. If not, hibernate holds on the "brand" in cache and retrieve it
    // from there instead of load from DB
    this.entityManager.clear();

    // When
    Optional<Brand> result = brandRepository.findById(brand.getId());

    // Then
    assertTrue(result.isEmpty());
  }

  @Test
  void findByName_when_match_returnsBrandType() {
    // Given
    BrandType brandType = createBrandType("craftedBreeder");

    // When
    Optional<BrandType> result = brandTypeRepository.findByName("craftedBreeder");

    // Then
    assertTrue(result.isPresent());
    assertTrue(brandType.getId().equals(result.orElseThrow().getId()));
  }

  @Test
  void findByName_when_unknown_returnsEmpty() {
    // Given

    // When / Then
    Optional<BrandType> result = brandTypeRepository.findByName("unknown");
    assertTrue(result.isEmpty());
  }

  @Test
  void softDeletedBrand_when_deleted_rowStillPresentPhysically() {
    // Given
    Brand brand = createBrand("persistent");

    // When — invoke repository delete to trigger @SQLDelete
    brandRepository.delete(brand);
    this.entityManager.flush();
    this.entityManager.clear();

    // Then — row must remain physically present (proving soft-delete, not hard-delete)
    long physicalCount =
        (Long)
            this.entityManager
                .getEntityManager()
                .createNativeQuery("SELECT COUNT(b) FROM brands b WHERE b.id = :id", Long.class)
                .setParameter("id", brand.getId())
                .getSingleResult();
    assertEquals(1, physicalCount);

    // And — entity must be hidden from normal repository queries (proving @SQLRestriction filters it out)
    Optional<Brand> result = brandRepository.findById(brand.getId());
    assertTrue(result.isEmpty());
  }
}
