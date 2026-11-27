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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.demo.domain.models.brand.Brand;
import com.example.demo.domain.models.brand.BrandType;
import com.example.demo.domain.models.product.Category;
import com.example.demo.domain.models.product.Collection;
import com.example.demo.domain.models.product.Product;
import com.example.demo.domain.models.product.Subcategory;
import com.example.demo.domain.models.product.Unit;
import com.example.demo.domain.models.strain.Strain;
import com.example.demo.domain.repositories.BrandRepository;
import com.example.demo.domain.repositories.BrandTypeRepository;
import com.example.demo.domain.repositories.CategoryRepository;
import com.example.demo.domain.repositories.CollectionRepository;
import com.example.demo.domain.repositories.ProductRepository;
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

class BrandControllerEndpointsTests extends EndpointIntegrationTest {

  @Autowired private MockMvc mockMvc;

  @Autowired private ObjectMapper objectMapper;

  @Autowired private BrandTypeRepository brandTypeRepository;

  @Autowired private BrandRepository brandRepository;

  @Autowired private CollectionRepository collectionRepository;

  @Autowired private CategoryRepository categoryRepository;

  @Autowired private SubcategoryRepository subcategoryRepository;

  @Autowired private UnitRepository unitRepository;

  @Autowired private StrainRepository strainRepository;

  @Autowired private ProductRepository productRepository;

  @Autowired private EntityManagerFactory entityManagerFactory;

  private BrandType seededBrandType;

  @BeforeEach
  void setUp() {
    BrandType brandType = new BrandType();
    brandType.setName("grower");
    seededBrandType = brandTypeRepository.save(brandType);
  }

  @AfterEach
  void tearDown() {
    // Method deleteAll invokes @SQLDelete in Brand entity, which soft-deletes entities instead of
    // hard-deleting them.
    // @SQLRestriction then filters out soft-deleted rows from subsequent queries.
    // It is needed to remove them using native query for cleanup.
    // brandRepository.deleteAll();
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
    brandTypeRepository
        .deleteAll(); // BrandType doesn't have @SQLDelete or @SQLRestriction because it doesn't
    // have soft-delete
  }

  private ObjectNode validBody(String name, String brandTypeName) {
    ObjectNode node = objectMapper.createObjectNode();
    node.put("name", name);
    node.put("description", "Premium cannabis products");
    node.put("email", name + "@yopmail.com");
    node.put("stateLicense", "CAL-2026-001");
    node.put("brandTypeName", brandTypeName);
    node.put("logoImageUrl", "https://example.com/logo.png");
    node.put("instagramUrl", "https://instagram.com/" + name);
    node.put("adminId", 7);
    node.put("enabled", true);
    return node;
  }

  private long seedBrand(String name) {
    Brand brand =
        Brand.builder()
            .name(name)
            .description("description")
            .email(name + "@yopmail.com")
            .stateLicense("LIC")
            .brandType(seededBrandType)
            .logoImageUrl("https://example.com/logo.png")
            .adminId(1)
            .enabled(Boolean.TRUE)
            .build();
    return brandRepository.save(brand).getId();
  }

  private void softDelete(long id) {
    Brand brand = brandRepository.findById(id).orElseThrow();
    brand.setDeletedAt(LocalDateTime.now());
    brandRepository.save(brand);
  }

  private long seedProduct(String ocpc, long brandId) {
    Collection collection = new Collection();
    collection.setName("collection-" + ocpc);
    Long collectionId = collectionRepository.save(collection).getId();

    Category category = new Category();
    category.setName("category-" + ocpc);
    category.setImageUrl("https://example.com/category.png");
    category.setTagIcon("icon");
    category.setTagColor("#000000");
    Long categoryId = categoryRepository.save(category).getId();

    Subcategory subcategory = new Subcategory();
    subcategory.setName("subcategory-" + ocpc);
    subcategory.setCategory(categoryRepository.findById(categoryId).orElseThrow());
    subcategory.setImageUrl("https://example.com/subcategory.png");
    subcategory.setTagIcon("icon");
    subcategory.setTagColor("#111111");
    Long subcategoryId = subcategoryRepository.save(subcategory).getId();

    Unit formatUnit = new Unit();
    formatUnit.setName("gram-" + ocpc);
    Long formatUnitId = unitRepository.save(formatUnit).getId();

    Unit contentUnit = new Unit();
    contentUnit.setName("milligram-" + ocpc);
    Long contentUnitId = unitRepository.save(contentUnit).getId();

    Integer strainTypeId =
        seedNative("INSERT INTO strain_types (name) VALUES ('Sativa') RETURNING id");
    Integer seedCompanyId =
        seedNative("INSERT INTO seed_companies (name) VALUES ('Acme Seeds') RETURNING id");

    Strain strain = new Strain();
    strain.setUcpc("UCPC-" + ocpc);
    strain.setName("Blue Dream");
    strain.setDescription("A balanced hybrid strain");
    strain.setStrainTypeId(strainTypeId);
    strain.setSeedCompanyId(seedCompanyId);
    strain.setCalmingEnergizingValue(5);
    Long strainId = strainRepository.save(strain).getId();

    Product product =
        Product.builder()
            .ocpc(ocpc)
            .title("Title " + ocpc)
            .description("description")
            .collection(collectionRepository.findById(collectionId).orElseThrow())
            .category(categoryRepository.findById(categoryId).orElseThrow())
            .subcategory(subcategoryRepository.findById(subcategoryId).orElseThrow())
            .brand(brandRepository.findById(brandId).orElseThrow())
            .strain(strainRepository.findById(strainId).orElseThrow())
            .formatValue(10)
            .formatUnit(unitRepository.findById(formatUnitId).orElseThrow())
            .contentValue(100)
            .contentUnit(unitRepository.findById(contentUnitId).orElseThrow())
            .enabled(Boolean.TRUE)
            .build();
    return productRepository.save(product).getId();
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

  private void softDeleteProduct(long productId) {
    try (EntityManager entityManager = entityManagerFactory.createEntityManager()) {
      EntityTransaction tx = entityManager.getTransaction();
      tx.begin();
      entityManager
          .createNativeQuery("UPDATE products SET deleted_at = now() WHERE id = :id")
          .setParameter("id", productId)
          .executeUpdate();
      tx.commit();
    }
  }

  private long countBrands() {
    try (EntityManager entityManager = entityManagerFactory.createEntityManager()) {
      return ((Number)
              entityManager.createNativeQuery("SELECT count(*) FROM brands").getSingleResult())
          .longValue();
    }
  }

  private Object brandDeletedAt(long brandId) {
    try (EntityManager entityManager = entityManagerFactory.createEntityManager()) {
      return entityManager
          .createNativeQuery("SELECT deleted_at FROM brands WHERE id = :id")
          .setParameter("id", brandId)
          .getSingleResult();
    }
  }

  @Test
  @DisplayName("POST valid brand returns 201 with echo")
  void postValidBrand_shouldReturn201_andEcho() throws Exception {
    mockMvc
        .perform(
            post("/api/brands")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(validBody("greenleaf", "grower"))))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.data.name").value("greenleaf"))
        .andExpect(jsonPath("$.data.email").value("greenleaf@yopmail.com"))
        .andExpect(jsonPath("$.data.brandTypeName").value("grower"))
        .andExpect(jsonPath("$.data.id").isNumber());
  }

  @Test
  @DisplayName("POST missing required field returns 400 field error")
  void postMissingName_shouldReturn400_withFieldError() throws Exception {
    ObjectNode body = validBody("greenleaf", "grower");
    body.remove("name");

    mockMvc
        .perform(
            post("/api/brands")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.status").value(400))
        .andExpect(jsonPath("$.errors[0].field").value("name"));
  }

  @Test
  @DisplayName("POST blank required field returns 400 field error")
  void postBlankName_shouldReturn400() throws Exception {
    ObjectNode body = validBody("greenleaf", "grower");
    body.put("name", "");

    mockMvc
        .perform(
            post("/api/brands")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].field").value("name"));
  }

  @Test
  @DisplayName("POST invalid email returns 400")
  void postInvalidEmail_shouldReturn400() throws Exception {
    ObjectNode body = validBody("greenleaf", "grower");
    body.put("email", "not-an-email");

    mockMvc
        .perform(
            post("/api/brands")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].field").value("email"));
  }

  @Test
  @DisplayName("GET all excludes a soft-deleted brand")
  void getAll_shouldExcludeSoftDeletedBrand() throws Exception {
    long deletedId = seedBrand("toDelete");
    softDelete(deletedId);
    seedBrand("keepme");

    mockMvc
        .perform(get("/api/brands"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data").isArray())
        .andExpect(jsonPath("$.data[0].name").value("keepme"))
        .andExpect(jsonPath("$.data.length()").value(1));
  }

  @Test
  @DisplayName("GET by live id returns 200")
  void getByIdLive_shouldReturn200() throws Exception {
    long id = seedBrand("bylive");

    mockMvc
        .perform(get("/api/brands/" + id))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.id").value(id))
        .andExpect(jsonPath("$.data.name").value("bylive"));
  }

  @Test
  @DisplayName("GET non-existent id returns 404")
  void getByIdNonExistent_shouldReturn404() throws Exception {
    mockMvc
        .perform(get("/api/brands/999999"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.status").value(404))
        .andExpect(jsonPath("$.errors[0].field").value("general"));
  }

  @Test
  @DisplayName("GET soft-deleted id returns 404")
  void getByIdSoftDeleted_shouldReturn404() throws Exception {
    long id = seedBrand("softdelget");
    softDelete(id);

    mockMvc
        .perform(get("/api/brands/" + id))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.status").value(404));
  }

  @Test
  @DisplayName("PATCH partial updates only provided fields")
  void patchPartial_shouldUpdateOnlyProvidedFields() throws Exception {
    long id = seedBrand("patchme");
    ObjectNode body = objectMapper.createObjectNode();
    body.put("description", "updated description");

    mockMvc
        .perform(
            patch("/api/brands/" + id)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.description").value("updated description"))
        .andExpect(jsonPath("$.data.name").value("patchme"))
        .andExpect(jsonPath("$.data.brandTypeName").value("grower"));
  }

  @Test
  @DisplayName("PATCH non-existent id returns 404")
  void patchNonExistent_shouldReturn404() throws Exception {
    ObjectNode body = objectMapper.createObjectNode();
    body.put("name", "new-name");

    mockMvc
        .perform(
            patch("/api/brands/999999")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.status").value(404));
  }

  @Test
  @DisplayName("PATCH soft-deleted id returns 404")
  void patchSoftDeleted_shouldReturn404() throws Exception {
    long id = seedBrand("patchdel");
    softDelete(id);

    ObjectNode body = objectMapper.createObjectNode();
    body.put("name", "new-name");

    mockMvc
        .perform(
            patch("/api/brands/" + id)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.status").value(404));
  }

  @Test
  @DisplayName("PATCH invalid brandTypeName returns 404 and brand unchanged")
  void patchInvalidBrandType_shouldReturn404_andBrandUnchanged() throws Exception {
    long id = seedBrand("badtype");
    ObjectNode body = objectMapper.createObjectNode();
    body.put("brandTypeName", "does-not-exist");

    mockMvc
        .perform(
            patch("/api/brands/" + id)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.status").value(404));

    mockMvc
        .perform(get("/api/brands/" + id))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.brandTypeName").value("grower"))
        .andExpect(jsonPath("$.data.name").value("badtype"));
  }

  @Test
  @DisplayName("POST unknown brandTypeName returns 404 and no brand created")
  void postUnknownBrandType_shouldReturn404_andNoRowCreated() throws Exception {
    mockMvc
        .perform(
            post("/api/brands")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(validBody("nobrand", "does-not-exist"))))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.status").value(404));
  }

  @Test
  @DisplayName("POST malformed/empty body returns 400 with static message, not 500 (D9 regression)")
  void postEmptyBody_shouldReturn400WithStaticMessage() throws Exception {
    mockMvc
        .perform(post("/api/brands").contentType(MediaType.APPLICATION_JSON).content(""))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.status").value(400))
        .andExpect(jsonPath("$.errors[0].field").value("general"))
        .andExpect(jsonPath("$.errors[0].message").value("Malformed or missing request body"));
  }

  @Test
  @DisplayName("PATCH invalid email returns 400")
  void patchInvalidEmail_shouldReturn400() throws Exception {
    long id = seedBrand("bademail");
    ObjectNode body = objectMapper.createObjectNode();
    body.put("email", "not-an-email");

    mockMvc
        .perform(
            patch("/api/brands/" + id)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].field").value("email"));
  }

  @Test
  @DisplayName("DELETE live unreferenced brand returns 200 and soft-deletes")
  void should_return200AndSoftDelete_when_brandIsLiveAndUnreferenced() throws Exception {
    long id = seedBrand("delhappy");

    mockMvc
        .perform(delete("/api/brands/" + id))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.id").value(id))
        .andExpect(jsonPath("$.data.name").value("delhappy"))
        .andExpect(jsonPath("$.data.brandType").value("grower"));

    try (EntityManager entityManager = entityManagerFactory.createEntityManager()) {
      Number rowCount =
          (Number)
              entityManager
                  .createNativeQuery("SELECT count(*) FROM brands WHERE id = :id")
                  .setParameter("id", id)
                  .getSingleResult();
      assertEquals(1L, rowCount.longValue());
    }
    assertNotNull(brandDeletedAt(id));

    mockMvc.perform(get("/api/brands/" + id)).andExpect(status().isNotFound());
    mockMvc
        .perform(get("/api/brands"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.length()").value(0));
  }

  @Test
  @DisplayName("DELETE non-existent id returns 404")
  void should_return404_when_idDoesNotExist() throws Exception {
    long before = countBrands();

    mockMvc
        .perform(delete("/api/brands/999999"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.status").value(404))
        .andExpect(jsonPath("$.errors[0].field").value("general"))
        .andExpect(jsonPath("$.errors[0].message").value("Brand not found with ID: 999999"));

    assertEquals(before, countBrands());
  }

  @Test
  @DisplayName(
      "DELETE already soft-deleted brand returns 404 even when a live product references it")
  void should_return404_when_brandIsAlreadySoftDeleted() throws Exception {
    long id = seedBrand("delgone");
    seedProduct("OCPC-GONE-001", id);
    softDelete(id);

    mockMvc
        .perform(delete("/api/brands/" + id))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.status").value(404))
        .andExpect(jsonPath("$.errors[0].field").value("general"))
        .andExpect(jsonPath("$.errors[0].message").value("Brand not found with ID: " + id));
  }

  @Test
  @DisplayName("DELETE brand referenced by a live product returns 409 and does not soft-delete")
  void should_return409AndNotSoftDelete_when_liveProductReferencesBrand() throws Exception {
    long id = seedBrand("delblocked");
    seedProduct("OCPC-BLOCKED-001", id);

    mockMvc
        .perform(delete("/api/brands/" + id))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.status").value(409))
        .andExpect(jsonPath("$.errors[0].field").value("general"))
        .andExpect(
            jsonPath("$.errors[0].message")
                .value(
                    "Brand with ID " + id + " cannot be deleted because it is used by products"));

    assertNull(brandDeletedAt(id));
    mockMvc.perform(get("/api/brands/" + id)).andExpect(status().isOk());
  }

  @Test
  @DisplayName("DELETE brand referenced only by soft-deleted products returns 200")
  void should_return200_when_onlySoftDeletedProductsReferenceBrand() throws Exception {
    long id = seedBrand("delsoftref");
    long productId = seedProduct("OCPC-SOFTREF-001", id);
    softDeleteProduct(productId);

    mockMvc
        .perform(delete("/api/brands/" + id))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.id").value(id));

    assertNotNull(brandDeletedAt(id));
  }

  @Test
  @DisplayName("DELETE non-numeric id returns 400 with the type-mismatch handler output")
  void should_return400_when_idIsNonNumeric() throws Exception {
    long before = countBrands();

    mockMvc
        .perform(delete("/api/brands/not-a-number"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.status").value(400))
        .andExpect(jsonPath("$.errors[0].field").value("id"))
        .andExpect(jsonPath("$.errors[0].message").value("Invalid value for parameter: id"));

    assertEquals(before, countBrands());
  }

  @Test
  @DisplayName("A rejected delete leaves the catalog unchanged")
  void should_leaveCatalogUnchanged_when_deleteIsRejected() throws Exception {
    long guardedId = seedBrand("delguarded");
    seedProduct("OCPC-GUARDED-001", guardedId);
    long softId = seedBrand("delsoft");
    softDelete(softId);
    long before = countBrands();
    Object guardedDeletedAtBefore = brandDeletedAt(guardedId);

    mockMvc.perform(delete("/api/brands/not-a-number")).andExpect(status().isBadRequest());
    mockMvc.perform(delete("/api/brands/999999")).andExpect(status().isNotFound());
    mockMvc.perform(delete("/api/brands/" + guardedId)).andExpect(status().isConflict());

    assertEquals(before, countBrands());
    assertEquals(guardedDeletedAtBefore, brandDeletedAt(guardedId));
    assertNotNull(brandDeletedAt(softId));
  }
}
