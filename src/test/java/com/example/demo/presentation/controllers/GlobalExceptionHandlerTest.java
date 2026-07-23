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

import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasEntry;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.demo.presentation.controllers.GlobalExceptionHandlerTest.TestController;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@WebMvcTest(controllers = TestController.class)
@Import(GlobalExceptionHandler.class)
class GlobalExceptionHandlerTest {

  static class TestRequest {
    @NotNull(message = "name must not be empty") private String name;

    @NotEmpty(message = "enumeration must not be empty")
    private String enumeration;

    @Email
    @NotNull(message = "email must not be empty") private String email;
  }

  @RestController
  @Validated
  static class TestController {

    @GetMapping("/test/error")
    void throwRuntimeException() {
      throw new RuntimeException("Intentional test exception");
    }

    @PostMapping("/test/validations")
    public ResponseEntity<String> postMethodName(@Valid @RequestBody TestRequest request) {
      return ResponseEntity.ok().body("Success");
    }
  }

  @TestConfiguration
  static class TestConfig {
    @Bean
    public TestController testController() {
      return new TestController();
    }
  }

  @Autowired private MockMvc mockMvc;

  private ObjectMapper objectMapper = new ObjectMapper();

  @Test
  @DisplayName("Should return 500 with generic message for unhandled exceptions")
  void handleGeneric_shouldReturn500() throws Exception {
    mockMvc
        .perform(get("/test/error"))
        .andExpect(status().isInternalServerError())
        .andExpect(jsonPath("$.status").value(500))
        .andExpect(jsonPath("$.path").value("/test/error"))
        .andExpect(jsonPath("$.errors[0].field").value("general"))
        .andExpect(jsonPath("$.errors[0].message").value("An unexpected error occurred"))
        .andExpect(jsonPath("$.timestamp").isString());
  }

  @Test
  @DisplayName("Should return 404 with generic message for not found endpoint")
  void handleNotFound_shouldReturn404() throws Exception {
    mockMvc
        .perform(get("/test/not_found"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.status").value(404))
        .andExpect(jsonPath("$.path").value("/test/not_found"))
        .andExpect(jsonPath("$.errors[0].field").value("general"))
        .andExpect(jsonPath("$.errors[0].message").value("Resource not found"))
        .andExpect(jsonPath("$.timestamp").isString());
  }

  @Test
  @DisplayName("Should return 400 with field errors when validation fails")
  void handleValidation_shouldReturn400() throws Exception {
    var body = objectMapper.createObjectNode().put("enumeration", "INVALID");
    mockMvc
        .perform(
            post("/test/validations")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.status").value(400))
        .andExpect(jsonPath("$.path").value("/test/validations"))
        .andExpect(jsonPath("$.timestamp").isString())
        .andExpect(
            jsonPath(
                "$.errors[*]",
                containsInAnyOrder(
                    allOf(hasEntry("field", "name"), hasEntry("message", "name must not be empty")),
                    allOf(
                        hasEntry("field", "email"), hasEntry("message", "email must not be empty")),
                    allOf(
                        hasEntry("field", "enumeration"),
                        hasEntry("message", "enumeration must not be empty")))));
  }
}
