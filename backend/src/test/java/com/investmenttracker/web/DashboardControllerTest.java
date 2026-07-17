package com.investmenttracker.web;

import com.investmenttracker.domain.Account;
import com.investmenttracker.domain.Action;
import com.investmenttracker.domain.Dividend;
import com.investmenttracker.domain.Portfolio;
import com.investmenttracker.domain.PriceSnapshot;
import com.investmenttracker.domain.Security;
import com.investmenttracker.domain.SecurityTransaction;
import com.investmenttracker.repository.AccountRepository;
import com.investmenttracker.repository.DividendRepository;
import com.investmenttracker.repository.PortfolioRepository;
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
    private DividendRepository dividendRepository;

    @Autowired
    private PortfolioRepository portfolioRepository;

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

        var today = LocalDate.now();
        // Value as of a date = shares(date) x nearest price on/before date (computed live).
        savePrice(xei, today.minusYears(1).minusDays(2), "20.00"); // basis 2000 for the one-year return
        savePrice(xei, today.minusDays(1), "24.00");               // basis 2400 for today's return
        savePrice(xei, today, "25.00");                            // current value 2500

        saveDividend(xei, today.minusDays(3), "50.00", "0");

        mockMvc.perform(get("/api/v1/portfolios/{id}/dashboard", portfolio().getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.portfolioValue").value(2500.0000))
                .andExpect(jsonPath("$.invested").value(2000.0000))
                // 500 (returnAmount) + 50 (net dividends) = 550; 550 / 2000 = 27.5%
                .andExpect(jsonPath("$.allTimeReturn.available").value(true))
                .andExpect(jsonPath("$.allTimeReturn.amount").value(550.0000))
                .andExpect(jsonPath("$.allTimeReturn.pct").value(27.5))
                // all-time split: price 500 (2500 - 2000) and net dividends 50
                .andExpect(jsonPath("$.priceReturn.amount").value(500.0000))
                .andExpect(jsonPath("$.priceReturn.pct").value(25.00))
                .andExpect(jsonPath("$.dividendReturn.amount").value(50.0000))
                .andExpect(jsonPath("$.dividendReturn.pct").value(2.5))
                // 2500 - 2400 = 100; 100 / 2400 = 4.17%
                .andExpect(jsonPath("$.todaysReturn.available").value(true))
                .andExpect(jsonPath("$.todaysReturn.amount").value(100.0000))
                // One Year column: price 500 (2500 - 2000) + dividend 50 = 550 total
                .andExpect(jsonPath("$.periodReturns", hasSize(4)))
                .andExpect(jsonPath("$.periodReturns[3].label").value("One Year"))
                .andExpect(jsonPath("$.periodReturns[3].available").value(true))
                .andExpect(jsonPath("$.periodReturns[3].amount").value(550.0000))
                .andExpect(jsonPath("$.periodReturns[3].pct").value(27.5))
                .andExpect(jsonPath("$.periodReturns[3].priceAmount").value(500.0000))
                .andExpect(jsonPath("$.periodReturns[3].pricePct").value(25.00))
                .andExpect(jsonPath("$.periodReturns[3].dividendAmount").value(50.0000))
                .andExpect(jsonPath("$.periodReturns[3].dividendPct").value(2.5))
                // single holding => 100% allocation
                .andExpect(jsonPath("$.allocation", hasSize(1)))
                .andExpect(jsonPath("$.allocation[0].ticker").value("TSE:XEI"))
                .andExpect(jsonPath("$.allocation[0].pct").value(100.00))
                // per-ETF breakdown: single holding mirrors the portfolio-level split exactly
                .andExpect(jsonPath("$.holdingBreakdowns", hasSize(1)))
                .andExpect(jsonPath("$.holdingBreakdowns[0].ticker").value("TSE:XEI"))
                .andExpect(jsonPath("$.holdingBreakdowns[0].priceReturn.amount").value(500.0000))
                .andExpect(jsonPath("$.holdingBreakdowns[0].priceReturn.pct").value(25.00))
                .andExpect(jsonPath("$.holdingBreakdowns[0].dividendReturn.amount").value(50.0000))
                .andExpect(jsonPath("$.holdingBreakdowns[0].dividendReturn.pct").value(2.5))
                .andExpect(jsonPath("$.holdingBreakdowns[0].periodReturns[3].priceAmount").value(500.0000))
                .andExpect(jsonPath("$.holdingBreakdowns[0].periodReturns[3].pricePct").value(25.00))
                .andExpect(jsonPath("$.holdingBreakdowns[0].periodReturns[3].dividendAmount").value(50.0000))
                .andExpect(jsonPath("$.holdingBreakdowns[0].periodReturns[3].dividendPct").value(2.5));
    }

    @Test
    void returnsAreUnavailableWithoutPriorPrices() throws Exception {
        var xei = requireSecurity("TSE:XEI");
        saveBuy(xei, 100, "20.00");
        // Only a price dated today: nothing on/before the return targets, so returns are unavailable.
        savePrice(xei, LocalDate.now(), "25.00");

        mockMvc.perform(get("/api/v1/portfolios/{id}/dashboard", portfolio().getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.todaysReturn.available").value(false))
                .andExpect(jsonPath("$.periodReturns[0].available").value(false))
                // all-time is still available because it only needs current value + dividends
                .andExpect(jsonPath("$.allTimeReturn.available").value(true));
    }

    @Test
    void overallDashboardAggregatesAllPortfolios() throws Exception {
        var xei = requireSecurity("TSE:XEI");
        var otherPortfolio = createPortfolio("Second Portfolio");
        var otherAccount = createAccount(otherPortfolio, "Second Margin");
        var today = LocalDate.now();

        saveBuy(account(), xei, 100, "20.00");
        saveBuy(otherAccount, xei, 50, "10.00");
        // Prices are per security, shared across portfolios; basis differs only by share count.
        savePrice(xei, today.minusDays(1), "24.00"); // basis: 2400 (portfolio) + 1200 (other) = 3600
        savePrice(xei, today, "25.00");              // current value 3750
        saveDividend(portfolio(), xei, today.minusDays(3), "50.00", "0");
        saveDividend(otherPortfolio, xei, today.minusDays(2), "25.00", "0");

        mockMvc.perform(get("/api/v1/portfolios/dashboard"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.portfolioValue").value(3750.0000))
                .andExpect(jsonPath("$.invested").value(2500.0000))
                // portfolio 100x(25-24)=100, other 50x(25-24)=50; total 150 over basis 3600 = 4.17%
                .andExpect(jsonPath("$.todaysReturn.amount").value(150.0000))
                .andExpect(jsonPath("$.todaysReturn.pct").value(4.17))
                .andExpect(jsonPath("$.allTimeReturn.amount").value(1325.0000))
                .andExpect(jsonPath("$.allTimeReturn.pct").value(53.00))
                // all-time split across portfolios: price 1250 (500 + 750), dividends 75 (50 + 25)
                .andExpect(jsonPath("$.priceReturn.amount").value(1250.0000))
                .andExpect(jsonPath("$.priceReturn.pct").value(50.00))
                .andExpect(jsonPath("$.dividendReturn.amount").value(75.0000))
                .andExpect(jsonPath("$.dividendReturn.pct").value(3.00))
                .andExpect(jsonPath("$.allocation", hasSize(1)))
                .andExpect(jsonPath("$.allocation[0].marketValue").value(3750.0000))
                .andExpect(jsonPath("$.allocation[0].pct").value(100.00))
                // XEI held in both portfolios collapses into one breakdown that reconciles to the totals:
                // price 500 + 750 = 1250 over reconstructed basis 2000 + 500; dividends 50 + 25 = 75
                .andExpect(jsonPath("$.holdingBreakdowns", hasSize(1)))
                .andExpect(jsonPath("$.holdingBreakdowns[0].ticker").value("TSE:XEI"))
                .andExpect(jsonPath("$.holdingBreakdowns[0].priceReturn.amount").value(1250.0000))
                .andExpect(jsonPath("$.holdingBreakdowns[0].priceReturn.pct").value(50.00))
                .andExpect(jsonPath("$.holdingBreakdowns[0].dividendReturn.amount").value(75.0000))
                .andExpect(jsonPath("$.holdingBreakdowns[0].dividendReturn.pct").value(3.00));

        mockMvc.perform(get("/api/v1/portfolios/dividends/summary")
                        .param("year", String.valueOf(today.getYear())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ytdTotal").value(75.0000));
    }

    private void saveBuy(Security security, long shares, String price) {
        saveBuy(account(), security, shares, price);
    }

    private void saveBuy(Account targetAccount, Security security, long shares, String price) {
        var transaction = new SecurityTransaction();
        transaction.setSecurity(security);
        transaction.setAccount(targetAccount);
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

    private void saveDividend(Security security, LocalDate date, String gross, String withholding) {
        saveDividend(portfolio(), security, date, gross, withholding);
    }

    private void saveDividend(
            Portfolio targetPortfolio,
            Security security,
            LocalDate date,
            String gross,
            String withholding
    ) {
        var dividend = new Dividend();
        dividend.setPortfolio(targetPortfolio);
        dividend.setSecurity(security);
        dividend.setPaymentDate(date);
        dividend.setGrossAmount(new BigDecimal(gross));
        dividend.setWithholdingTax(new BigDecimal(withholding));
        dividend.setCurrency("CAD");
        dividend.setDrip(false);
        dividendRepository.save(dividend);
    }

    private Portfolio createPortfolio(String name) {
        var newPortfolio = new Portfolio();
        newPortfolio.setName(name);
        newPortfolio.setBaseCurrency("CAD");
        return portfolioRepository.save(newPortfolio);
    }

    private Account createAccount(Portfolio targetPortfolio, String label) {
        var newAccount = new Account();
        newAccount.setPortfolio(targetPortfolio);
        newAccount.setLabel(label);
        newAccount.setType("Margin");
        newAccount.setCurrency("CAD");
        newAccount.setOpeningBalance(BigDecimal.ZERO);
        return accountRepository.save(newAccount);
    }
}
