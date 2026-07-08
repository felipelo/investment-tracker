package com.investmenttracker.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.investmenttracker.domain.Account;
import com.investmenttracker.domain.Action;
import com.investmenttracker.domain.Security;
import com.investmenttracker.domain.SecurityTransaction;
import com.investmenttracker.repository.AccountRepository;
import com.investmenttracker.repository.SecurityRepository;
import com.investmenttracker.repository.SecurityTransactionRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
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
class PortfolioSnapshotControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private SecurityRepository securityRepository;

    @Autowired
    private SecurityTransactionRepository securityTransactionRepository;

    private Account account() {
        return accountRepository.findAllByOrderByLabelAsc().getFirst();
    }

    private Long portfolioId() {
        return account().getPortfolio().getId();
    }

    private Security requireSecurity(String ticker) {
        return securityRepository.findAllByOrderByTickerAsc().stream()
                .filter(s -> ticker.equals(s.getTicker()))
                .findFirst()
                .orElseThrow();
    }

    @Test
    void priceUpdateAutoCapturesPortfolioSnapshot() throws Exception {
        var xei = requireSecurity("TSE:XEI");
        saveBuy(xei, 100, "20.00");

        var body = Map.of("snapshots", List.of(
                Map.of("securityId", xei.getId(), "date", "2026-01-15", "price", "25.00")
        ));
        mockMvc.perform(post("/api/v1/price-snapshots")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/portfolios/{id}/snapshots", portfolioId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].date").value("2026-01-15"))
                // 100 shares * 25.00 = 2500
                .andExpect(jsonPath("$[0].marketValue").value(2500.0000));
    }

    @Test
    void manualSnapshotUpsertsByDate() throws Exception {
        var portfolioId = portfolioId();

        postManual(portfolioId, "2026-02-01", "10000.00");
        postManual(portfolioId, "2026-02-01", "10500.00");

        mockMvc.perform(get("/api/v1/portfolios/{id}/snapshots", portfolioId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].marketValue").value(10500.0000));
    }

    private void postManual(Long portfolioId, String date, String marketValue) throws Exception {
        var body = Map.of("date", date, "marketValue", marketValue);
        mockMvc.perform(post("/api/v1/portfolios/{id}/snapshots", portfolioId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated());
    }

    private void saveBuy(Security security, long shares, String price) {
        var transaction = new SecurityTransaction();
        transaction.setSecurity(security);
        transaction.setAccount(account());
        transaction.setDate(LocalDate.of(2024, 1, 1));
        transaction.setAction(Action.BUY);
        transaction.setShares(BigDecimal.valueOf(shares));
        transaction.setPricePerShare(new BigDecimal(price));
        transaction.setCommission(BigDecimal.ZERO);
        securityTransactionRepository.save(transaction);
    }
}
