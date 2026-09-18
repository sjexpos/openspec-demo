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
import com.example.demo.domain.models.product.Category;
import com.example.demo.domain.models.product.Collection;
import com.example.demo.domain.models.product.Product;
import com.example.demo.domain.models.product.Subcategory;
import com.example.demo.domain.models.product.Unit;
import com.example.demo.domain.models.strain.Strain;
import com.example.demo.domain.repositories.ProductRepository;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class ProductRepositoryTests extends RepositoryTest {

  @Autowired private ProductRepository productRepository;

  private Collection createCollection() {
    Collection collection = new Collection();
    collection.setName("Flowers");
    return this.entityManager.persistAndFlush(collection);
  }

  private Category createCategory() {
    Category category = new Category();
    category.setName("Edibles");
    category.setImageUrl("https://example.com/category.png");
    category.setTagIcon("icon");
    category.setTagColor("#000000");
    return this.entityManager.persistAndFlush(category);
  }

  private Subcategory createSubcategory(Category category) {
    Subcategory subcategory = new Subcategory();
    subcategory.setName("Gummies");
    subcategory.setCategory(category);
    subcategory.setImageUrl("https://example.com/subcategory.png");
    subcategory.setTagIcon("icon");
    subcategory.setTagColor("#111111");
    return this.entityManager.persistAndFlush(subcategory);
  }

  private Unit createUnit(String name) {
    Unit unit = new Unit();
    unit.setName(name);
    return this.entityManager.persistAndFlush(unit);
  }

  private Brand createBrand() {
    BrandType brandType = new BrandType();
    brandType.setName("grower");
    BrandType persistedBrandType = this.entityManager.persistAndFlush(brandType);

    Brand brand =
        Brand.builder()
            .name("Test Brand")
            .description("Test brand description")
            .email("brand@yopmail.com")
            .stateLicense("LIC-BRAND")
            .brandType(persistedBrandType)
            .logoImageUrl("https://example.com/logo.png")
            .adminId(1)
            .enabled(Boolean.TRUE)
            .build();
    return this.entityManager.persistAndFlush(brand);
  }

  private Strain createStrain() {
    Strain strain = new Strain();
    strain.setUcpc("UCPC-STRAIN-001");
    strain.setName("Blue Dream");
    strain.setDescription("A balanced hybrid strain");
    strain.setStrainTypeId(StrainReferenceDataFixtures.seedStrainType(this.entityManager));
    strain.setSeedCompanyId(StrainReferenceDataFixtures.seedSeedCompany(this.entityManager));
    strain.setCalmingEnergizingValue(5);
    return this.entityManager.persistAndFlush(strain);
  }

  // Holds the seven distinct reference ids used to build the last Product created by
  // createProduct(...), so callers can assert each FK getter against its own expected id rather
  // than merely asserting non-null (code-review Major #1 — a wrong or transposed FK mapping must
  // fail this assertion).
  private record ReferenceIds(
      Long collectionId,
      Long categoryId,
      Long subcategoryId,
      Long brandId,
      Long strainId,
      Long formatUnitId,
      Long contentUnitId) {}

  private ReferenceIds lastCreatedReferenceIds;

  private Product createProduct(String ocpc) {
    Category category = createCategory();
    Collection collection = createCollection();
    Subcategory subcategory = createSubcategory(category);
    Brand brand = createBrand();
    Strain strain = createStrain();
    Unit formatUnit = createUnit("gram");
    Unit contentUnit = createUnit("milligram");

    this.lastCreatedReferenceIds =
        new ReferenceIds(
            collection.getId(),
            category.getId(),
            subcategory.getId(),
            brand.getId(),
            strain.getId(),
            formatUnit.getId(),
            contentUnit.getId());

    Product product =
        Product.builder()
            .ocpc(ocpc)
            .title("Sour Diesel Gummies")
            .description("A tasty gummy")
            .collection(collection)
            .category(category)
            .subcategory(subcategory)
            .brand(brand)
            .strain(strain)
            .formatValue(10)
            .formatUnit(formatUnit)
            .contentValue(100)
            .contentUnit(contentUnit)
            .isCoreProduct(Boolean.TRUE)
            .approved(Boolean.TRUE)
            .thc(15)
            .cbd(5)
            .enabled(Boolean.TRUE)
            .build();
    return this.entityManager.persistAndFlush(product);
  }

  @Test
  void should_returnAllMappedColumnsWithCorrectReferenceIds_when_productExists() {
    // Given
    Product persisted = createProduct("OCPC-001");
    ReferenceIds expected = this.lastCreatedReferenceIds;

    // When
    Optional<Product> result = productRepository.findById(persisted.getId());

    // Then
    assertTrue(result.isPresent());
    Product found = result.orElseThrow();
    assertEquals("OCPC-001", found.getOcpc());
    assertEquals("Sour Diesel Gummies", found.getTitle());
    assertEquals("A tasty gummy", found.getDescription());
    assertEquals(10, found.getFormatValue());
    assertEquals(100, found.getContentValue());
    assertEquals(Boolean.TRUE, found.getIsCoreProduct());
    assertEquals(Boolean.TRUE, found.getApproved());
    assertEquals(15, found.getThc());
    assertEquals(5, found.getCbd());
    assertEquals(Boolean.TRUE, found.getEnabled());
    // Corrective (2026-09-10, code-review Major #1): assertEquals against each FK's own distinct
    // seeded id, not merely assertNotNull, so a silent transposition of two FK mappings fails.
    assertEquals(expected.collectionId(), found.getCollection().getId());
    assertEquals(expected.categoryId(), found.getCategory().getId());
    assertEquals(expected.subcategoryId(), found.getSubcategory().getId());
    assertEquals(expected.brandId(), found.getBrand().getId());
    assertEquals(expected.strainId(), found.getStrain().getId());
    assertEquals(expected.formatUnitId(), found.getFormatUnit().getId());
    assertEquals(expected.contentUnitId(), found.getContentUnit().getId());
    assertNotNull(found.getCreatedAt());
  }

  @Test
  void should_returnTrue_when_ocpcIsUsedByLiveProduct() {
    // Given
    createProduct("OCPC-EXISTS-001");

    // When / Then
    assertTrue(productRepository.existsByOcpc("OCPC-EXISTS-001"));
  }

  @Test
  void should_returnFalse_when_ocpcIsUnknown() {
    // Given / When / Then
    assertFalse(productRepository.existsByOcpc("OCPC-UNKNOWN"));
  }

  @Test
  void should_returnFalse_when_onlyOcpcHolderIsSoftDeleted() {
    // Given
    Product product = createProduct("OCPC-SOFT-DELETED-001");
    product.setDeletedAt(LocalDateTime.now());
    this.entityManager.persistAndFlush(product);
    this.entityManager.clear();

    // When / Then
    assertFalse(productRepository.existsByOcpc("OCPC-SOFT-DELETED-001"));
  }

  @Test
  void should_excludeSoftDeletedProduct_when_findById() {
    // Given
    Product product = createProduct("OCPC-FIND-EXCLUDED-001");
    product.setDeletedAt(LocalDateTime.now());
    this.entityManager.persistAndFlush(product);
    this.entityManager.clear();

    // When
    Optional<Product> result = productRepository.findById(product.getId());

    // Then
    assertTrue(result.isEmpty());
  }

  @Test
  void should_keepRowPhysicallyPresent_when_deleteIsCalled() {
    // Given
    Product product = createProduct("OCPC-DELETE-001");

    // When — invoke repository delete to trigger @SQLDelete
    productRepository.delete(product);
    this.entityManager.flush();
    this.entityManager.clear();

    // Then — row must remain physically present (soft delete, not hard delete)
    long physicalCount =
        ((Number)
                this.entityManager
                    .getEntityManager()
                    .createNativeQuery("SELECT count(*) FROM products WHERE id = :id")
                    .setParameter("id", product.getId())
                    .getSingleResult())
            .longValue();
    assertEquals(1, physicalCount);

    // And — entity must be hidden from normal repository queries
    Optional<Product> result = productRepository.findById(product.getId());
    assertTrue(result.isEmpty());
  }
}
