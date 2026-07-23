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

import org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@Import(FlywayAutoConfiguration.class)
// @SetEnvironmentVariable(key = "AWS_ENDPOINT", value = "http://localhost:4566")
// @SetEnvironmentVariable(key = "AWS_REGION", value = "us-east-1")
// @SetEnvironmentVariable(key = "AWS_DEFAULT_REGION", value = "us-east-1")
// @SetEnvironmentVariable(key = "AWS_ACCESS_KEY_ID", value = "test")
// @SetEnvironmentVariable(key = "AWS_SECRET_ACCESS_KEY", value = "test")
class EndpointIntegrationTest {
  // This class is used to load the Spring Boot application context for integration tests.

}
