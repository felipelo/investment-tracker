package com.investmenttracker.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The rest of the suite runs with {@code app.auth.enabled=false}, so this is the one place the
 * protected filter chain is exercised.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SecurityConfigTest {

    private static final String USERNAME = "tracker";
    private static final String PASSWORD = "correct-horse-battery-staple";

    @DynamicPropertySource
    static void enableAuth(DynamicPropertyRegistry registry) {
        registry.add("app.auth.enabled", () -> "true");
        registry.add("app.auth.username", () -> USERNAME);
        registry.add("app.auth.password-hash", () -> new BCryptPasswordEncoder().encode(PASSWORD));
    }

    @Autowired
    private MockMvc mockMvc;

    @Test
    void apiRejectsAnonymousRequests() throws Exception {
        mockMvc.perform(get("/api/v1/securities"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void apiAcceptsTheConfiguredCredential() throws Exception {
        mockMvc.perform(get("/api/v1/securities").header("Authorization", basic(USERNAME, PASSWORD)))
                .andExpect(status().isOk());
    }

    @Test
    void apiRejectsTheWrongPassword() throws Exception {
        mockMvc.perform(get("/api/v1/securities").header("Authorization", basic(USERNAME, "wrong")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void healthProbesStayAnonymousSoOrchestratorsCanReachThem() throws Exception {
        mockMvc.perform(get("/actuator/health")).andExpect(status().isOk());
        mockMvc.perform(get("/actuator/health/readiness")).andExpect(status().isOk());
    }

    private static String basic(String username, String password) {
        var credentials = (username + ":" + password).getBytes(StandardCharsets.UTF_8);
        return "Basic " + Base64.getEncoder().encodeToString(credentials);
    }
}
