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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.demo.domain.models.brand.Brand;
import com.example.demo.domain.models.brand.BrandType;
import com.example.demo.domain.repositories.BrandRepository;
import com.example.demo.domain.repositories.BrandTypeRepository;
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
    // Method deleteAll processes @SQLRestriction in Brand entity, so it is not deleting entities.
    // It is needed to remove them using native query
    // brandRepository.deleteAll();
    try (EntityManager entityManager = entityManagerFactory.createEntityManager()) {
      EntityTransaction tx = entityManager.getTransaction();
      try {
        tx.begin();
        entityManager.createNativeQuery("DELETE FROM brands").executeUpdate();
        tx.commit();
      } catch (Exception e) {
        if (tx.isActive()) {
          tx.rollback();
        }
        throw e;
      }
    }
    brandTypeRepository
        .deleteAll(); // BrandType doesn't have @SQLRestriction because it doesn't have soft-delete
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
}
