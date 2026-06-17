package com.investmenttracker.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.investmenttracker.domain.Account;
import com.investmenttracker.domain.Action;
import com.investmenttracker.domain.Security;
import com.investmenttracker.domain.PriceSnapshot;
import com.investmenttracker.domain.SecurityTransaction;
import com.investmenttracker.repository.AccountRepository;
import com.investmenttracker.repository.PriceSnapshotRepository;
import com.investmenttracker.repository.SecurityRepository;
import com.investmenttracker.repository.SecurityTransactionRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class HoldingControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private SecurityRepository securityRepository;

    @Autowired
    private SecurityTransactionRepository securityTransactionRepository;

    @Autowired
    private PriceSnapshotRepository priceSnapshotRepository;

    @Autowired
    private AccountRepository accountRepository;

    private Account account() {
        return accountRepository.findAllByOrderByLabelAsc().getFirst();
    }

    private Long portfolioId() {
        return account().getPortfolio().getId();
    }

    @Test
    void listHoldingsReturnsComputedPositions() throws Exception {
        var xei = securityRepository.findAllByOrderByTickerAsc().stream()
                .filter(security -> "TSE:XEI".equals(security.getTicker()))
                .findFirst()
                .orElseThrow();
        seedXeiTransactions(xei);

        mockMvc.perform(get("/api/v1/holdings").param("portfolioId", String.valueOf(portfolioId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].securityId").value(xei.getId()))
                .andExpect(jsonPath("$[0].ticker").value("TSE:XEI"))
                .andExpect(jsonPath("$[0].name").value("iShares CDN Select Div"))
                .andExpect(jsonPath("$[0].shareBalance").value(1420.000000))
                .andExpect(jsonPath("$[0].totalAcb").value(45639.0000))
                .andExpect(jsonPath("$[0].acbPerShare").value(32.14014085))
                .andExpect(jsonPath("$[0].latestPrice").isEmpty())
                .andExpect(jsonPath("$[0].priceDate").isEmpty())
                .andExpect(jsonPath("$[0].marketValue").isEmpty())
                .andExpect(jsonPath("$[0].unrealizedGainLoss").isEmpty());
    }

    @Test
    void listHoldingsComputesMarketValueAndUnrealizedFromLatestPrice() throws Exception {
        var xei = requireSecurity("TSE:XEI");
        seedXeiTransactions(xei);

        var olderPrice = new BigDecimal("30.00");
        var latestPriceValue = new BigDecimal("33.80");
        savePriceSnapshot(xei, LocalDate.of(2025, 9, 1), olderPrice);
        savePriceSnapshot(xei, LocalDate.of(2025, 12, 31), latestPriceValue);

        mockMvc.perform(get("/api/v1/holdings").param("portfolioId", String.valueOf(portfolioId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].latestPrice").value(33.80))
                .andExpect(jsonPath("$[0].priceDate").value("2025-12-31"))
                // 1420 * 33.80 = 47996.0000
                .andExpect(jsonPath("$[0].marketValue").value(47996.0000))
                // 47996 - 45639 = 2357.0000
                .andExpect(jsonPath("$[0].unrealizedGainLoss").value(2357.0000));
    }

    @Test
    void listHoldingsExcludesZeroShareBalance() throws Exception {
        var xei = requireSecurity("TSE:XEI");
        seedXeiTransactions(xei);

        var enb = requireSecurity("TSE:ENB");
        saveTransaction(enb, Action.BUY, LocalDate.of(2024, 1, 1), 100, "50.00", "0");
        saveTransaction(enb, Action.SELL, LocalDate.of(2024, 6, 1), 100, "55.00", "0");

        mockMvc.perform(get("/api/v1/holdings").param("portfolioId", String.valueOf(portfolioId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].ticker").value("TSE:XEI"));
    }

    @Test
    void getHistoryReturnsComputedRowsInOrder() throws Exception {
        var xei = requireSecurity("TSE:XEI");
        seedXeiTransactions(xei);

        var response = mockMvc.perform(get("/api/v1/holdings/{securityId}/history", xei.getId())
                        .param("portfolioId", String.valueOf(portfolioId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(4)))
                .andExpect(jsonPath("$[0].action").value("BUY"))
                .andExpect(jsonPath("$[0].acbChange").value(15610.0000))
                .andExpect(jsonPath("$[1].action").value("RETURN_OF_CAPITAL"))
                .andExpect(jsonPath("$[1].acbChange").value(-420.0000))
                .andExpect(jsonPath("$[3].shareBalance").value(1420.000000))
                .andExpect(jsonPath("$[3].totalAcb").value(45639.0000))
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode rows = objectMapper.readTree(response);
        assertEquals("2024-03-15", rows.get(0).get("date").asText());
        assertEquals("HELOC-funded buy", rows.get(0).get("notes").asText());
    }

    @Test
    void getHistoryForUnknownSecurityReturnsNotFound() throws Exception {
        mockMvc.perform(get("/api/v1/holdings/{securityId}/history", 999_999L)
                        .param("portfolioId", String.valueOf(portfolioId())))
                .andExpect(status().isNotFound());
    }

    @Test
    void getHistoryForSecurityWithoutTransactionsReturnsEmptyList() throws Exception {
        var bank = requireSecurity("TSE:BANK");

        mockMvc.perform(get("/api/v1/holdings/{securityId}/history", bank.getId())
                        .param("portfolioId", String.valueOf(portfolioId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    private Security requireSecurity(String ticker) {
        return securityRepository.findAllByOrderByTickerAsc().stream()
                .filter(security -> ticker.equals(security.getTicker()))
                .findFirst()
                .orElseThrow();
    }

    private void seedXeiTransactions(Security xei) {
        saveTransaction(xei, Action.BUY, LocalDate.of(2024, 3, 15), 500, "31.20", "10", "HELOC-funded buy");
        saveDistribution(xei, Action.RETURN_OF_CAPITAL, LocalDate.of(2024, 11, 2), "420", "Return of capital");
        saveDistribution(xei, Action.REINVESTED_DISTRIBUTION, LocalDate.of(2025, 6, 10), "380", "Phantom distribution");
        saveTransaction(xei, Action.BUY, LocalDate.of(2025, 9, 20), 920, "32.68", "3.40", null);
    }

    private void saveTransaction(
            Security security,
            Action action,
            LocalDate date,
            long shares,
            String price,
            String commission
    ) {
        saveTransaction(security, action, date, shares, price, commission, null);
    }

    private void saveTransaction(
            Security security,
            Action action,
            LocalDate date,
            long shares,
            String price,
            String commission,
            String notes
    ) {
        var transaction = new SecurityTransaction();
        transaction.setSecurity(security);
        transaction.setAccount(account());
        transaction.setDate(date);
        transaction.setAction(action);
        transaction.setShares(BigDecimal.valueOf(shares));
        transaction.setPricePerShare(new BigDecimal(price));
        transaction.setCommission(new BigDecimal(commission));
        transaction.setNotes(notes);
        securityTransactionRepository.save(transaction);
    }

    private void savePriceSnapshot(Security security, LocalDate date, BigDecimal price) {
        var snapshot = new PriceSnapshot();
        snapshot.setSecurity(security);
        snapshot.setSnapshotDate(date);
        snapshot.setPrice(price);
        priceSnapshotRepository.save(snapshot);
    }

    private void saveDistribution(
            Security security,
            Action action,
            LocalDate date,
            String cashAmount,
            String notes
    ) {
        var transaction = new SecurityTransaction();
        transaction.setSecurity(security);
        transaction.setAccount(account());
        transaction.setDate(date);
        transaction.setAction(action);
        transaction.setCashAmount(new BigDecimal(cashAmount));
        transaction.setNotes(notes);
        securityTransactionRepository.save(transaction);
    }
}
