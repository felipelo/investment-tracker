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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class SecurityTransactionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private SecurityRepository securityRepository;

    @Autowired
    private AccountRepository accountRepository;

    private Account account() {
        return accountRepository.findAllByOrderByLabelAsc().getFirst();
    }

    private Long portfolioId() {
        return account().getPortfolio().getId();
    }

    private Security security(String ticker) {
        return securityRepository.findAllByOrderByTickerAsc().stream()
                .filter(s -> ticker.equals(s.getTicker()))
                .findFirst()
                .orElseThrow();
    }

    @Test
    void deniedLossAdjustmentOnNonSellIsRejected() throws Exception {
        var body = tradeBody(security("TSE:XEI").getId(), "BUY", "2024-01-01", "100", "20.00");
        body.put("deniedLossAdjustment", "250.00");

        mockMvc.perform(post("/api/v1/security-transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.deniedLossAdjustment")
                        .value("Denied loss adjustment is only allowed for Sell"));
    }

    @Test
    void negativeDeniedLossAdjustmentIsRejected() throws Exception {
        var body = tradeBody(security("TSE:XEI").getId(), "SELL", "2024-02-01", "50", "15.00");
        body.put("deniedLossAdjustment", "-10.00");

        mockMvc.perform(post("/api/v1/security-transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.deniedLossAdjustment")
                        .value("Denied loss adjustment must be zero or greater"));
    }

    @Test
    void historyFlagsSuperficialLossAndPersistsDeniedLossAdjustment() throws Exception {
        var xei = security("TSE:XEI");
        mockMvc.perform(post("/api/v1/security-transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                tradeBody(xei.getId(), "BUY", "2024-01-01", "100", "20.00"))))
                .andExpect(status().isCreated());

        var sell = tradeBody(xei.getId(), "SELL", "2024-01-20", "50", "15.00");
        sell.put("deniedLossAdjustment", "250.00");
        mockMvc.perform(post("/api/v1/security-transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(sell)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.deniedLossAdjustment").value(250.0000));

        mockMvc.perform(get("/api/v1/holdings/{securityId}/history", xei.getId())
                        .param("portfolioId", String.valueOf(portfolioId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[1].action").value("SELL"))
                .andExpect(jsonPath("$[1].superficialLossFlag").value(true))
                .andExpect(jsonPath("$[1].deniedLossAdjustment").value(250.0000))
                .andExpect(jsonPath("$[0].superficialLossFlag").value(false));
    }

    @Test
    void fourDecimalPricePerShareRoundTrips() throws Exception {
        var xei = security("TSE:XEI");
        mockMvc.perform(post("/api/v1/security-transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                tradeBody(xei.getId(), "BUY", "2024-03-01", "100", "4.9734"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.pricePerShare").value(4.9734));

        mockMvc.perform(get("/api/v1/holdings/{securityId}/history", xei.getId())
                        .param("portfolioId", String.valueOf(portfolioId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].pricePerShare").value(4.9734));
    }

    private Map<String, Object> tradeBody(Long securityId, String action, String date, String shares, String price) {
        var body = new LinkedHashMap<String, Object>();
        body.put("date", date);
        body.put("securityId", securityId);
        body.put("accountId", account().getId());
        body.put("action", action);
        body.put("shares", shares);
        body.put("pricePerShare", price);
        body.put("commission", "0");
        return body;
    }
}
