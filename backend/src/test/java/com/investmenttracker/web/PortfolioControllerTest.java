package com.investmenttracker.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.investmenttracker.domain.Account;
import com.investmenttracker.domain.Action;
import com.investmenttracker.domain.Portfolio;
import com.investmenttracker.domain.Security;
import com.investmenttracker.domain.PriceSnapshot;
import com.investmenttracker.domain.SecurityTransaction;
import com.investmenttracker.repository.AccountRepository;
import com.investmenttracker.repository.PortfolioRepository;
import com.investmenttracker.repository.PriceSnapshotRepository;
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
import java.util.Map;

import static org.hamcrest.Matchers.greaterThanOrEqualTo;
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
class PortfolioControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private PortfolioRepository portfolioRepository;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private SecurityRepository securityRepository;

    @Autowired
    private SecurityTransactionRepository securityTransactionRepository;

    @Autowired
    private PriceSnapshotRepository priceSnapshotRepository;

    @Test
    void createPersistsPortfolio() throws Exception {
        var body = Map.of(
                "name", "Wealthsimple TFSA",
                "description", "Registered growth account",
                "baseCurrency", "CAD",
                "type", "TFSA"
        );

        mockMvc.perform(post("/api/v1/portfolios")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.name").value("Wealthsimple TFSA"))
                .andExpect(jsonPath("$.type").value("TFSA"))
                .andExpect(jsonPath("$.holdingsCount").value(0))
                .andExpect(jsonPath("$.invested").value(0));
    }

    @Test
    void duplicateNameIsRejected() throws Exception {
        var body = Map.of("name", "Default Portfolio");

        mockMvc.perform(post("/api/v1/portfolios")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.name").value("A portfolio with this name already exists"));
    }

    @Test
    void blankNameIsRejected() throws Exception {
        var body = Map.of("name", "   ");

        mockMvc.perform(post("/api/v1/portfolios")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void listComputesDerivedMetricsFromTransactions() throws Exception {
        var account = accountRepository.findAllByOrderByLabelAsc().getFirst();
        var portfolio = account.getPortfolio();
        var xei = requireSecurity("TSE:XEI");

        saveBuy(xei, account, LocalDate.of(2024, 1, 1), 100, "20.00");
        savePrice(xei, LocalDate.of(2024, 12, 31), "25.00");

        mockMvc.perform(get("/api/v1/portfolios/{id}", portfolio.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.invested").value(2000.0))
                .andExpect(jsonPath("$.marketValue").value(2500.0))
                .andExpect(jsonPath("$.returnAmount").value(500.0))
                .andExpect(jsonPath("$.returnPct").value(25.0))
                .andExpect(jsonPath("$.holdingsCount").value(1));
    }

    @Test
    void listReturnsAtLeastTheDefaultPortfolio() throws Exception {
        mockMvc.perform(get("/api/v1/portfolios"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(greaterThanOrEqualTo(1)));
    }

    @Test
    void updateChangesFields() throws Exception {
        var portfolio = newPortfolio("To rename");
        var body = Map.of("name", "Renamed", "type", "Taxable");

        mockMvc.perform(put("/api/v1/portfolios/{id}", portfolio.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Renamed"))
                .andExpect(jsonPath("$.type").value("Taxable"));
    }

    @Test
    void deleteEmptyPortfolioSucceeds() throws Exception {
        var portfolio = newPortfolio("Disposable");

        mockMvc.perform(delete("/api/v1/portfolios/{id}", portfolio.getId()))
                .andExpect(status().isNoContent());
    }

    @Test
    void deletePortfolioWithAccountsIsRejected() throws Exception {
        var account = accountRepository.findAllByOrderByLabelAsc().getFirst();

        mockMvc.perform(delete("/api/v1/portfolios/{id}", account.getPortfolio().getId()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.portfolio").value("Cannot delete a portfolio that still has accounts"));
    }

    private Portfolio newPortfolio(String name) {
        var portfolio = new Portfolio();
        portfolio.setName(name);
        portfolio.setBaseCurrency("CAD");
        return portfolioRepository.save(portfolio);
    }

    private Security requireSecurity(String ticker) {
        return securityRepository.findAllByOrderByTickerAsc().stream()
                .filter(security -> ticker.equals(security.getTicker()))
                .findFirst()
                .orElseThrow();
    }

    private void saveBuy(Security security, Account account, LocalDate date, long shares, String price) {
        var transaction = new SecurityTransaction();
        transaction.setSecurity(security);
        transaction.setAccount(account);
        transaction.setDate(date);
        transaction.setAction(Action.BUY);
        transaction.setShares(BigDecimal.valueOf(shares));
        transaction.setPricePerShare(new BigDecimal(price));
        transaction.setCommission(BigDecimal.ZERO);
        securityTransactionRepository.save(transaction);
    }

    private void savePrice(Security security, LocalDate date, String price) {
        var snapshot = new PriceSnapshot();
        snapshot.setSecurity(security);
        snapshot.setSnapshotDate(date);
        snapshot.setPrice(new BigDecimal(price));
        priceSnapshotRepository.save(snapshot);
    }
}
