package com.investmenttracker.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.investmenttracker.domain.Account;
import com.investmenttracker.domain.Security;
import com.investmenttracker.repository.AccountRepository;
import com.investmenttracker.repository.SecurityRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class DividendControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private SecurityRepository securityRepository;

    private Long portfolioId() {
        return accountRepository.findAllByOrderByLabelAsc().getFirst().getPortfolio().getId();
    }

    private Security requireSecurity(String ticker) {
        return securityRepository.findAllByOrderByTickerAsc().stream()
                .filter(s -> ticker.equals(s.getTicker()))
                .findFirst()
                .orElseThrow();
    }

    @Test
    void createPersistsDividendWithDerivedNet() throws Exception {
        var xei = requireSecurity("TSE:XEI");
        var body = dividendBody(portfolioId(), xei.getId(), "2026-03-15", "120.00", "18.00");

        mockMvc.perform(post("/api/v1/dividends")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.ticker").value("TSE:XEI"))
                .andExpect(jsonPath("$.grossAmount").value(120.00))
                .andExpect(jsonPath("$.withholdingTax").value(18.00))
                .andExpect(jsonPath("$.netAmount").value(102.00));
    }

    @Test
    void createWithAccountStoresAttribution() throws Exception {
        var account = accountRepository.findAllByOrderByLabelAsc().getFirst();
        var xei = requireSecurity("TSE:XEI");
        var body = dividendBody(account.getPortfolio().getId(), xei.getId(), "2026-03-15", "120.00", "0");
        body.put("accountId", account.getId());

        mockMvc.perform(post("/api/v1/dividends")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.accountId").value(account.getId()))
                .andExpect(jsonPath("$.accountLabel").value(account.getLabel()));
    }

    @Test
    void listReturnsDividendsForPortfolio() throws Exception {
        var xei = requireSecurity("TSE:XEI");
        postDividend(dividendBody(portfolioId(), xei.getId(), "2026-01-15", "50.00", "0"));
        postDividend(dividendBody(portfolioId(), xei.getId(), "2026-02-15", "60.00", "0"));

        mockMvc.perform(get("/api/v1/dividends").param("portfolioId", String.valueOf(portfolioId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)));
    }

    @Test
    void withholdingExceedingGrossIsRejected() throws Exception {
        var xei = requireSecurity("TSE:XEI");
        var body = dividendBody(portfolioId(), xei.getId(), "2026-03-15", "50.00", "75.00");

        mockMvc.perform(post("/api/v1/dividends")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.withholdingTax").value("Withholding tax cannot exceed the gross amount"));
    }

    @Test
    void summaryBucketsByMonthAndAccumulates() throws Exception {
        var xei = requireSecurity("TSE:XEI");
        postDividend(dividendBody(portfolioId(), xei.getId(), "2026-01-15", "100.00", "0"));
        postDividend(dividendBody(portfolioId(), xei.getId(), "2026-03-10", "200.00", "0"));
        postDividend(dividendBody(portfolioId(), xei.getId(), "2026-03-25", "50.00", "0"));

        mockMvc.perform(get("/api/v1/portfolios/{id}/dividends/summary", portfolioId())
                        .param("year", "2026"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.year").value(2026))
                .andExpect(jsonPath("$.months[0]").value(100.0000))
                .andExpect(jsonPath("$.months[1]").value(0))
                .andExpect(jsonPath("$.months[2]").value(250.0000))
                .andExpect(jsonPath("$.cumulative[2]").value(350.0000))
                .andExpect(jsonPath("$.cumulative[11]").value(350.0000))
                .andExpect(jsonPath("$.ytdTotal").value(350.0000));
    }

    @Test
    void createDefaultsCurrencyFromPortfolio() throws Exception {
        var xei = requireSecurity("TSE:XEI");
        var body = dividendBody(portfolioId(), xei.getId(), "2026-03-15", "50.00", "0");

        mockMvc.perform(post("/api/v1/dividends")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.currency").value("CAD"));
    }

    @Test
    void updateChangesFields() throws Exception {
        var xei = requireSecurity("TSE:XEI");
        var created = mockMvc.perform(post("/api/v1/dividends")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                dividendBody(portfolioId(), xei.getId(), "2026-01-15", "50.00", "0"))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long id = objectMapper.readTree(created).get("id").asLong();

        var update = dividendBody(portfolioId(), xei.getId(), "2026-02-20", "80.00", "10.00");
        update.put("drip", true);
        update.put("notes", "Updated");

        mockMvc.perform(put("/api/v1/dividends/{id}", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(update)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paymentDate").value("2026-02-20"))
                .andExpect(jsonPath("$.grossAmount").value(80.00))
                .andExpect(jsonPath("$.withholdingTax").value(10.00))
                .andExpect(jsonPath("$.netAmount").value(70.00))
                .andExpect(jsonPath("$.drip").value(true))
                .andExpect(jsonPath("$.notes").value("Updated"));
    }

    @Test
    void updateUnknownIdReturns404() throws Exception {
        var xei = requireSecurity("TSE:XEI");
        var body = dividendBody(portfolioId(), xei.getId(), "2026-03-15", "50.00", "0");

        mockMvc.perform(put("/api/v1/dividends/{id}", 999_999L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isNotFound());
    }

    @Test
    void updateRejectsWithholdingExceedingGross() throws Exception {
        var xei = requireSecurity("TSE:XEI");
        var created = mockMvc.perform(post("/api/v1/dividends")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                dividendBody(portfolioId(), xei.getId(), "2026-01-15", "50.00", "0"))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long id = objectMapper.readTree(created).get("id").asLong();

        var body = dividendBody(portfolioId(), xei.getId(), "2026-01-15", "50.00", "75.00");
        mockMvc.perform(put("/api/v1/dividends/{id}", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.withholdingTax").value("Withholding tax cannot exceed the gross amount"));
    }

    @Test
    void deleteRemovesDividend() throws Exception {
        var xei = requireSecurity("TSE:XEI");
        var created = mockMvc.perform(post("/api/v1/dividends")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                dividendBody(portfolioId(), xei.getId(), "2026-01-15", "50.00", "0"))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long id = objectMapper.readTree(created).get("id").asLong();

        mockMvc.perform(delete("/api/v1/dividends/{id}", id))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/dividends").param("portfolioId", String.valueOf(portfolioId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    private void postDividend(Map<String, Object> body) throws Exception {
        mockMvc.perform(post("/api/v1/dividends")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated());
    }

    private static Map<String, Object> dividendBody(
            Long portfolioId,
            Long securityId,
            String paymentDate,
            String gross,
            String withholding
    ) {
        var body = new LinkedHashMap<String, Object>();
        body.put("portfolioId", portfolioId);
        body.put("securityId", securityId);
        body.put("paymentDate", paymentDate);
        body.put("grossAmount", gross);
        body.put("withholdingTax", withholding);
        return body;
    }
}
