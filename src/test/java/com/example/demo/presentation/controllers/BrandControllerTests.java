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

import static org.hamcrest.Matchers.hasEntry;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.demo.application.exceptions.NotFoundException;
import com.example.demo.application.services.BrandService;
import com.example.demo.domain.models.brand.Brand;
import com.example.demo.domain.models.brand.BrandType;
import com.example.demo.presentation.api.model.CreateBrandRequest;
import com.example.demo.presentation.api.model.UpdateBrandRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = BrandController.class)
@Import(GlobalExceptionHandler.class)
class BrandControllerTests {

  @TestConfiguration
  static class TestConfig {}

  @Autowired private MockMvc mockMvc;

  @MockitoBean private BrandService brandService;

  private BrandType brandType;

  private ObjectMapper objectMapper = new ObjectMapper();

  @BeforeEach
  void setUp() {
    brandType = new BrandType();
    brandType.setId(1L);
    brandType.setName("grower");
  }

  private Brand liveBrand(Long id, String name) {
    Brand brand =
        Brand.builder()
            .id(id)
            .name(name)
            .description("Description")
            .email(name + "@yopmail.com")
            .stateLicense("LIC")
            .brandType(brandType)
            .logoImageUrl("https://example.com/logo.png")
            .adminId(1)
            .enabled(Boolean.TRUE)
            .build();
    return brand;
  }

  private CreateBrandRequest validCreateRequest() {
    return CreateBrandRequest.builder()
        .name("Green Leaf")
        .description("Description")
        .email("brand@yopmail.com")
        .stateLicense("CAL-01")
        .brandTypeName("grower")
        .logoImageUrl("https://example.com/logo.png")
        .adminId(7)
        .enabled(Boolean.TRUE)
        .build();
  }

  @Test
  @DisplayName("GET /api/brands returns 200 with DataResponse list")
  void getAll_shouldReturn200_when_listOfBrands() throws Exception {
    // Given
    given(brandService.findAll()).willReturn(List.of(liveBrand(1L, "brand-a")));

    // When / Then
    mockMvc
        .perform(get("/api/brands"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data").isArray())
        .andExpect(jsonPath("$.data", hasSize(1)))
        .andExpect(jsonPath("$.data[0].name").value("brand-a"))
        .andExpect(jsonPath("$.data[0].brandTypeName").value("grower"));
  }

  @Test
  @DisplayName("POST /api/brands valid request returns 201 with wrapped response")
  void create_shouldReturn201_when_validRequest() throws Exception {
    // Given
    given(
            brandService.create(
                any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
        .willReturn(liveBrand(10L, "Green Leaf"));

    // When / Then
    mockMvc
        .perform(
            post("/api/brands")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(validCreateRequest())))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.data.id").value(10))
        .andExpect(jsonPath("$.data.name").value("Green Leaf"))
        .andExpect(jsonPath("$.data.brandTypeName").value("grower"));
  }

  @Test
  @DisplayName("POST /api/brands missing name returns 400 with FieldError for name")
  void create_shouldReturn400_when_missingName() throws Exception {
    // When / Then
    CreateBrandRequest request = validCreateRequest();
    request.setName(null);

    mockMvc
        .perform(
            post("/api/brands")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.status").value(400))
        .andExpect(jsonPath("$.errors", org.hamcrest.Matchers.contains(hasEntry("field", "name"))));

    verify(brandService, never())
        .create(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any());
  }

  @Test
  @DisplayName("POST /api/brands invalid email returns 400 field error")
  void create_shouldReturn400_when_invalidEmail() throws Exception {
    // When / Then
    CreateBrandRequest request = validCreateRequest();
    request.setEmail("not-an-email");

    mockMvc
        .perform(
            post("/api/brands")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.status").value(400))
        .andExpect(
            jsonPath("$.errors", org.hamcrest.Matchers.contains(hasEntry("field", "email"))));

    verify(brandService, never())
        .create(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any());
  }

  @Test
  @DisplayName("GET /api/brands/{id} returns 200 for a live brand")
  void getById_shouldReturn200_when_liveBrand() throws Exception {
    // Given
    given(brandService.getById(1L)).willReturn(Optional.of(liveBrand(1L, "brand-a")));

    // When / Then
    mockMvc
        .perform(get("/api/brands/1"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.id").value(1))
        .andExpect(jsonPath("$.data.name").value("brand-a"))
        .andExpect(jsonPath("$.data.brandTypeName").value("grower"));
  }

  @Test
  @DisplayName("GET /api/brands/{id} empty service returns 404")
  void getById_shouldReturn404_when_emptyService() throws Exception {
    // Given
    given(brandService.getById(999L)).willReturn(Optional.empty());

    // When / Then
    mockMvc
        .perform(get("/api/brands/999"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.status").value(404))
        .andExpect(jsonPath("$.errors[0].field").value("general"));
  }

  @Test
  @DisplayName("PATCH /api/brands/{id} valid request returns 200")
  void update_shouldReturn200_when_validRequest() throws Exception {
    // Given
    given(
            brandService.update(
                anyLong(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(),
                any(), any()))
        .willReturn(liveBrand(1L, "updated-name"));

    UpdateBrandRequest request = new UpdateBrandRequest();
    request.setName("updated-name");

    // When / Then
    mockMvc
        .perform(
            patch("/api/brands/1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.name").value("updated-name"));
  }

  @Test
  @DisplayName("PATCH /api/brands/{id} service NotFoundException returns 404")
  void update_shouldReturn404_when_serviceThrowsNotFound() throws Exception {
    // Given
    given(
            brandService.update(
                anyLong(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(),
                any(), any()))
        .willThrow(new NotFoundException("Brand not found with ID: 999"));

    UpdateBrandRequest request = new UpdateBrandRequest();
    request.setName("name");

    // When / Then
    mockMvc
        .perform(
            patch("/api/brands/999")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.status").value(404))
        .andExpect(jsonPath("$.errors[0].field").value("general"));
  }

  @Test
  @DisplayName("PATCH /api/brands/{id} invalid email returns 400")
  void update_shouldReturn400_when_invalidEmail() throws Exception {
    // Given
    UpdateBrandRequest request = new UpdateBrandRequest();
    request.setEmail("not-an-email");

    // When / Then
    mockMvc
        .perform(
            patch("/api/brands/1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.status").value(400))
        .andExpect(jsonPath("$.errors[0].field").value("email"));

    verify(brandService, never())
        .update(
            anyLong(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(),
            any());
  }
}
