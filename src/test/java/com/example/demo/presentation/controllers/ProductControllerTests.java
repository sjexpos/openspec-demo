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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.demo.application.exceptions.ConflictException;
import com.example.demo.application.exceptions.NotFoundException;
import com.example.demo.application.services.ProductService;
import com.example.demo.application.services.model.CreateProductCommand;
import com.example.demo.domain.models.brand.Brand;
import com.example.demo.domain.models.product.Category;
import com.example.demo.domain.models.product.Collection;
import com.example.demo.domain.models.product.Product;
import com.example.demo.domain.models.product.Subcategory;
import com.example.demo.domain.models.product.Unit;
import com.example.demo.domain.models.strain.Strain;
import com.example.demo.presentation.api.model.CreateProductRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

// Controller unit tests (D8/D10), mirroring BrandControllerTests: @WebMvcTest + mocked
// ProductService + @Import(GlobalExceptionHandler.class). The empty/malformed-body scenario (D9)
// is covered by create_shouldReturn400WithStaticMessage_when_bodyIsEmpty below.
@WebMvcTest(controllers = ProductController.class)
@Import(GlobalExceptionHandler.class)
class ProductControllerTests {

  @TestConfiguration
  static class TestConfig {}

  @Autowired private MockMvc mockMvc;

  @MockitoBean private ProductService productService;

  private final ObjectMapper objectMapper = new ObjectMapper();

  private CreateProductRequest.CreateProductRequestBuilder validRequestBuilder() {
    // Corrective (2026-09-10, second code-review Major finding): isCoreProduct/approved/enabled
    // are deliberately given distinct values (not all Boolean.TRUE) so a transposition among them
    // in ProductController.create's request->command mapping is observable via the
    // ArgumentCaptor assertions in create_shouldReturn201_when_validRequest below.
    return CreateProductRequest.builder()
        .ocpc("OCPC-001")
        .title("Product title")
        .description("Description")
        .collectionId(1L)
        .categoryId(2L)
        .subcategoryId(3L)
        .brandId(4L)
        .strainId(5L)
        .formatValue(10)
        .formatUnitId(6L)
        .contentValue(20)
        .contentUnitId(7L)
        .isCoreProduct(Boolean.TRUE)
        .approved(Boolean.FALSE)
        .thc(15)
        .cbd(5)
        .enabled(Boolean.TRUE);
  }

  private Product savedProduct(Long id) {
    // Aligned with validRequestBuilder()'s isCoreProduct=true/approved=false/enabled=true so the
    // response-side fixture also distinguishes the isCoreProduct/approved and approved/enabled
    // pairs (isCoreProduct==enabled here, both true, so this fixture alone still cannot catch an
    // isCoreProduct/enabled transposition — see savedProductWithCoreDifferingFromEnabled below).
    return savedProduct(id, Boolean.TRUE, Boolean.FALSE, Boolean.TRUE);
  }

  private Product savedProductWithCoreDifferingFromEnabled(Long id) {
    // Corrective (2026-09-10, pigeonhole gap found by adversarial mutation testing): with 3
    // same-typed boolean fields (isCoreProduct, approved, enabled) and only 2 possible values, any
    // single fixture is guaranteed (by the pigeonhole principle) to leave at least one pair
    // sharing the same value, making a transposition between that pair invisible to assertions
    // that only check final values. savedProduct(id) above has isCoreProduct==enabled (both
    // true), so it cannot catch a swap between them. This second fixture deliberately sets
    // isCoreProduct=false != enabled=true so that specific swap becomes observable.
    return savedProduct(id, Boolean.FALSE, Boolean.TRUE, Boolean.TRUE);
  }

  private Product savedProduct(Long id, Boolean isCoreProduct, Boolean approved, Boolean enabled) {
    Collection collection = new Collection();
    collection.setId(1L);
    collection.setName("Collection");

    Category category = new Category();
    category.setId(2L);
    category.setName("Category");

    Subcategory subcategory = new Subcategory();
    subcategory.setId(3L);
    subcategory.setName("Subcategory");
    subcategory.setCategory(category);

    Brand brand = Brand.builder().id(4L).name("Brand").build();
    Strain strain = Strain.builder().id(5L).name("Strain").build();

    Unit formatUnit = new Unit();
    formatUnit.setId(6L);
    formatUnit.setName("g");

    Unit contentUnit = new Unit();
    contentUnit.setId(7L);
    contentUnit.setName("mg");

    return Product.builder()
        .id(id)
        .ocpc("OCPC-001")
        .title("Product title")
        .description("Description")
        .collection(collection)
        .category(category)
        .subcategory(subcategory)
        .brand(brand)
        .strain(strain)
        .formatValue(10)
        .formatUnit(formatUnit)
        .contentValue(20)
        .contentUnit(contentUnit)
        .isCoreProduct(isCoreProduct)
        .approved(approved)
        .thc(15)
        .cbd(5)
        .enabled(enabled)
        .build();
  }

  @Test
  @DisplayName("POST /api/products valid request returns 201 with every response field correct")
  void create_shouldReturn201_when_validRequest() throws Exception {
    // Given
    given(productService.create(any(CreateProductCommand.class))).willReturn(savedProduct(100L));

    // When / Then — asserts all 18 response fields against the fixture's seven *distinct*
    // reference ids (collectionId=1, categoryId=2, subcategoryId=3, brandId=4, strainId=5,
    // formatUnitId=6, contentUnitId=7), so a transposition of any two reference-id mappings in
    // ProductController.toCreateProductResponse would fail this test (code-review Major #1).
    mockMvc
        .perform(
            post("/api/products")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(validRequestBuilder().build())))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.data.id").value(100))
        .andExpect(jsonPath("$.data.ocpc").value("OCPC-001"))
        .andExpect(jsonPath("$.data.title").value("Product title"))
        .andExpect(jsonPath("$.data.description").value("Description"))
        .andExpect(jsonPath("$.data.collectionId").value(1))
        .andExpect(jsonPath("$.data.categoryId").value(2))
        .andExpect(jsonPath("$.data.subcategoryId").value(3))
        .andExpect(jsonPath("$.data.brandId").value(4))
        .andExpect(jsonPath("$.data.strainId").value(5))
        .andExpect(jsonPath("$.data.formatValue").value(10))
        .andExpect(jsonPath("$.data.formatUnitId").value(6))
        .andExpect(jsonPath("$.data.contentValue").value(20))
        .andExpect(jsonPath("$.data.contentUnitId").value(7))
        .andExpect(jsonPath("$.data.isCoreProduct").value(true))
        .andExpect(jsonPath("$.data.approved").value(false))
        .andExpect(jsonPath("$.data.thc").value(15))
        .andExpect(jsonPath("$.data.cbd").value(5))
        .andExpect(jsonPath("$.data.enabled").value(true));

    // Corrective (2026-09-10, second code-review Major finding): capture the command actually
    // passed to productService.create(...) and assert every one of its 17 fields against the
    // request fixture's own (distinct) values, so a transposition anywhere in
    // ProductController.create's request->command mapping block would fail this assertion.
    ArgumentCaptor<CreateProductCommand> commandCaptor =
        ArgumentCaptor.forClass(CreateProductCommand.class);
    verify(productService).create(commandCaptor.capture());
    CreateProductCommand capturedCommand = commandCaptor.getValue();
    assertEquals("OCPC-001", capturedCommand.ocpc());
    assertEquals("Product title", capturedCommand.title());
    assertEquals("Description", capturedCommand.description());
    assertEquals(1L, capturedCommand.collectionId());
    assertEquals(2L, capturedCommand.categoryId());
    assertEquals(3L, capturedCommand.subcategoryId());
    assertEquals(4L, capturedCommand.brandId());
    assertEquals(5L, capturedCommand.strainId());
    assertEquals(10, capturedCommand.formatValue());
    assertEquals(6L, capturedCommand.formatUnitId());
    assertEquals(20, capturedCommand.contentValue());
    assertEquals(7L, capturedCommand.contentUnitId());
    assertEquals(Boolean.TRUE, capturedCommand.isCoreProduct());
    assertEquals(Boolean.FALSE, capturedCommand.approved());
    assertEquals(15, capturedCommand.thc());
    assertEquals(5, capturedCommand.cbd());
    assertEquals(Boolean.TRUE, capturedCommand.enabled());
  }

  @Test
  @DisplayName(
      "POST /api/products isCoreProduct/enabled with differing values are not transposed"
          + " (pigeonhole corrective)")
  void create_shouldNotTransposeIsCoreProductAndEnabled_when_theirValuesDiffer() throws Exception {
    // Corrective (2026-09-10, pigeonhole gap found by adversarial mutation testing): the primary
    // fixture above has isCoreProduct=true, approved=false, enabled=true — isCoreProduct==enabled
    // (both true), so a transposition between exactly those two fields is invisible to it (3
    // same-typed boolean fields, only 2 possible values, pigeonhole principle guarantees at least
    // one equal pair per fixture). This second scenario deliberately sets isCoreProduct=false !=
    // enabled=true, on both the request->command side and the response side, to make that
    // specific swap observable.
    CreateProductRequest request =
        validRequestBuilder().isCoreProduct(Boolean.FALSE).approved(Boolean.TRUE).build();
    given(productService.create(any(CreateProductCommand.class)))
        .willReturn(savedProductWithCoreDifferingFromEnabled(101L));

    // When / Then
    mockMvc
        .perform(
            post("/api/products")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.data.isCoreProduct").value(false))
        .andExpect(jsonPath("$.data.approved").value(true))
        .andExpect(jsonPath("$.data.enabled").value(true));

    ArgumentCaptor<CreateProductCommand> commandCaptor =
        ArgumentCaptor.forClass(CreateProductCommand.class);
    verify(productService).create(commandCaptor.capture());
    CreateProductCommand capturedCommand = commandCaptor.getValue();
    assertEquals(Boolean.FALSE, capturedCommand.isCoreProduct());
    assertEquals(Boolean.TRUE, capturedCommand.approved());
    assertEquals(Boolean.TRUE, capturedCommand.enabled());
  }

  @Test
  @DisplayName("POST /api/products missing title returns 400 with FieldError for title")
  void create_shouldReturn400_when_missingTitle() throws Exception {
    // Given
    CreateProductRequest request = validRequestBuilder().title(null).build();

    // When / Then
    mockMvc
        .perform(
            post("/api/products")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.status").value(400))
        .andExpect(jsonPath("$.errors[0].field").value("title"));

    verify(productService, never()).create(any());
  }

  @Test
  @DisplayName("POST /api/products blank ocpc returns 400 with FieldError for ocpc")
  void create_shouldReturn400_when_blankOcpc() throws Exception {
    // Given
    CreateProductRequest request = validRequestBuilder().ocpc("   ").build();

    // When / Then
    mockMvc
        .perform(
            post("/api/products")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.status").value(400))
        .andExpect(jsonPath("$.errors[0].field").value("ocpc"));

    verify(productService, never()).create(any());
  }

  @Test
  @DisplayName("POST /api/products non-positive formatValue returns 400")
  void create_shouldReturn400_when_formatValueIsNotPositive() throws Exception {
    // Given
    CreateProductRequest request = validRequestBuilder().formatValue(0).build();

    // When / Then
    mockMvc
        .perform(
            post("/api/products")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.status").value(400))
        .andExpect(jsonPath("$.errors[0].field").value("formatValue"));

    verify(productService, never()).create(any());
  }

  @Test
  @DisplayName("POST /api/products negative thc returns 400")
  void create_shouldReturn400_when_thcIsNegative() throws Exception {
    // Given
    CreateProductRequest request = validRequestBuilder().thc(-1).build();

    // When / Then
    mockMvc
        .perform(
            post("/api/products")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.status").value(400))
        .andExpect(jsonPath("$.errors[0].field").value("thc"));

    verify(productService, never()).create(any());
  }

  @Test
  @DisplayName("POST /api/products ocpc exceeding 64 characters returns 400 (code-review Major #2)")
  void create_shouldReturn400_when_ocpcExceedsMaxLength() throws Exception {
    // Given
    CreateProductRequest request = validRequestBuilder().ocpc("a".repeat(65)).build();

    // When / Then
    mockMvc
        .perform(
            post("/api/products")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.status").value(400))
        .andExpect(jsonPath("$.errors[0].field").value("ocpc"));

    verify(productService, never()).create(any());
  }

  @Test
  @DisplayName(
      "POST /api/products title exceeding 255 characters returns 400 (code-review Major #2)")
  void create_shouldReturn400_when_titleExceedsMaxLength() throws Exception {
    // Given
    CreateProductRequest request = validRequestBuilder().title("a".repeat(256)).build();

    // When / Then
    mockMvc
        .perform(
            post("/api/products")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.status").value(400))
        .andExpect(jsonPath("$.errors[0].field").value("title"));

    verify(productService, never()).create(any());
  }

  @Test
  @DisplayName(
      "POST /api/products ocpc at exactly 64 characters is accepted (corrective m1, two-sided"
          + " boundary)")
  void create_shouldReturn201_when_ocpcIsExactlyAtMaxLength() throws Exception {
    // Corrective (2026-09-10, third code-review Minor m1): the max+1 test above only proves the
    // upper boundary is REJECTED. Without this test, tightening @Size(max = 64) down to a smaller
    // value (e.g. 40) leaves the whole suite green, since no test ever proves the documented limit
    // itself is still ACCEPTED. This test pins the accept-at-limit direction of the same boundary.
    CreateProductRequest request = validRequestBuilder().ocpc("a".repeat(64)).build();
    given(productService.create(any(CreateProductCommand.class))).willReturn(savedProduct(102L));

    // When / Then
    mockMvc
        .perform(
            post("/api/products")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isCreated());
  }

  @Test
  @DisplayName(
      "POST /api/products title at exactly 255 characters is accepted (corrective m1, two-sided"
          + " boundary)")
  void create_shouldReturn201_when_titleIsExactlyAtMaxLength() throws Exception {
    // Corrective (2026-09-10, third code-review Minor m1): mirrors the ocpc test above for the
    // title boundary — proves @Size(max = 255) still accepts a value at exactly the documented
    // limit, so simultaneously tightening both bounds (e.g. to 40) is now caught.
    CreateProductRequest request = validRequestBuilder().title("a".repeat(255)).build();
    given(productService.create(any(CreateProductCommand.class))).willReturn(savedProduct(103L));

    // When / Then
    mockMvc
        .perform(
            post("/api/products")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isCreated());
  }

  @Test
  @DisplayName("POST /api/products service NotFoundException returns 404")
  void create_shouldReturn404_when_serviceThrowsNotFound() throws Exception {
    // Given
    given(productService.create(any(CreateProductCommand.class)))
        .willThrow(new NotFoundException("Brand not found with ID: 999"));

    // When / Then
    mockMvc
        .perform(
            post("/api/products")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(validRequestBuilder().build())))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.status").value(404))
        .andExpect(jsonPath("$.errors[0].field").value("general"));
  }

  @Test
  @DisplayName("POST /api/products service ConflictException returns 409")
  void create_shouldReturn409_when_serviceThrowsConflict() throws Exception {
    // Given
    given(productService.create(any(CreateProductCommand.class)))
        .willThrow(new ConflictException("Product already exists with OCPC: OCPC-001"));

    // When / Then
    mockMvc
        .perform(
            post("/api/products")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(validRequestBuilder().build())))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.status").value(409))
        .andExpect(jsonPath("$.errors[0].field").value("general"));
  }

  @Test
  @DisplayName("POST /api/products empty body returns 400 with static message (D9)")
  void create_shouldReturn400WithStaticMessage_when_bodyIsEmpty() throws Exception {
    // When / Then
    mockMvc
        .perform(post("/api/products").contentType(MediaType.APPLICATION_JSON).content(""))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.status").value(400))
        .andExpect(jsonPath("$.errors[0].field").value("general"))
        .andExpect(jsonPath("$.errors[0].message").value("Malformed or missing request body"));

    verify(productService, never()).create(any());
  }
}
