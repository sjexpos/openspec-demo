package com.example.demo.integration.endpoints;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.example.demo.presentation.api.model.CreateDispensaryRequest;
import com.fasterxml.jackson.databind.ObjectMapper;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class DispensaryEndpointsTests extends EndpointIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    // @Test
    // @DisplayName("Should create a new user")
    // void create_shouldReturn201() throws Exception {

    //     CreateDispensaryRequest createDispensaryRequest = CreateDispensaryRequest.builder()
    //             .name("Pharmacy Dispensary")
    //             .address("123 Main St")
    //             .email("pharmacy@example.com")
    //             .licenseStatus("PENDING")
    //             .build();

    //     mockMvc.perform(post("/api/dispensaries")
    //                     .contentType(MediaType.APPLICATION_JSON)
    //                     .content(objectMapper.writeValueAsString(createDispensaryRequest)))
    //             .andExpect(status().isCreated())
    //             .andExpect(jsonPath("$.name").value("Pharmacy Dispensary"))
    //             .andExpect(jsonPath("$.address").value("123 Main St"))
    //             .andExpect(jsonPath("$.email").value("pharmacy@example.com"));
    // }

}
