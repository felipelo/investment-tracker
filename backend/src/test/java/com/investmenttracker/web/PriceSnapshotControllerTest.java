package com.investmenttracker.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.investmenttracker.domain.Security;
import com.investmenttracker.repository.SecurityRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class PriceSnapshotControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private SecurityRepository securityRepository;

    @Test
    void createBatchPersistsSnapshots() throws Exception {
        var xei = requireSecurity("TSE:XEI");
        var enb = requireSecurity("TSE:ENB");

        var body = Map.of("snapshots", List.of(
                Map.of("securityId", xei.getId(), "date", "2025-12-31", "price", "33.80"),
                Map.of("securityId", enb.getId(), "date", "2025-12-31", "price", "52.40")
        ));

        mockMvc.perform(post("/api/v1/price-snapshots")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].securityId").value(xei.getId()))
                .andExpect(jsonPath("$[0].price").value(33.80))
                .andExpect(jsonPath("$[1].price").value(52.40));
    }

    @Test
    void listReturnsSnapshotsFilteredBySecurity() throws Exception {
        var xei = requireSecurity("TSE:XEI");
        var enb = requireSecurity("TSE:ENB");
        postSnapshot(xei.getId(), "2025-12-31", "33.80");
        postSnapshot(enb.getId(), "2025-12-31", "52.40");

        mockMvc.perform(get("/api/v1/price-snapshots").param("securityId", String.valueOf(xei.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].securityId").value(xei.getId()));

        mockMvc.perform(get("/api/v1/price-snapshots"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)));
    }

    @Test
    void resubmittingSameSecurityAndDateUpsertsPrice() throws Exception {
        var xei = requireSecurity("TSE:XEI");
        postSnapshot(xei.getId(), "2025-12-31", "33.80");
        postSnapshot(xei.getId(), "2025-12-31", "34.10");

        mockMvc.perform(get("/api/v1/price-snapshots").param("securityId", String.valueOf(xei.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].price").value(34.10));
    }

    @Test
    void unknownSecurityIsRejected() throws Exception {
        var body = Map.of("snapshots", List.of(
                Map.of("securityId", 999_999L, "date", "2025-12-31", "price", "10.00")
        ));

        mockMvc.perform(post("/api/v1/price-snapshots")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors['snapshots[0].securityId']").value("Security not found"));
    }

    @Test
    void emptySnapshotListIsRejected() throws Exception {
        var body = Map.of("snapshots", List.of());

        mockMvc.perform(post("/api/v1/price-snapshots")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void negativePriceIsRejected() throws Exception {
        var xei = requireSecurity("TSE:XEI");
        var body = Map.of("snapshots", List.of(
                Map.of("securityId", xei.getId(), "date", "2025-12-31", "price", "-1.00")
        ));

        mockMvc.perform(post("/api/v1/price-snapshots")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest());
    }

    private void postSnapshot(Long securityId, String date, String price) throws Exception {
        var body = Map.of("snapshots", List.of(
                Map.of("securityId", securityId, "date", date, "price", price)
        ));
        mockMvc.perform(post("/api/v1/price-snapshots")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated());
    }

    private Security requireSecurity(String ticker) {
        return securityRepository.findAllByOrderByTickerAsc().stream()
                .filter(security -> ticker.equals(security.getTicker()))
                .findFirst()
                .orElseThrow();
    }
}
