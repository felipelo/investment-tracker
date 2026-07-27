package com.investmenttracker.web;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.forwardedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Asserts the forward target rather than the rendered page: the bundle only exists in
 * {@code resources/static} after a container build, but the routing decision is what can break.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SpaForwardingControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void spaRoutesForwardToTheBundle() throws Exception {
        mockMvc.perform(get("/holdings")).andExpect(forwardedUrl("/index.html"));
        mockMvc.perform(get("/smith-maneuver")).andExpect(forwardedUrl("/index.html"));
    }

    @Test
    void apiRequestsAreNotSwallowed() throws Exception {
        mockMvc.perform(get("/api/v1/securities")).andExpect(status().isOk());
    }

    @Test
    void actuatorIsNotSwallowed() throws Exception {
        mockMvc.perform(get("/actuator/health")).andExpect(status().isOk());
    }

    @Test
    void unknownAssetsStillReturnNotFound() throws Exception {
        mockMvc.perform(get("/missing.js")).andExpect(status().isNotFound());
    }
}
