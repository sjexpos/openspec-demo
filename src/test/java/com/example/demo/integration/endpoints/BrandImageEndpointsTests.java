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

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.demo.domain.models.AssetStatus;
import com.example.demo.domain.models.brand.Brand;
import com.example.demo.domain.models.brand.BrandImage;
import com.example.demo.domain.models.brand.BrandType;
import com.example.demo.domain.repositories.BrandImageRepository;
import com.example.demo.domain.repositories.BrandRepository;
import com.example.demo.domain.repositories.BrandTypeRepository;
import com.example.demo.infrastructure.config.AwsS3Properties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.EntityTransaction;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;

class BrandImageEndpointsTests extends EndpointIntegrationTest {

  @Autowired private MockMvc mockMvc;

  @Autowired private ObjectMapper objectMapper;

  @Autowired private BrandTypeRepository brandTypeRepository;

  @Autowired private BrandRepository brandRepository;

  @Autowired private BrandImageRepository brandImageRepository;

  @Autowired private EntityManagerFactory entityManagerFactory;

  @Autowired private S3Client s3Client;

  @Autowired private AwsS3Properties properties;

  private final HttpClient httpClient = HttpClient.newHttpClient();

  private BrandType seededBrandType;

  @BeforeEach
  void setUp() {
    BrandType brandType = new BrandType();
    brandType.setName("grower");
    seededBrandType = brandTypeRepository.save(brandType);
  }

  @AfterEach
  void tearDown() {
    // FK delete order: brand_images BEFORE brands (BrandImage has no @SQLDelete,
    // Brand soft-deletes so brands must go through native SQL).
    try (EntityManager entityManager = entityManagerFactory.createEntityManager()) {
      EntityTransaction tx = entityManager.getTransaction();
      try {
        tx.begin();
        entityManager.createNativeQuery("DELETE FROM brand_images").executeUpdate();
        entityManager.createNativeQuery("DELETE FROM brands").executeUpdate();
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

  private long pendingRowCount(long brandId) {
    try (EntityManager entityManager = entityManagerFactory.createEntityManager()) {
      Number count =
          (Number)
              entityManager
                  .createNativeQuery(
                      "SELECT COUNT(*) FROM brand_images WHERE brand_id = :brandId AND status ="
                          + " 'PENDING'")
                  .setParameter("brandId", brandId)
                  .getSingleResult();
      return count.longValue();
    }
  }

  private String postUploadTarget(long brandId) throws Exception {
    MvcResult result =
        mockMvc
            .perform(post("/api/brands/" + brandId + "/images"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.data.uploadUrl").isString())
            .andExpect(jsonPath("$.data.uploadMethod").value("PUT"))
            .andExpect(jsonPath("$.data.expiresAt").isString())
            .andReturn();
    JsonNode data = objectMapper.readTree(result.getResponse().getContentAsString()).get("data");
    return data.get("uploadUrl").asText();
  }

  @Test
  @DisplayName("POST existing brand returns upload target and persists one PENDING row")
  void should_createPendingRowAndReturnUploadTarget_when_brandExists() throws Exception {
    // Given
    long brandId = seedBrand("withimage");

    // When
    mockMvc
        .perform(post("/api/brands/" + brandId + "/images"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.data.uploadUrl").isString())
        .andExpect(jsonPath("$.data.uploadMethod").value("PUT"))
        .andExpect(jsonPath("$.data.expiresAt").isString());

    // Then: exactly one PENDING row for the brand
    assertThat(pendingRowCount(brandId)).isEqualTo(1L);
  }

  @Test
  @DisplayName("credential-free PUT to the returned URL stores the object at the persisted key")
  void should_storeObjectAtIssuedKey_when_clientUploadsToReturnedUrl() throws Exception {
    // Given
    long brandId = seedBrand("uploadbytes");
    String uploadUrl = postUploadTarget(brandId);
    String imageKey =
        brandImageRepository.findByBrandIdAndStatus(brandId, AssetStatus.PENDING).stream()
            .findFirst()
            .map(BrandImage::getImageKey)
            .orElseThrow();
    byte[] content = "kan-11 credential-free upload".getBytes(StandardCharsets.UTF_8);

    // When: PUT over plain HTTP with no AWS credentials
    HttpResponse<Void> response =
        httpClient.send(
            HttpRequest.newBuilder(java.net.URI.create(uploadUrl))
                .PUT(HttpRequest.BodyPublishers.ofByteArray(content))
                .build(),
            HttpResponse.BodyHandlers.discarding());

    // Then: the object lands at exactly the persisted key
    assertThat(response.statusCode()).isEqualTo(200);
    var head =
        s3Client.headObject(
            HeadObjectRequest.builder().bucket(properties.s3().bucket()).key(imageKey).build());
    assertThat(head.contentLength()).isEqualTo(content.length);

    // Cleanup the uploaded object so the bucket keeps no residue
    s3Client.deleteObject(
        software.amazon.awssdk.services.s3.model.DeleteObjectRequest.builder()
            .bucket(properties.s3().bucket())
            .key(imageKey)
            .build());
  }

  @Test
  @DisplayName("two consecutive calls yield distinct URLs and distinct rows (non-idempotent)")
  void should_createDistinctKeysAndRows_when_calledTwice() throws Exception {
    // Given
    long brandId = seedBrand("twice");

    // When
    String firstUrl = postUploadTarget(brandId);
    String secondUrl = postUploadTarget(brandId);

    // Then
    assertThat(secondUrl).isNotEqualTo(firstUrl);
    List<BrandImage> rows =
        brandImageRepository.findByBrandIdAndStatus(brandId, AssetStatus.PENDING);
    assertThat(rows).hasSize(2);
    assertThat(rows.get(0).getImageKey()).isNotEqualTo(rows.get(1).getImageKey());
  }

  @Test
  @DisplayName("POST soft-deleted brand returns 404")
  void should_return404_when_brandIsSoftDeleted() throws Exception {
    // Given
    long brandId = seedBrand("softdelimg");
    softDelete(brandId);

    // When / Then
    mockMvc
        .perform(post("/api/brands/" + brandId + "/images"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.status").value(404))
        .andExpect(jsonPath("$.errors[0].field").value("general"));
  }

  @Test
  @DisplayName("POST missing brand returns 404 and inserts no row")
  void should_return404AndNotInsertRow_when_brandDoesNotExist() throws Exception {
    // When / Then
    mockMvc
        .perform(post("/api/brands/999999/images"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.status").value(404));

    assertThat(pendingRowCount(999999L)).isZero();
  }

  @Test
  @DisplayName("persisted row holds only the opaque key, never URL or signature material")
  void should_notPersistAnyUrl_when_imageIsRegistered() throws Exception {
    // Given
    long brandId = seedBrand("nourl");

    // When
    postUploadTarget(brandId);

    // Then
    List<?> rows;
    try (EntityManager entityManager = entityManagerFactory.createEntityManager()) {
      rows = entityManager.createNativeQuery("SELECT * FROM brand_images").getResultList();
    }
    assertThat(rows).hasSize(1);
    String flattened =
        rows.stream()
            .map(row -> java.util.Arrays.deepToString((Object[]) row))
            .reduce("", (left, right) -> left + "\n" + right);
    assertThat(flattened).doesNotContain("X-Amz-Signature").doesNotContain("http");
  }
}
