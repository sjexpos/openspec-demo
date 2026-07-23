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

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

class DispensaryEndpointsTests extends EndpointIntegrationTest {

  @Autowired private MockMvc mockMvc;

  @Autowired private ObjectMapper objectMapper;

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
