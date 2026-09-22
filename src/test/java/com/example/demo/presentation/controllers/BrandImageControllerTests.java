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

package com.example.demo.presentation.controllers;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.demo.application.exceptions.NotFoundException;
import com.example.demo.application.services.BrandImageService;
import com.example.demo.domain.models.BlobUploadTarget;
import com.example.demo.domain.repositories.BlobStorageException;
import java.net.URI;
import java.time.Instant;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = BrandImageController.class)
@Import(GlobalExceptionHandler.class)
class BrandImageControllerTests {

  @TestConfiguration
  static class TestConfig {}

  @Autowired private MockMvc mockMvc;

  @MockitoBean private BrandImageService brandImageService;

  private BlobUploadTarget uploadTarget() {
    try {
      return new BlobUploadTarget(
          "brands/images/9f2c4a1ed3b74e8fa1c6b0d2e5f7a913",
          new URI("https://example.invalid/brands/images/placeholder?X-Amz-Signature=abc"),
          HttpMethod.PUT,
          Instant.parse("2026-09-22T10:15:30Z"));
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  @Test
  @DisplayName("POST valid brand returns 201 with exact three-field upload target JSON")
  void createUploadTarget_shouldReturn201_withExactJson() throws Exception {
    // Given
    given(brandImageService.createUploadTarget(1L)).willReturn(uploadTarget());

    // When / Then
    mockMvc
        .perform(post("/api/brands/1/images"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.data.uploadUrl").isString())
        .andExpect(jsonPath("$.data.uploadMethod").value("PUT"))
        .andExpect(jsonPath("$.data.expiresAt").isString())
        .andExpect(jsonPath("$.data.*", hasSize(3)));
  }

  @Test
  @DisplayName("POST response exposes only upload fields, never row internals (D2 guard)")
  void createUploadTarget_shouldExposeOnlyUploadFields_when_imageIsRegistered() throws Exception {
    // Given
    given(brandImageService.createUploadTarget(1L)).willReturn(uploadTarget());

    // When / Then
    mockMvc
        .perform(post("/api/brands/1/images"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.data.id").doesNotExist())
        .andExpect(jsonPath("$.data.brandId").doesNotExist())
        .andExpect(jsonPath("$.data.imageKey").doesNotExist())
        .andExpect(jsonPath("$.data.status").doesNotExist());
  }

  @Test
  @DisplayName("POST success carries Cache-Control no-store (bearer capability)")
  void createUploadTarget_shouldReturnNoStore_when_imageIsRegistered() throws Exception {
    // Given
    given(brandImageService.createUploadTarget(1L)).willReturn(uploadTarget());

    // When / Then
    mockMvc
        .perform(post("/api/brands/1/images"))
        .andExpect(status().isCreated())
        .andExpect(header().string("Cache-Control", "no-store"));
  }

  @Test
  @DisplayName("POST missing brand returns 404 with standard error shape")
  void createUploadTarget_shouldReturn404_when_brandDoesNotExist() throws Exception {
    // Given
    given(brandImageService.createUploadTarget(999L))
        .willThrow(new NotFoundException("Brand not found with ID: 999"));

    // When / Then
    mockMvc
        .perform(post("/api/brands/999/images"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.status").value(404))
        .andExpect(jsonPath("$.errors[0].field").value("general"))
        .andExpect(jsonPath("$.errors[0].message").value("Brand not found with ID: 999"));
  }

  @Test
  @DisplayName("POST non-numeric brand id returns 400 without touching the service")
  void createUploadTarget_shouldReturn400_when_brandIdIsNotNumeric() throws Exception {
    // When / Then
    mockMvc
        .perform(post("/api/brands/abc/images"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.status").value(400));

    org.mockito.Mockito.verify(brandImageService, org.mockito.Mockito.never())
        .createUploadTarget(org.mockito.ArgumentMatchers.any());
  }

  @Test
  @DisplayName("POST blob store failure returns 502 with fixed message")
  void createUploadTarget_shouldReturn502_when_blobStorageFails() throws Exception {
    // Given
    given(brandImageService.createUploadTarget(1L))
        .willThrow(new BlobStorageException("presign failed", Set.of()));

    // When / Then
    mockMvc
        .perform(post("/api/brands/1/images"))
        .andExpect(status().isBadGateway())
        .andExpect(jsonPath("$.status").value(502))
        .andExpect(jsonPath("$.errors[0].field").value("general"))
        .andExpect(jsonPath("$.errors[0].message").value("Blob storage is currently unavailable"));
  }

  @Test
  @DisplayName("PUT on the collection URI returns 405")
  void collectionUri_shouldReturn405_when_putIsUsed() throws Exception {
    // When / Then
    mockMvc.perform(put("/api/brands/1/images")).andExpect(status().isMethodNotAllowed());
  }
}
