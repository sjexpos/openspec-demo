package com.example.demo.integration.endpoints;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.allOf;
import static org.hamcrest.CoreMatchers.anyOf;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.anything;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

class ActuatorEndpointsTests extends EndpointIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("Should be able to access the info, scheduledtasks, health, and metrics actuator endpoints")
    void testEnabledActuators() throws Exception {
        mockMvc.perform(get("/actuator"))
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/vnd.spring-boot.actuator.v3+json"))
                .andExpect(jsonPath("$._links.info", anything()))
                .andExpect(jsonPath("$._links.scheduledtasks", anything()))
                .andExpect(jsonPath("$._links.health", anything()))
                .andExpect(jsonPath("$._links.metrics", anything()))
                .andExpect(jsonPath("$._links.auditevents").doesNotExist())
                .andExpect(jsonPath("$._links.beans").doesNotExist())
                .andExpect(jsonPath("$._links.caches").doesNotExist())
                .andExpect(jsonPath("$._links.conditions").doesNotExist())
                .andExpect(jsonPath("$._links.configprops").doesNotExist())
                .andExpect(jsonPath("$._links.env").doesNotExist())
                .andExpect(jsonPath("$._links.flyway").doesNotExist())
                .andExpect(jsonPath("$._links.loggers").doesNotExist())
                .andExpect(jsonPath("$._links.logfile").doesNotExist())
                .andExpect(jsonPath("$._links.liquibase").doesNotExist())
                .andExpect(jsonPath("$._links.mappings").doesNotExist())
                .andExpect(jsonPath("$._links.threaddump").doesNotExist())
                .andExpect(jsonPath("$._links.sbom").doesNotExist())
                .andExpect(jsonPath("$._links.sessions").doesNotExist())
                .andExpect(jsonPath("$._links.shutdown").doesNotExist());
    }

    @Test
    @DisplayName("Should return git information in the info actuator endpoint")
    void testInfoEndpoint() throws Exception {
        mockMvc.perform(get("/actuator/info"))
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/vnd.spring-boot.actuator.v3+json"))
                .andExpect(jsonPath("$.git", anything()));
    }

    @Test
    @DisplayName("Should return the correct health status")
    void testHealthEndpoint() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/vnd.spring-boot.actuator.v3+json"))
                .andExpect(jsonPath("$.status", is("UP")))
                .andExpect(jsonPath("$.components").exists())
                .andExpect(jsonPath("$.components.db").exists())
                .andExpect(jsonPath("$.components.db.status", is("UP")))
                .andExpect(jsonPath("$.components.diskSpace").exists())
                .andExpect(jsonPath("$.components.diskSpace.status", is("UP")));
    }

    @Test
    @DisplayName("Should return the correct metrics")
    void testMetricsEndpoint() throws Exception {
        mockMvc.perform(get("/actuator/metrics"))
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/vnd.spring-boot.actuator.v3+json"))
                .andExpect(jsonPath("$.names", hasItems("jvm.memory.used", "http.server.requests.active", "jvm.threads.live")));
    }

}
