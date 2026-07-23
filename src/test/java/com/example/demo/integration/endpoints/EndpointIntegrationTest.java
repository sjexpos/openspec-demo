package com.example.demo.integration.endpoints;

import org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;

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
