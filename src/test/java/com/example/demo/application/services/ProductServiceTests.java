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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;

import com.example.demo.application.exceptions.ConflictException;
import com.example.demo.application.exceptions.NotFoundException;
import com.example.demo.application.services.model.CreateProductCommand;
import com.example.demo.domain.models.brand.Brand;
import com.example.demo.domain.models.product.Category;
import com.example.demo.domain.models.product.Collection;
import com.example.demo.domain.models.product.Product;
import com.example.demo.domain.models.product.Subcategory;
import com.example.demo.domain.models.product.Unit;
import com.example.demo.domain.models.strain.Strain;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.ComponentScan;

class ProductServiceTests extends ServiceTest {

  @TestConfiguration
  @ComponentScan(lazyInit = true)
  static class TestConfig {}

  @Autowired private ProductService productService;

  private Collection collection;
  private Category category;
  private Subcategory subcategory;
  private Brand brand;
  private Strain strain;
  private Unit formatUnit;
  private Unit contentUnit;

  @BeforeEach
  void setUp() {
    collection = new Collection();
    collection.setId(10L);
    collection.setName("Flower");

    category = new Category();
    category.setId(20L);
    category.setName("Indoor");

    subcategory = new Subcategory();
    subcategory.setId(30L);
    subcategory.setName("Premium");
    subcategory.setCategory(category);

    brand = Brand.builder().id(40L).name("Green Leaf").build();

    strain = new Strain();
    strain.setId(50L);
    strain.setName("Blue Dream");

    formatUnit = new Unit();
    formatUnit.setId(60L);
    formatUnit.setName("gram");

    contentUnit = new Unit();
    contentUnit.setId(61L);
    contentUnit.setName("milliliter");
  }

  private CreateProductCommand.CreateProductCommandBuilder validCommandBuilder() {
    // Corrective (2026-09-10, third code-review Minor m3): formatValue/contentValue/cbd are three
    // same-typed Integer fields that previously all shared the value 1, and
    // isCoreProduct/approved/enabled previously were all Boolean.TRUE — both leaving field
    // transpositions among those groups invisible to should_persistProductOnce_when_commandIsValid
    // at the unit level (mvn test), even though ProductEndpointsTests caught some of them at the
    // integration level. Distinct Integer values close that gap directly. For the three booleans,
    // pigeonhole (3 fields, 2 possible values) means no single fixture can discriminate every pair;
    // this fixture (isCoreProduct=TRUE, approved=FALSE, enabled=TRUE) discriminates
    // isCoreProduct/approved and approved/enabled, and the second fixture in
    // should_notTransposeIsCoreProductAndEnabled_when_theirValuesDiffer below (isCoreProduct=FALSE,
    // approved=TRUE, enabled=TRUE) discriminates the remaining isCoreProduct/enabled pair — the
    // same two-fixture pattern already used in ProductControllerTests.
    return CreateProductCommand.builder()
        .ocpc("OCPC-001")
        .title("Blue Dream 1g")
        .description("A classic hybrid")
        .collectionId(collection.getId())
        .categoryId(category.getId())
        .subcategoryId(subcategory.getId())
        .brandId(brand.getId())
        .strainId(strain.getId())
        .formatValue(1)
        .formatUnitId(formatUnit.getId())
        .contentValue(2)
        .contentUnitId(contentUnit.getId())
        .isCoreProduct(Boolean.TRUE)
        .approved(Boolean.FALSE)
        .thc(20)
        .cbd(3)
        .enabled(Boolean.TRUE);
  }

  private void stubAllReferencesResolvable() {
    given(collectionRepository.findById(collection.getId())).willReturn(Optional.of(collection));
    given(categoryRepository.findById(category.getId())).willReturn(Optional.of(category));
    given(subcategoryRepository.findById(subcategory.getId())).willReturn(Optional.of(subcategory));
    given(brandRepository.findById(brand.getId())).willReturn(Optional.of(brand));
    given(strainRepository.findById(strain.getId())).willReturn(Optional.of(strain));
    given(unitRepository.findById(formatUnit.getId())).willReturn(Optional.of(formatUnit));
    given(unitRepository.findById(contentUnit.getId())).willReturn(Optional.of(contentUnit));
  }

  @Test
  @DisplayName("create persists the product once with every field mapped when the command is valid")
  void should_persistProductOnce_when_commandIsValid() {
    // Given
    CreateProductCommand command = validCommandBuilder().build();
    given(productRepository.existsByOcpc(command.ocpc())).willReturn(false);
    stubAllReferencesResolvable();
    given(productRepository.save(any(Product.class))).willAnswer(inv -> inv.getArgument(0));

    // When
    Product result = productService.create(command);

    // Then
    assertNotNull(result);
    assertEquals(command.ocpc(), result.getOcpc());
    assertEquals(command.title(), result.getTitle());
    assertEquals(command.description(), result.getDescription());
    assertEquals(collection, result.getCollection());
    assertEquals(category, result.getCategory());
    assertEquals(subcategory, result.getSubcategory());
    assertEquals(brand, result.getBrand());
    assertEquals(strain, result.getStrain());
    assertEquals(command.formatValue(), result.getFormatValue());
    assertEquals(formatUnit, result.getFormatUnit());
    assertEquals(command.contentValue(), result.getContentValue());
    assertEquals(contentUnit, result.getContentUnit());
    assertEquals(command.isCoreProduct(), result.getIsCoreProduct());
    assertEquals(command.approved(), result.getApproved());
    assertEquals(command.thc(), result.getThc());
    assertEquals(command.cbd(), result.getCbd());
    assertEquals(command.enabled(), result.getEnabled());
    then(productRepository).should(times(1)).save(result);
  }

  @Test
  @DisplayName(
      "create does not transpose isCoreProduct and enabled when their values differ (corrective"
          + " m3, pigeonhole second fixture)")
  void should_notTransposeIsCoreProductAndEnabled_when_theirValuesDiffer() {
    // Corrective (2026-09-10, third code-review Minor m3): validCommandBuilder() has
    // isCoreProduct=TRUE and enabled=TRUE (equal), so a transposition between exactly those two
    // fields in ProductServiceImpl.create is invisible to should_persistProductOnce_when_command
    // IsValid above. This second fixture sets isCoreProduct=FALSE != enabled=TRUE to make that
    // specific swap observable at the unit level, mirroring the pattern already used in
    // ProductControllerTests.
    CreateProductCommand command =
        validCommandBuilder().isCoreProduct(Boolean.FALSE).approved(Boolean.TRUE).build();
    given(productRepository.existsByOcpc(command.ocpc())).willReturn(false);
    stubAllReferencesResolvable();
    given(productRepository.save(any(Product.class))).willAnswer(inv -> inv.getArgument(0));

    // When
    Product result = productService.create(command);

    // Then
    assertEquals(Boolean.FALSE, result.getIsCoreProduct());
    assertEquals(Boolean.TRUE, result.getApproved());
    assertEquals(Boolean.TRUE, result.getEnabled());
  }

  @Test
  @DisplayName(
      "create throws ConflictException naming the OCPC and never saves when it is already used"
          + " by a live product")
  void should_throwConflict_when_ocpcAlreadyUsedByLiveProduct() {
    // Given
    CreateProductCommand command = validCommandBuilder().build();
    given(productRepository.existsByOcpc(command.ocpc())).willReturn(true);

    // When / Then
    Exception ex = assertThrows(ConflictException.class, () -> productService.create(command));

    assertEquals("Product already exists with OCPC: " + command.ocpc(), ex.getMessage());
    then(productRepository).should(never()).save(any(Product.class));
  }

  @Test
  @DisplayName(
      "create throws NotFoundException naming Collection and never saves when it does not exist")
  void should_throwNotFound_when_collectionDoesNotExist() {
    // Given
    CreateProductCommand command = validCommandBuilder().build();
    given(productRepository.existsByOcpc(command.ocpc())).willReturn(false);
    given(collectionRepository.findById(collection.getId())).willReturn(Optional.empty());

    // When / Then
    Exception ex = assertThrows(NotFoundException.class, () -> productService.create(command));

    assertEquals("Collection not found with ID: " + collection.getId(), ex.getMessage());
    then(productRepository).should(never()).save(any(Product.class));
  }

  @Test
  @DisplayName(
      "create throws NotFoundException naming Category and never saves when it does not exist")
  void should_throwNotFound_when_categoryDoesNotExist() {
    // Given
    CreateProductCommand command = validCommandBuilder().build();
    given(productRepository.existsByOcpc(command.ocpc())).willReturn(false);
    stubAllReferencesResolvable();
    given(categoryRepository.findById(category.getId())).willReturn(Optional.empty());

    // When / Then
    Exception ex = assertThrows(NotFoundException.class, () -> productService.create(command));

    assertEquals("Category not found with ID: " + category.getId(), ex.getMessage());
    then(productRepository).should(never()).save(any(Product.class));
  }

  @Test
  @DisplayName(
      "create throws NotFoundException naming Subcategory and never saves when it does not exist")
  void should_throwNotFound_when_subcategoryDoesNotExist() {
    // Given
    CreateProductCommand command = validCommandBuilder().build();
    given(productRepository.existsByOcpc(command.ocpc())).willReturn(false);
    stubAllReferencesResolvable();
    given(subcategoryRepository.findById(subcategory.getId())).willReturn(Optional.empty());

    // When / Then
    Exception ex = assertThrows(NotFoundException.class, () -> productService.create(command));

    assertEquals("Subcategory not found with ID: " + subcategory.getId(), ex.getMessage());
    then(productRepository).should(never()).save(any(Product.class));
  }

  @Test
  @DisplayName(
      "create throws NotFoundException naming Brand and never saves when it does not exist")
  void should_throwNotFound_when_brandDoesNotExist() {
    // Given
    CreateProductCommand command = validCommandBuilder().build();
    given(productRepository.existsByOcpc(command.ocpc())).willReturn(false);
    stubAllReferencesResolvable();
    given(brandRepository.findById(brand.getId())).willReturn(Optional.empty());

    // When / Then
    Exception ex = assertThrows(NotFoundException.class, () -> productService.create(command));

    assertEquals("Brand not found with ID: " + brand.getId(), ex.getMessage());
    then(productRepository).should(never()).save(any(Product.class));
  }

  @Test
  @DisplayName(
      "create throws NotFoundException naming Strain and never saves when it does not exist")
  void should_throwNotFound_when_strainDoesNotExist() {
    // Given
    CreateProductCommand command = validCommandBuilder().build();
    given(productRepository.existsByOcpc(command.ocpc())).willReturn(false);
    stubAllReferencesResolvable();
    given(strainRepository.findById(strain.getId())).willReturn(Optional.empty());

    // When / Then
    Exception ex = assertThrows(NotFoundException.class, () -> productService.create(command));

    assertEquals("Strain not found with ID: " + strain.getId(), ex.getMessage());
    then(productRepository).should(never()).save(any(Product.class));
  }

  @Test
  @DisplayName(
      "create throws NotFoundException naming FormatUnit and never saves when it does not exist")
  void should_throwNotFound_when_formatUnitDoesNotExist() {
    // Given
    CreateProductCommand command = validCommandBuilder().build();
    given(productRepository.existsByOcpc(command.ocpc())).willReturn(false);
    stubAllReferencesResolvable();
    given(unitRepository.findById(formatUnit.getId())).willReturn(Optional.empty());

    // When / Then
    Exception ex = assertThrows(NotFoundException.class, () -> productService.create(command));

    assertEquals("FormatUnit not found with ID: " + formatUnit.getId(), ex.getMessage());
    then(productRepository).should(never()).save(any(Product.class));
  }

  @Test
  @DisplayName(
      "create throws NotFoundException naming ContentUnit and never saves when it does not exist")
  void should_throwNotFound_when_contentUnitDoesNotExist() {
    // Given
    CreateProductCommand command = validCommandBuilder().build();
    given(productRepository.existsByOcpc(command.ocpc())).willReturn(false);
    stubAllReferencesResolvable();
    given(unitRepository.findById(contentUnit.getId())).willReturn(Optional.empty());

    // When / Then
    Exception ex = assertThrows(NotFoundException.class, () -> productService.create(command));

    assertEquals("ContentUnit not found with ID: " + contentUnit.getId(), ex.getMessage());
    then(productRepository).should(never()).save(any(Product.class));
  }

  @Test
  @DisplayName(
      "create throws ConflictException and never saves when the subcategory belongs to another"
          + " category")
  void should_throwConflict_when_subcategoryBelongsToAnotherCategory() {
    // Given
    Category otherCategory = new Category();
    otherCategory.setId(21L);
    otherCategory.setName("Outdoor");
    subcategory.setCategory(otherCategory);

    CreateProductCommand command = validCommandBuilder().build();
    given(productRepository.existsByOcpc(command.ocpc())).willReturn(false);
    stubAllReferencesResolvable();

    // When / Then
    Exception ex = assertThrows(ConflictException.class, () -> productService.create(command));

    assertEquals(
        "Subcategory " + subcategory.getId() + " does not belong to category " + category.getId(),
        ex.getMessage());
    then(productRepository).should(never()).save(any(Product.class));
  }

  @Test
  @DisplayName(
      "create saves the product once when the subcategory belongs to the supplied category")
  void should_continueCreation_when_subcategoryBelongsToSuppliedCategory() {
    // Given (setUp's subcategory already has the matching category as its parent)
    CreateProductCommand command = validCommandBuilder().build();
    given(productRepository.existsByOcpc(command.ocpc())).willReturn(false);
    stubAllReferencesResolvable();
    given(productRepository.save(any(Product.class))).willAnswer(inv -> inv.getArgument(0));

    // When
    productService.create(command);

    // Then
    then(productRepository).should(times(1)).save(any(Product.class));
  }

  @Test
  @DisplayName(
      "create persists description, isCoreProduct, approved, thc, cbd and enabled as null when"
          + " omitted")
  void should_persistNulls_when_optionalAttributesAreOmitted() {
    // Given
    CreateProductCommand command =
        validCommandBuilder()
            .description(null)
            .isCoreProduct(null)
            .approved(null)
            .thc(null)
            .cbd(null)
            .enabled(null)
            .build();
    given(productRepository.existsByOcpc(command.ocpc())).willReturn(false);
    stubAllReferencesResolvable();
    given(productRepository.save(any(Product.class))).willAnswer(inv -> inv.getArgument(0));

    // When
    Product result = productService.create(command);

    // Then
    assertNull(result.getDescription());
    assertNull(result.getIsCoreProduct());
    assertNull(result.getApproved());
    assertNull(result.getThc());
    assertNull(result.getCbd());
    assertNull(result.getEnabled());
  }

  @Test
  @DisplayName("create persists false flags verbatim when they are explicitly false")
  void should_persistFalse_when_flagsAreExplicitlyFalse() {
    // Given
    CreateProductCommand command =
        validCommandBuilder()
            .isCoreProduct(Boolean.FALSE)
            .approved(Boolean.FALSE)
            .enabled(Boolean.FALSE)
            .build();
    given(productRepository.existsByOcpc(command.ocpc())).willReturn(false);
    stubAllReferencesResolvable();
    given(productRepository.save(any(Product.class))).willAnswer(inv -> inv.getArgument(0));

    // When
    Product result = productService.create(command);

    // Then
    assertEquals(Boolean.FALSE, result.getIsCoreProduct());
    assertEquals(Boolean.FALSE, result.getApproved());
    assertEquals(Boolean.FALSE, result.getEnabled());
  }

  @Test
  @DisplayName(
      "create throws ConflictException when the command is both duplicated-OCPC and has an"
          + " unknown brandId, pinning the fixed guard order")
  void should_throwConflict_when_ocpcDuplicatedAndBrandUnknown() {
    // Given
    CreateProductCommand command = validCommandBuilder().build();
    given(productRepository.existsByOcpc(command.ocpc())).willReturn(true);
    given(brandRepository.findById(brand.getId())).willReturn(Optional.empty());

    // When / Then
    assertThrows(ConflictException.class, () -> productService.create(command));
    then(productRepository).should(never()).save(any(Product.class));
  }

  @Test
  @DisplayName(
      "create throws NotFoundException when the command is both incoherent-taxonomy and has an"
          + " unknown contentUnitId, pinning the fixed guard order")
  void should_throwNotFound_when_taxonomyIncoherentAndContentUnitUnknown() {
    // Given
    Category otherCategory = new Category();
    otherCategory.setId(21L);
    otherCategory.setName("Outdoor");
    subcategory.setCategory(otherCategory);

    CreateProductCommand command = validCommandBuilder().build();
    given(productRepository.existsByOcpc(command.ocpc())).willReturn(false);
    stubAllReferencesResolvable();
    given(unitRepository.findById(contentUnit.getId())).willReturn(Optional.empty());

    // When / Then
    assertThrows(NotFoundException.class, () -> productService.create(command));
    then(productRepository).should(never()).save(any(Product.class));
  }

  @Test
  @DisplayName("create propagates a RuntimeException raised by save, without swallowing it")
  void should_propagateException_when_saveFails() {
    // Given
    CreateProductCommand command = validCommandBuilder().build();
    given(productRepository.existsByOcpc(command.ocpc())).willReturn(false);
    stubAllReferencesResolvable();
    given(productRepository.save(any(Product.class)))
        .willThrow(new RuntimeException("database is down"));

    // When / Then
    Exception ex = assertThrows(RuntimeException.class, () -> productService.create(command));
    assertEquals("database is down", ex.getMessage());
  }
}
