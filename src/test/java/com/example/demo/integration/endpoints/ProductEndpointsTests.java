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

package com.example.demo.integration.endpoints;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.demo.domain.models.brand.Brand;
import com.example.demo.domain.models.brand.BrandType;
import com.example.demo.domain.models.product.Category;
import com.example.demo.domain.models.product.Collection;
import com.example.demo.domain.models.product.Subcategory;
import com.example.demo.domain.models.product.Unit;
import com.example.demo.domain.models.strain.Strain;
import com.example.demo.domain.repositories.BrandRepository;
import com.example.demo.domain.repositories.BrandTypeRepository;
import com.example.demo.domain.repositories.CategoryRepository;
import com.example.demo.domain.repositories.CollectionRepository;
import com.example.demo.domain.repositories.StrainRepository;
import com.example.demo.domain.repositories.SubcategoryRepository;
import com.example.demo.domain.repositories.UnitRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.EntityTransaction;
import java.time.LocalDateTime;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Endpoint-level integration tests for {@code POST /api/products} against the real local
 * PostgreSQL database (not Testcontainers), mirroring {@code BrandControllerEndpointsTests}.
 * Every test seeds only what it needs on top of the shared {@link #setUp()} fixtures and the
 * suite is verified order-independent via the {@link #tearDown()} native-SQL cleanup, exactly
 * like the sibling {@code Brand}/{@code Strain}/{@code Product} repository test classes.
 */
class ProductEndpointsTests extends EndpointIntegrationTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;
  @Autowired private CollectionRepository collectionRepository;
  @Autowired private CategoryRepository categoryRepository;
  @Autowired private SubcategoryRepository subcategoryRepository;
  @Autowired private UnitRepository unitRepository;
  @Autowired private BrandTypeRepository brandTypeRepository;
  @Autowired private BrandRepository brandRepository;
  @Autowired private StrainRepository strainRepository;
  @Autowired private EntityManagerFactory entityManagerFactory;

  private Long collectionId;
  private Long categoryId;
  private Long otherCategoryId;
  private Long subcategoryId;
  private Long otherSubcategoryId;
  private Long formatUnitId;
  private Long contentUnitId;
  private Long liveBrandId;
  private Long softDeletedBrandId;
  private Long liveStrainId;
  private Long softDeletedStrainId;

  @BeforeEach
  void setUp() {
    Collection collection = new Collection();
    collection.setName("Flowers");
    collectionId = collectionRepository.save(collection).getId();

    categoryId = seedCategory("Edibles").getId();
    otherCategoryId = seedCategory("Concentrates").getId();
    subcategoryId = seedSubcategory("Gummies", categoryId).getId();
    otherSubcategoryId = seedSubcategory("Wax", otherCategoryId).getId();

    Unit formatUnit = new Unit();
    formatUnit.setName("gram");
    formatUnitId = unitRepository.save(formatUnit).getId();

    Unit contentUnit = new Unit();
    contentUnit.setName("milligram");
    contentUnitId = unitRepository.save(contentUnit).getId();

    BrandType brandType = new BrandType();
    brandType.setName("grower");
    BrandType savedBrandType = brandTypeRepository.save(brandType);

    liveBrandId = seedBrand("live-brand", savedBrandType).getId();
    Brand softDeletedBrand = seedBrand("deleted-brand", savedBrandType);
    softDeletedBrandId = softDeletedBrand.getId();
    softDelete(softDeletedBrand);

    Integer strainTypeId =
        seedNative("INSERT INTO strain_types (name) VALUES ('Sativa') RETURNING id");
    Integer seedCompanyId =
        seedNative("INSERT INTO seed_companies (name) VALUES ('Acme Seeds') RETURNING id");

    liveStrainId = seedStrain("UCPC-LIVE-001", strainTypeId, seedCompanyId).getId();
    Strain softDeletedStrain = seedStrain("UCPC-DELETED-001", strainTypeId, seedCompanyId);
    softDeletedStrainId = softDeletedStrain.getId();
    softDeleteStrain(softDeletedStrain);
  }

  @AfterEach
  void tearDown() {
    try (EntityManager entityManager = entityManagerFactory.createEntityManager()) {
      EntityTransaction tx = entityManager.getTransaction();
      try {
        tx.begin();
        entityManager.createNativeQuery("DELETE FROM products").executeUpdate();
        entityManager.createNativeQuery("DELETE FROM strains").executeUpdate();
        entityManager.createNativeQuery("DELETE FROM seed_companies").executeUpdate();
        entityManager.createNativeQuery("DELETE FROM strain_types").executeUpdate();
        entityManager.createNativeQuery("DELETE FROM brands").executeUpdate();
        entityManager.createNativeQuery("DELETE FROM subcategories").executeUpdate();
        entityManager.createNativeQuery("DELETE FROM categories").executeUpdate();
        entityManager.createNativeQuery("DELETE FROM collections").executeUpdate();
        entityManager.createNativeQuery("DELETE FROM units").executeUpdate();
        tx.commit();
      } catch (Exception e) {
        if (tx.isActive()) {
          tx.rollback();
        }
        throw e;
      }
    }
    brandTypeRepository.deleteAll();
  }

  private Category seedCategory(String name) {
    Category category = new Category();
    category.setName(name);
    category.setImageUrl("https://example.com/category.png");
    category.setTagIcon("icon");
    category.setTagColor("#000000");
    return categoryRepository.save(category);
  }

  private Subcategory seedSubcategory(String name, Long categoryId) {
    Subcategory subcategory = new Subcategory();
    subcategory.setName(name);
    subcategory.setCategory(categoryRepository.findById(categoryId).orElseThrow());
    subcategory.setImageUrl("https://example.com/subcategory.png");
    subcategory.setTagIcon("icon");
    subcategory.setTagColor("#111111");
    return subcategoryRepository.save(subcategory);
  }

  private Brand seedBrand(String name, BrandType brandType) {
    Brand brand =
        Brand.builder()
            .name(name)
            .description("description")
            .email(name + "@yopmail.com")
            .stateLicense("LIC-" + name)
            .brandType(brandType)
            .logoImageUrl("https://example.com/logo.png")
            .adminId(1)
            .enabled(Boolean.TRUE)
            .build();
    return brandRepository.save(brand);
  }

  private void softDelete(Brand brand) {
    brand.setDeletedAt(LocalDateTime.now());
    brandRepository.save(brand);
  }

  private Strain seedStrain(String ucpc, Integer strainTypeId, Integer seedCompanyId) {
    Strain strain = new Strain();
    strain.setUcpc(ucpc);
    strain.setName("Blue Dream");
    strain.setDescription("A balanced hybrid strain");
    strain.setStrainTypeId(strainTypeId);
    strain.setSeedCompanyId(seedCompanyId);
    strain.setCalmingEnergizingValue(5);
    return strainRepository.save(strain);
  }

  private void softDeleteStrain(Strain strain) {
    strain.setDeletedAt(LocalDateTime.now());
    strainRepository.save(strain);
  }

  private Integer seedNative(String sql) {
    try (EntityManager entityManager = entityManagerFactory.createEntityManager()) {
      EntityTransaction tx = entityManager.getTransaction();
      tx.begin();
      Number result = (Number) entityManager.createNativeQuery(sql).getSingleResult();
      tx.commit();
      return result.intValue();
    }
  }

  private long countProducts() {
    try (EntityManager entityManager = entityManagerFactory.createEntityManager()) {
      return ((Number)
              entityManager.createNativeQuery("SELECT count(*) FROM products").getSingleResult())
          .longValue();
    }
  }

  private ObjectNode validBody(String ocpc) {
    ObjectNode node = objectMapper.createObjectNode();
    node.put("ocpc", ocpc);
    node.put("title", "Sour Diesel Gummies");
    node.put("description", "A tasty gummy");
    node.put("collectionId", collectionId);
    node.put("categoryId", categoryId);
    node.put("subcategoryId", subcategoryId);
    node.put("brandId", liveBrandId);
    node.put("strainId", liveStrainId);
    node.put("formatValue", 10);
    node.put("formatUnitId", formatUnitId);
    node.put("contentValue", 100);
    node.put("contentUnitId", contentUnitId);
    node.put("isCoreProduct", true);
    node.put("approved", true);
    node.put("thc", 15);
    node.put("cbd", 5);
    node.put("enabled", true);
    return node;
  }

  @Test
  @DisplayName("POST valid product returns 201 and persists exactly one row")
  void should_return201AndPersistProduct_when_requestIsValid() throws Exception {
    long before = countProducts();

    mockMvc
        .perform(
            post("/api/products")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(validBody("OCPC-VALID-001"))))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.data.id").isNumber())
        .andExpect(jsonPath("$.data.ocpc").value("OCPC-VALID-001"));

    assertEquals(before + 1, countProducts());

    try (EntityManager entityManager = entityManagerFactory.createEntityManager()) {
      // Corrective (2026-09-10, code-review Major #1, then extended by the second code-review
      // Major finding): assert every FK column AND every non-reference field against its own
      // distinct seeded/submitted value, not just ocpc/title/created_at, so a wrong or transposed
      // field mapping anywhere in ProductServiceImpl/ProductController would fail this end-to-end
      // assertion.
      Object[] row =
          (Object[])
              entityManager
                  .createNativeQuery(
                      "SELECT ocpc, title, created_at, collection_id, category_id,"
                          + " subcategory_id, brand_id, strain_id, format_unit_id, content_unit_id,"
                          + " description, format_value, content_value, thc, cbd"
                          + " FROM products WHERE ocpc = :ocpc")
                  .setParameter("ocpc", "OCPC-VALID-001")
                  .getSingleResult();
      assertEquals("OCPC-VALID-001", row[0]);
      assertEquals("Sour Diesel Gummies", row[1]);
      assertNotNull(row[2]);
      assertEquals(collectionId.longValue(), ((Number) row[3]).longValue());
      assertEquals(categoryId.longValue(), ((Number) row[4]).longValue());
      assertEquals(subcategoryId.longValue(), ((Number) row[5]).longValue());
      assertEquals(liveBrandId.longValue(), ((Number) row[6]).longValue());
      assertEquals(liveStrainId.longValue(), ((Number) row[7]).longValue());
      assertEquals(formatUnitId.longValue(), ((Number) row[8]).longValue());
      assertEquals(contentUnitId.longValue(), ((Number) row[9]).longValue());
      assertEquals("A tasty gummy", row[10]);
      assertEquals(10, ((Number) row[11]).intValue());
      assertEquals(100, ((Number) row[12]).intValue());
      assertEquals(15, ((Number) row[13]).intValue());
      assertEquals(5, ((Number) row[14]).intValue());
    }
  }

  @Test
  @DisplayName("POST duplicated OCPC returns 409 and does not create a second row")
  void should_return409_when_ocpcIsAlreadyUsed() throws Exception {
    mockMvc
        .perform(
            post("/api/products")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(validBody("OCPC-DUP-001"))))
        .andExpect(status().isCreated());

    long before = countProducts();

    mockMvc
        .perform(
            post("/api/products")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(validBody("OCPC-DUP-001"))))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.status").value(409))
        .andExpect(jsonPath("$.errors[0].field").value("general"));

    assertEquals(before, countProducts());
  }

  @Test
  @DisplayName("POST reusing OCPC of a soft-deleted product returns 201")
  void should_return201_when_ocpcHolderIsSoftDeleted() throws Exception {
    mockMvc
        .perform(
            post("/api/products")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(validBody("OCPC-REUSE-001"))))
        .andExpect(status().isCreated());

    try (EntityManager entityManager = entityManagerFactory.createEntityManager()) {
      EntityTransaction tx = entityManager.getTransaction();
      tx.begin();
      entityManager
          .createNativeQuery("UPDATE products SET deleted_at = now() WHERE ocpc = :ocpc")
          .setParameter("ocpc", "OCPC-REUSE-001")
          .executeUpdate();
      tx.commit();
    }

    mockMvc
        .perform(
            post("/api/products")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(validBody("OCPC-REUSE-001"))))
        .andExpect(status().isCreated());
  }

  @Test
  @DisplayName("POST unknown collectionId returns 404 and creates no row")
  void should_return404_when_collectionIsUnknown() throws Exception {
    long before = countProducts();
    ObjectNode body = validBody("OCPC-404-COLLECTION");
    body.put("collectionId", 999999L);

    mockMvc
        .perform(
            post("/api/products")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.errors[0].message").value("Collection not found with ID: 999999"));

    assertEquals(before, countProducts());
  }

  @Test
  @DisplayName("POST unknown categoryId returns 404 and creates no row")
  void should_return404_when_categoryIsUnknown() throws Exception {
    long before = countProducts();
    ObjectNode body = validBody("OCPC-404-CATEGORY");
    body.put("categoryId", 999999L);

    mockMvc
        .perform(
            post("/api/products")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.errors[0].message").value("Category not found with ID: 999999"));

    assertEquals(before, countProducts());
  }

  @Test
  @DisplayName("POST unknown subcategoryId returns 404 and creates no row")
  void should_return404_when_subcategoryIsUnknown() throws Exception {
    long before = countProducts();
    ObjectNode body = validBody("OCPC-404-SUBCATEGORY");
    body.put("subcategoryId", 999999L);

    mockMvc
        .perform(
            post("/api/products")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.errors[0].message").value("Subcategory not found with ID: 999999"));

    assertEquals(before, countProducts());
  }

  @Test
  @DisplayName("POST unknown formatUnitId returns 404 and creates no row")
  void should_return404_when_formatUnitIsUnknown() throws Exception {
    long before = countProducts();
    ObjectNode body = validBody("OCPC-404-FORMATUNIT");
    body.put("formatUnitId", 999999L);

    mockMvc
        .perform(
            post("/api/products")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.errors[0].message").value("FormatUnit not found with ID: 999999"));

    assertEquals(before, countProducts());
  }

  @Test
  @DisplayName("POST unknown contentUnitId returns 404 and creates no row")
  void should_return404_when_contentUnitIsUnknown() throws Exception {
    long before = countProducts();
    ObjectNode body = validBody("OCPC-404-CONTENTUNIT");
    body.put("contentUnitId", 999999L);

    mockMvc
        .perform(
            post("/api/products")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.errors[0].message").value("ContentUnit not found with ID: 999999"));

    assertEquals(before, countProducts());
  }

  @Test
  @DisplayName("POST unknown brandId returns 404 and creates no row")
  void should_return404_when_brandIsUnknown() throws Exception {
    long before = countProducts();
    ObjectNode body = validBody("OCPC-404-BRAND");
    body.put("brandId", 999999L);

    mockMvc
        .perform(
            post("/api/products")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.errors[0].message").value("Brand not found with ID: 999999"));

    assertEquals(before, countProducts());
  }

  @Test
  @DisplayName("POST unknown strainId returns 404 and creates no row")
  void should_return404_when_strainIsUnknown() throws Exception {
    long before = countProducts();
    ObjectNode body = validBody("OCPC-404-STRAIN");
    body.put("strainId", 999999L);

    mockMvc
        .perform(
            post("/api/products")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.errors[0].message").value("Strain not found with ID: 999999"));

    assertEquals(before, countProducts());
  }

  @Test
  @DisplayName("POST soft-deleted brand returns 404 and creates no row")
  void should_return404_when_brandIsSoftDeleted() throws Exception {
    long before = countProducts();
    ObjectNode body = validBody("OCPC-404-BRAND-DELETED");
    body.put("brandId", softDeletedBrandId);

    mockMvc
        .perform(
            post("/api/products")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)))
        .andExpect(status().isNotFound());

    assertEquals(before, countProducts());
  }

  @Test
  @DisplayName("POST soft-deleted strain returns 404 and creates no row")
  void should_return404_when_strainIsSoftDeleted() throws Exception {
    long before = countProducts();
    ObjectNode body = validBody("OCPC-404-STRAIN-DELETED");
    body.put("strainId", softDeletedStrainId);

    mockMvc
        .perform(
            post("/api/products")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)))
        .andExpect(status().isNotFound());

    assertEquals(before, countProducts());
  }

  @Test
  @DisplayName("POST subcategory belonging to another category returns 409 and creates no row")
  void should_return409_when_subcategoryBelongsToAnotherCategory() throws Exception {
    long before = countProducts();
    ObjectNode body = validBody("OCPC-409-TAXONOMY");
    body.put("subcategoryId", otherSubcategoryId);

    mockMvc
        .perform(
            post("/api/products")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.status").value(409));

    assertEquals(before, countProducts());
  }

  @Test
  @DisplayName("POST empty body returns 400 with the static D9 message and creates no row")
  void should_return400_when_bodyIsEmpty() throws Exception {
    long before = countProducts();

    mockMvc
        .perform(post("/api/products").contentType(MediaType.APPLICATION_JSON).content(""))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].field").value("general"))
        .andExpect(jsonPath("$.errors[0].message").value("Malformed or missing request body"));

    assertEquals(before, countProducts());
  }

  @Test
  @DisplayName("POST blank required field returns 400 and creates no row")
  void should_return400_when_requiredFieldIsBlank() throws Exception {
    long before = countProducts();
    ObjectNode body = validBody("OCPC-400-BLANK-TITLE");
    body.put("title", "   ");

    mockMvc
        .perform(
            post("/api/products")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].field").value("title"));

    assertEquals(before, countProducts());
  }

  @Test
  @DisplayName("POST omitted optional attributes persists NULL, not defaults")
  void should_persistNullOptionalAttributes_when_theyAreOmitted() throws Exception {
    ObjectNode body = validBody("OCPC-NULLS-001");
    body.remove("enabled");
    body.remove("approved");
    body.remove("isCoreProduct");
    body.remove("thc");
    body.remove("cbd");

    mockMvc
        .perform(
            post("/api/products")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)))
        .andExpect(status().isCreated());

    try (EntityManager entityManager = entityManagerFactory.createEntityManager()) {
      Object[] row =
          (Object[])
              entityManager
                  .createNativeQuery(
                      "SELECT enabled, approved, is_core_product, thc, cbd FROM products WHERE"
                          + " ocpc = :ocpc")
                  .setParameter("ocpc", "OCPC-NULLS-001")
                  .getSingleResult();
      for (Object value : row) {
        assertEquals(null, value);
      }
    }
  }

  @Test
  @DisplayName("POST with explicit flags persists them verbatim")
  void should_persistSuppliedFlags_when_theyAreExplicit() throws Exception {
    // Corrective (2026-09-10, second code-review Major finding): use a distinct combination
    // (true/false/true), not all-false, so a transposition among the three boolean fields in
    // ProductController.create's request->command mapping would fail this assertion.
    ObjectNode body = validBody("OCPC-FLAGS-001");
    body.put("isCoreProduct", true);
    body.put("approved", false);
    body.put("enabled", true);

    mockMvc
        .perform(
            post("/api/products")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)))
        .andExpect(status().isCreated());

    try (EntityManager entityManager = entityManagerFactory.createEntityManager()) {
      Object[] row =
          (Object[])
              entityManager
                  .createNativeQuery(
                      "SELECT enabled, approved, is_core_product FROM products WHERE ocpc ="
                          + " :ocpc")
                  .setParameter("ocpc", "OCPC-FLAGS-001")
                  .getSingleResult();
      assertEquals(Boolean.TRUE, row[0]);
      assertEquals(Boolean.FALSE, row[1]);
      assertEquals(Boolean.TRUE, row[2]);
    }
  }

  @Test
  @DisplayName("POST with isCoreProduct differing from enabled persists them without transposition")
  void should_persistIsCoreProductAndEnabled_when_theirValuesDiffer() throws Exception {
    // Corrective (2026-09-10, pigeonhole gap found by adversarial mutation testing): the fixture
    // above (isCoreProduct=true, approved=false, enabled=true) has isCoreProduct==enabled (both
    // true) — with only 3 same-typed boolean fields and 2 possible values, the pigeonhole
    // principle guarantees at least one pair shares a value, so a transposition between exactly
    // isCoreProduct and enabled is invisible to that fixture alone. This scenario deliberately
    // sets isCoreProduct=false != enabled=true (with approved=true, distinct from the primary
    // fixture's approved=false, purely for readability) so that specific swap becomes observable
    // end-to-end through the real database.
    ObjectNode body = validBody("OCPC-FLAGS-002");
    body.put("isCoreProduct", false);
    body.put("approved", true);
    body.put("enabled", true);

    mockMvc
        .perform(
            post("/api/products")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)))
        .andExpect(status().isCreated());

    try (EntityManager entityManager = entityManagerFactory.createEntityManager()) {
      Object[] row =
          (Object[])
              entityManager
                  .createNativeQuery(
                      "SELECT enabled, approved, is_core_product FROM products WHERE ocpc ="
                          + " :ocpc")
                  .setParameter("ocpc", "OCPC-FLAGS-002")
                  .getSingleResult();
      assertEquals(Boolean.TRUE, row[0]);
      assertEquals(Boolean.TRUE, row[1]);
      assertEquals(Boolean.FALSE, row[2]);
    }
  }

  @Test
  @DisplayName("Rejected requests never change the products row count (atomicity, D6)")
  void should_leaveProductCountUnchanged_when_requestIsRejected() throws Exception {
    // 400 empty body
    long before1 = countProducts();
    mockMvc
        .perform(post("/api/products").contentType(MediaType.APPLICATION_JSON).content(""))
        .andExpect(status().isBadRequest());
    assertEquals(before1, countProducts());

    // 400 blank field
    long before2 = countProducts();
    ObjectNode blankTitle = validBody("OCPC-ATOMIC-BLANK");
    blankTitle.put("title", "   ");
    mockMvc
        .perform(
            post("/api/products")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(blankTitle)))
        .andExpect(status().isBadRequest());
    assertEquals(before2, countProducts());

    // 404 unresolved reference
    long before3 = countProducts();
    ObjectNode unknownBrand = validBody("OCPC-ATOMIC-404");
    unknownBrand.put("brandId", 999999L);
    mockMvc
        .perform(
            post("/api/products")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(unknownBrand)))
        .andExpect(status().isNotFound());
    assertEquals(before3, countProducts());

    // 409 duplicate OCPC
    mockMvc
        .perform(
            post("/api/products")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(validBody("OCPC-ATOMIC-DUP"))))
        .andExpect(status().isCreated());
    long before4 = countProducts();
    mockMvc
        .perform(
            post("/api/products")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(validBody("OCPC-ATOMIC-DUP"))))
        .andExpect(status().isConflict());
    assertEquals(before4, countProducts());

    // 409 incoherent taxonomy
    long before5 = countProducts();
    ObjectNode incoherent = validBody("OCPC-ATOMIC-TAXONOMY");
    incoherent.put("subcategoryId", otherSubcategoryId);
    mockMvc
        .perform(
            post("/api/products")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(incoherent)))
        .andExpect(status().isConflict());
    assertEquals(before5, countProducts());
  }

  @Test
  @DisplayName("Not-found and conflict error responses are descriptive and leak no internal detail")
  void should_reportDescriptiveMessageWithoutInternalDetails_when_requestFails() throws Exception {
    ObjectNode unknownBrand = validBody("OCPC-LEAK-CHECK");
    unknownBrand.put("brandId", 999999L);

    String responseBody =
        mockMvc
            .perform(
                post("/api/products")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(unknownBrand)))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.timestamp").exists())
            .andExpect(jsonPath("$.status").value(404))
            .andExpect(jsonPath("$.path").value("/api/products"))
            .andExpect(jsonPath("$.errors").isArray())
            .andReturn()
            .getResponse()
            .getContentAsString();

    assertFalse(responseBody.toLowerCase().contains("insert into"));
    assertFalse(responseBody.contains("fk_products_"));
    assertFalse(responseBody.contains("Exception"));
  }
}
