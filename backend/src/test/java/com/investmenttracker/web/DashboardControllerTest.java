package com.investmenttracker.web;

import com.investmenttracker.domain.Account;
import com.investmenttracker.domain.Action;
import com.investmenttracker.domain.Dividend;
import com.investmenttracker.domain.Portfolio;
import com.investmenttracker.domain.PortfolioSnapshot;
import com.investmenttracker.domain.PriceSnapshot;
import com.investmenttracker.domain.Security;
import com.investmenttracker.domain.SecurityTransaction;
import com.investmenttracker.repository.AccountRepository;
import com.investmenttracker.repository.DividendRepository;
import com.investmenttracker.repository.PortfolioSnapshotRepository;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class DashboardControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private SecurityRepository securityRepository;

    @Autowired
    private SecurityTransactionRepository securityTransactionRepository;

    @Autowired
    private PriceSnapshotRepository priceSnapshotRepository;

    @Autowired
    private PortfolioSnapshotRepository portfolioSnapshotRepository;

    @Autowired
    private DividendRepository dividendRepository;

    private Account account() {
        return accountRepository.findAllByOrderByLabelAsc().getFirst();
    }

    private Portfolio portfolio() {
        return account().getPortfolio();
    }

    private Security requireSecurity(String ticker) {
        return securityRepository.findAllByOrderByTickerAsc().stream()
                .filter(s -> ticker.equals(s.getTicker()))
                .findFirst()
                .orElseThrow();
    }

    @Test
    void dashboardAggregatesHeroAllocationAndReturns() throws Exception {
        var xei = requireSecurity("TSE:XEI");
        saveBuy(xei, 100, "20.00");
        savePrice(xei, LocalDate.of(2026, 6, 1), "25.00");

        var today = LocalDate.now();
        savePortfolioSnapshot(today.minusDays(1), "2400.00");
        savePortfolioSnapshot(today.minusYears(1).minusDays(2), "2000.00");

        saveDividend(xei, today.minusDays(3), "50.00", "0");

        mockMvc.perform(get("/api/v1/portfolios/{id}/dashboard", portfolio().getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.portfolioValue").value(2500.0000))
                .andExpect(jsonPath("$.invested").value(2000.0000))
                // 500 (returnAmount) + 50 (net dividends) = 550; 550 / 2000 = 27.5%
                .andExpect(jsonPath("$.allTimeReturn.available").value(true))
                .andExpect(jsonPath("$.allTimeReturn.amount").value(550.0000))
                .andExpect(jsonPath("$.allTimeReturn.pct").value(27.5))
                // 2500 - 2400 = 100; 100 / 2400 = 4.17%
                .andExpect(jsonPath("$.todaysReturn.available").value(true))
                .andExpect(jsonPath("$.todaysReturn.amount").value(100.0000))
                // One Year card uses the year-old snapshot: 2500 - 2000 = 500
                .andExpect(jsonPath("$.periodReturns", hasSize(4)))
                .andExpect(jsonPath("$.periodReturns[3].label").value("One Year"))
                .andExpect(jsonPath("$.periodReturns[3].available").value(true))
                .andExpect(jsonPath("$.periodReturns[3].amount").value(500.0000))
                // single holding => 100% allocation
                .andExpect(jsonPath("$.allocation", hasSize(1)))
                .andExpect(jsonPath("$.allocation[0].ticker").value("TSE:XEI"))
                .andExpect(jsonPath("$.allocation[0].pct").value(100.00));
    }

    @Test
    void returnsAreUnavailableWithoutSnapshots() throws Exception {
        var xei = requireSecurity("TSE:XEI");
        saveBuy(xei, 100, "20.00");
        savePrice(xei, LocalDate.of(2026, 6, 1), "25.00");

        mockMvc.perform(get("/api/v1/portfolios/{id}/dashboard", portfolio().getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.todaysReturn.available").value(false))
                .andExpect(jsonPath("$.periodReturns[0].available").value(false))
                // all-time is still available because it only needs current value + dividends
                .andExpect(jsonPath("$.allTimeReturn.available").value(true));
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

    private void savePrice(Security security, LocalDate date, String price) {
        var snapshot = new PriceSnapshot();
        snapshot.setSecurity(security);
        snapshot.setSnapshotDate(date);
        snapshot.setPrice(new BigDecimal(price));
        priceSnapshotRepository.save(snapshot);
    }

    private void savePortfolioSnapshot(LocalDate date, String marketValue) {
        var snapshot = new PortfolioSnapshot();
        snapshot.setPortfolio(portfolio());
        snapshot.setSnapshotDate(date);
        snapshot.setMarketValue(new BigDecimal(marketValue));
        portfolioSnapshotRepository.save(snapshot);
    }

    private void saveDividend(Security security, LocalDate date, String gross, String withholding) {
        var dividend = new Dividend();
        dividend.setPortfolio(portfolio());
        dividend.setSecurity(security);
        dividend.setPaymentDate(date);
        dividend.setGrossAmount(new BigDecimal(gross));
        dividend.setWithholdingTax(new BigDecimal(withholding));
        dividend.setCurrency("CAD");
        dividend.setDrip(false);
        dividendRepository.save(dividend);
    }
}
