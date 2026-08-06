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

package com.example.demo.application.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;

import com.example.demo.application.exceptions.NotFoundException;
import com.example.demo.domain.models.brand.Brand;
import com.example.demo.domain.models.brand.BrandType;
import java.util.List;
import java.util.Optional;
import org.assertj.core.util.Lists;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.ComponentScan;

class BrandServiceTests extends ServiceTest {

  @TestConfiguration
  @ComponentScan(lazyInit = true)
  static class TestConfig {}

  @Autowired private BrandService brandService;

  private BrandType resolvedBrandType;

  @BeforeEach
  void setUp() {
    resolvedBrandType = new BrandType();
    resolvedBrandType.setId(1L);
    resolvedBrandType.setName("grower");
  }

  private Brand liveBrand(Long id, String name) {
    Brand brand =
        Brand.builder()
            .id(id)
            .name(name)
            .description("Description")
            .email(name + "@yopmail.com")
            .stateLicense("LIC")
            .brandType(resolvedBrandType)
            .logoImageUrl("https://example.com/logo.png")
            .adminId(1)
            .enabled(Boolean.TRUE)
            .build();
    return brand;
  }

  @Test
  @DisplayName("findAll returns only non-deleted brands")
  void findAll_shouldReturn_nonDeletedBrandsOnly() {
    // Given
    Brand deleted = liveBrand(1L, "deleted");
    Brand live = liveBrand(2L, "live");
    given(brandRepository.findAllByDeletedAtIsNull()).willReturn(List.of(live));

    // When
    Iterable<Brand> result = brandService.findAll();

    // Then
    assertNotNull(result);
    assertEquals(List.of(live), result);
    assertFalse(Lists.newArrayList(result).contains(deleted));
  }

  @Test
  @DisplayName("create saves brand with resolved BrandType")
  void create_shouldSaveBrand_withResolvedType() {
    // Given
    given(brandTypeRepository.findByName("grower")).willReturn(Optional.of(resolvedBrandType));
    given(brandRepository.save(any(Brand.class))).willAnswer(inv -> inv.getArgument(0));

    // When
    Brand result =
        brandService.create(
            "Green Leaf",
            "Description",
            "brand@yopmail.com",
            "CAL-01",
            "grower",
            "https://example.com/logo.png",
            "https://instagram.com/greenleaf",
            null,
            null,
            "https://website.com/greenleaf",
            7,
            Boolean.TRUE);

    // Then
    assertNotNull(result);
    assertEquals(resolvedBrandType, result.getBrandType());
    then(brandRepository).should(times(1)).save(result);
  }

  @Test
  @DisplayName("create with unknown brand type throws NotFoundException and never saves")
  void create_shouldThrow_whenUnknownBrandType_andDoNotSave() {
    // Given
    given(brandTypeRepository.findByName("unknown")).willReturn(Optional.empty());

    // When / Then
    Exception ex =
        assertThrows(
            NotFoundException.class,
            () ->
                brandService.create(
                    "Name",
                    "Description",
                    "brand@yopmail.com",
                    "CAL-1",
                    "unknown",
                    "https://example.com/logo.png",
                    null,
                    null,
                    null,
                    null,
                    1,
                    Boolean.TRUE));

    assertEquals("Invalid brand type: unknown", ex.getMessage());
    then(brandRepository).should(never()).save(any(Brand.class));
  }

  @Test
  @DisplayName("getById returns brand for a live id")
  void getById_shouldReturnBrand_when_live() {
    // Given
    Brand brand = liveBrand(5L, "brand");
    given(brandRepository.findByIdAndDeletedAtIsNull(5L)).willReturn(Optional.of(brand));

    // When
    Optional<Brand> result = brandService.getById(5L);

    // Then
    assertTrue(result.isPresent());
    assertEquals(brand, result.orElseThrow());
  }

  @Test
  @DisplayName("getById returns empty for soft-deleted or non-existent id")
  void getById_shouldReturnEmpty_when_softDeletedOrNonExistent() {
    // Given
    given(brandRepository.findByIdAndDeletedAtIsNull(99L)).willReturn(Optional.empty());

    // When
    Optional<Brand> result = brandService.getById(99L);

    // Then
    assertTrue(result.isEmpty());
  }

  @Test
  @DisplayName("update with all fields provided applies every field and saves once")
  void update_shouldApplyAllFields_when_allProvided() {
    // Given
    Brand brand = liveBrand(1L, "original");
    given(brandTypeRepository.findByName("processor")).willReturn(Optional.of(resolvedBrandType));
    given(brandRepository.findByIdAndDeletedAtIsNull(1L)).willReturn(Optional.of(brand));
    given(brandRepository.save(any(Brand.class))).willAnswer(inv -> inv.getArgument(0));

    // When
    Brand result =
        brandService.update(
            1L,
            "new-name",
            "new-description",
            "new@yopmail.com",
            "CA-2",
            "processor",
            "https://example.com/new-logo.png",
            "https://instagram.com/new",
            "https://twitter.com/new",
            "https://facebook.com/new",
            "https://website.com/new",
            9,
            Boolean.TRUE);

    // Then
    assertEquals("new-name", result.getName());
    assertEquals("new-description", result.getDescription());
    assertEquals("new@yopmail.com", result.getEmail());
    assertEquals("CA-2", result.getStateLicense());
    assertEquals(resolvedBrandType, result.getBrandType());
    assertEquals("https://example.com/new-logo.png", result.getLogoImageUrl());
    assertEquals("https://instagram.com/new", result.getInstagramUrl());
    assertEquals("https://twitter.com/new", result.getTwitterUrl());
    assertEquals("https://facebook.com/new", result.getFacebookUrl());
    assertEquals("https://website.com/new", result.getWebsiteUrl());
    assertEquals(9, result.getAdminId());
    assertEquals(Boolean.TRUE, result.getEnabled());
    then(brandRepository).should(times(1)).save(any(Brand.class));
  }

  @Test
  @DisplayName("update with a partial set applies only provided fields and saves once")
  void update_shouldApplyPartial_andSaveOnce() {
    // Given
    Brand brand = liveBrand(1L, "original");
    given(brandRepository.findByIdAndDeletedAtIsNull(1L)).willReturn(Optional.of(brand));
    given(brandRepository.save(any(Brand.class))).willAnswer(inv -> inv.getArgument(0));

    // When
    Brand result =
        brandService.update(
            1L,
            "updated-name",
            null,
            "updated@yopmail.com",
            null,
            null,
            null,
            null,
            null,
            null,
            "https://website.com/website",
            null,
            null);

    // Then
    assertEquals("updated-name", result.getName());
    assertEquals("updated@yopmail.com", result.getEmail());
    assertEquals("https://website.com/website", result.getWebsiteUrl());
    assertEquals("Description", result.getDescription());
    assertEquals(resolvedBrandType, result.getBrandType());
    then(brandRepository).should(times(1)).save(any(Brand.class));
  }

  @Test
  @DisplayName("update with a non-existent id throws NotFoundException and does not save")
  void update_shouldThrow_when_nonexistent_andDoNotSave() {
    // Given
    given(brandRepository.findByIdAndDeletedAtIsNull(77L)).willReturn(Optional.empty());

    // When / Then
    assertThrows(
        NotFoundException.class,
        () ->
            brandService.update(
                77L, "name", null, null, null, null, null, null, null, null, null, null, null));

    then(brandRepository).should(never()).save(any(Brand.class));
  }

  @Test
  @DisplayName("update on a soft-deleted brand throws NotFoundException")
  void update_shouldThrow_when_softDeleted() {
    // Given
    given(brandRepository.findByIdAndDeletedAtIsNull(2L)).willReturn(Optional.empty());

    // When / Then
    assertThrows(
        NotFoundException.class,
        () ->
            brandService.update(
                2L, "name", null, null, null, null, null, null, null, null, null, null, null));

    then(brandRepository).should(never()).save(any(Brand.class));
  }

  @Test
  @DisplayName("update with unknown brandTypeName throws NotFoundException and does not save")
  void update_shouldThrow_whenUnknownBrandType_andDoNotSave() {
    // Given / When
    Brand brand = liveBrand(1L, "original");
    given(brandTypeRepository.findByName("invalid")).willReturn(Optional.empty());
    given(brandRepository.findByIdAndDeletedAtIsNull(1L)).willReturn(Optional.of(brand));

    // Then
    assertThrows(
        NotFoundException.class,
        () ->
            brandService.update(
                1L, "name", null, null, null, "invalid", null, null, null, null, null, null, null));

    then(brandRepository).should(never()).save(any(Brand.class));
  }

  @Test
  @DisplayName("update omitting enabled leaves the existing enabled value unchanged")
  void update_shouldLeaveEnabled_when_omitted() {
    // Given
    Brand brand = liveBrand(1L, "brand");
    given(brandRepository.findByIdAndDeletedAtIsNull(1L)).willReturn(Optional.of(brand));
    given(brandRepository.save(any(Brand.class))).willAnswer(inv -> inv.getArgument(0));

    // When
    Brand result =
        brandService.update(
            1L, "name", null, null, null, null, null, null, null, null, null, null, null);

    // Then
    assertEquals(Boolean.TRUE, result.getEnabled());
  }

  @Test
  @DisplayName("update with enabled=false sets enabled to false")
  void update_shouldApplyFalse_when_enabledFalse() {
    // Given
    Brand brand = liveBrand(1L, "brand");
    given(brandRepository.findByIdAndDeletedAtIsNull(1L)).willReturn(Optional.of(brand));
    given(brandRepository.save(any(Brand.class))).willAnswer(inv -> inv.getArgument(0));

    // When
    Brand result =
        brandService.update(
            1L, null, null, null, null, null, null, null, null, null, null, null, Boolean.FALSE);

    // Then
    assertEquals(Boolean.FALSE, result.getEnabled());
  }
}
