package com.investmenttracker.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.investmenttracker.domain.Account;
import com.investmenttracker.domain.Action;
import com.investmenttracker.domain.CashPurpose;
import com.investmenttracker.domain.CashTransaction;
import com.investmenttracker.domain.CashTransactionType;
import com.investmenttracker.domain.Dividend;
import com.investmenttracker.domain.Portfolio;
import com.investmenttracker.domain.Security;
import com.investmenttracker.domain.SecurityTransaction;
import com.investmenttracker.repository.AccountRepository;
import com.investmenttracker.repository.CashTransactionRepository;
import com.investmenttracker.repository.DividendRepository;
import com.investmenttracker.repository.PortfolioRepository;
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
import java.util.LinkedHashMap;
import java.util.List;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class TaxSummaryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private PortfolioRepository portfolioRepository;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private CashTransactionRepository cashTransactionRepository;

    @Autowired
    private SecurityRepository securityRepository;

    @Autowired
    private SecurityTransactionRepository securityTransactionRepository;

    @Autowired
    private DividendRepository dividendRepository;

    private Portfolio defaultPortfolio() {
        return portfolioRepository.findAllByOrderByNameAsc().getFirst();
    }

    private Long portfolioId() {
        return defaultPortfolio().getId();
    }

    @Test
    void taxSummaryAggregatesRealizedGainsDividendsAndInterestForSelectedYear() throws Exception {
        var margin = createAccount("Test Margin", "Margin");
        var heloc = createAccount("Test HELOC", "HELOC");
        var xei = requireSecurity("TSE:XEI");
        var bank = requireSecurity("TSE:BANK");

        // Realized gain in 2025: buy 100 @ 20 then sell 50 @ 30 => proceeds 1500, ACB disposed 1000, gain 500.
        createBuy(margin, xei, "2025-02-01", 100, "20.00");
        createSell(margin, xei, "2025-06-01", 50, "30.00");

        // Dividend income: 2025 (kept) and 2024 (excluded from the 2025 view but drives availableYears).
        createDividend(xei, "2025-03-15", "100.00", "10.00");
        createDividend(xei, "2024-03-15", "40.00", "0");

        // Smith Maneuver: 9,500 investment draw + 500 interest => owed 10,000, traced 0.95, deductible 475.
        var draw = createCash(heloc, CashTransactionType.HELOC_DRAW, "2025-05-01", "-9500.00", CashPurpose.INVESTMENT);
        createCash(heloc, CashTransactionType.INTEREST_CHARGE, "2025-05-31", "-500.00", null);
        var flowBuy = createBuy(margin, bank, "2025-05-01", 100, "10.00");
        createFlow(heloc.getId(), "Flow 2025", "9500.00", List.of(draw.getId()), flowBuy.getId())
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/portfolios/{id}/tax-summary", portfolioId()))
                .andExpect(status().isOk())
                // resolves to the most recent year with activity
                .andExpect(jsonPath("$.year").value(2025))
                .andExpect(jsonPath("$.availableYears", contains(2025, 2024)))
                // realized gains: only the XEI disposition
                .andExpect(jsonPath("$.realizedGains.rows", hasSize(1)))
                .andExpect(jsonPath("$.realizedGains.rows[0].ticker").value("TSE:XEI"))
                .andExpect(jsonPath("$.realizedGains.rows[0].dispositions").value(1))
                .andExpect(jsonPath("$.realizedGains.rows[0].proceeds").value(1500.0000))
                .andExpect(jsonPath("$.realizedGains.rows[0].acbDisposed").value(1000.0000))
                .andExpect(jsonPath("$.realizedGains.rows[0].gainLoss").value(500.0000))
                .andExpect(jsonPath("$.realizedGains.total.gainLoss").value(500.0000))
                // dividend income: only the 2025 dividend
                .andExpect(jsonPath("$.dividends.rows", hasSize(1)))
                .andExpect(jsonPath("$.dividends.rows[0].ticker").value("TSE:XEI"))
                .andExpect(jsonPath("$.dividends.rows[0].gross").value(100.0000))
                .andExpect(jsonPath("$.dividends.rows[0].withholding").value(10.0000))
                .andExpect(jsonPath("$.dividends.rows[0].net").value(90.0000))
                .andExpect(jsonPath("$.dividends.total.net").value(90.0000))
                // interest: single May bucket, 500 charged, 475 deductible
                .andExpect(jsonPath("$.interest.months", hasSize(1)))
                .andExpect(jsonPath("$.interest.months[0].month").value("May"))
                .andExpect(jsonPath("$.interest.months[0].charged").value(500.0000))
                .andExpect(jsonPath("$.interest.months[0].deductibleEstimate").value(475.0000))
                .andExpect(jsonPath("$.interest.ytd.charged").value(500.0000))
                .andExpect(jsonPath("$.interest.ytd.deductibleEstimate").value(475.0000));
    }

    @Test
    void taxSummaryForExplicitYearScopesToThatYear() throws Exception {
        var margin = createAccount("Test Margin", "Margin");
        var xei = requireSecurity("TSE:XEI");

        createBuy(margin, xei, "2025-02-01", 100, "20.00");
        createSell(margin, xei, "2025-06-01", 50, "30.00");
        createDividend(xei, "2024-03-15", "40.00", "0");

        mockMvc.perform(get("/api/v1/portfolios/{id}/tax-summary", portfolioId())
                        .param("year", "2024"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.year").value(2024))
                .andExpect(jsonPath("$.realizedGains.rows", hasSize(0)))
                .andExpect(jsonPath("$.realizedGains.total.gainLoss").value(0.0000))
                .andExpect(jsonPath("$.dividends.rows", hasSize(1)))
                .andExpect(jsonPath("$.dividends.rows[0].net").value(40.0000))
                .andExpect(jsonPath("$.interest.months", hasSize(0)));
    }

    private Account createAccount(String label, String type) {
        var account = new Account();
        account.setPortfolio(defaultPortfolio());
        account.setLabel(label);
        account.setType(type);
        account.setCurrency("CAD");
        account.setOpeningBalance(BigDecimal.ZERO);
        return accountRepository.save(account);
    }

    private SecurityTransaction createBuy(Account account, Security security, String date, long shares, String price) {
        return saveTrade(account, security, date, Action.BUY, shares, price);
    }

    private SecurityTransaction createSell(Account account, Security security, String date, long shares, String price) {
        return saveTrade(account, security, date, Action.SELL, shares, price);
    }

    private SecurityTransaction saveTrade(
            Account account,
            Security security,
            String date,
            Action action,
            long shares,
            String price
    ) {
        var transaction = new SecurityTransaction();
        transaction.setSecurity(security);
        transaction.setAccount(account);
        transaction.setDate(LocalDate.parse(date));
        transaction.setAction(action);
        transaction.setShares(BigDecimal.valueOf(shares));
        transaction.setPricePerShare(new BigDecimal(price));
        transaction.setCommission(BigDecimal.ZERO);
        return securityTransactionRepository.save(transaction);
    }

    private void createDividend(Security security, String date, String gross, String withholding) {
        var dividend = new Dividend();
        dividend.setPortfolio(defaultPortfolio());
        dividend.setSecurity(security);
        dividend.setPaymentDate(LocalDate.parse(date));
        dividend.setGrossAmount(new BigDecimal(gross));
        dividend.setWithholdingTax(new BigDecimal(withholding));
        dividend.setCurrency("CAD");
        dividend.setDrip(false);
        dividendRepository.save(dividend);
    }

    private CashTransaction createCash(
            Account account,
            CashTransactionType type,
            String date,
            String signedAmount,
            CashPurpose purpose
    ) {
        var cash = new CashTransaction();
        cash.setAccount(account);
        cash.setType(type);
        cash.setDate(LocalDate.parse(date));
        cash.setAmount(new BigDecimal(signedAmount));
        cash.setPurpose(purpose);
        return cashTransactionRepository.save(cash);
    }

    private org.springframework.test.web.servlet.ResultActions createFlow(
            Long helocAccountId,
            String label,
            String investmentUseAmount,
            List<Long> cashTransactionIds,
            Long securityTransactionId
    ) throws Exception {
        var body = new LinkedHashMap<String, Object>();
        body.put("portfolioId", portfolioId());
        body.put("helocAccountId", helocAccountId);
        body.put("label", label);
        body.put("investmentUseAmount", investmentUseAmount);
        body.put("cashTransactionIds", cashTransactionIds);
        body.put("securityTransactionId", securityTransactionId);
        return mockMvc.perform(post("/api/v1/smith-maneuver-flows")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)));
    }

    private Security requireSecurity(String ticker) {
        return securityRepository.findAllByOrderByTickerAsc().stream()
                .filter(s -> ticker.equals(s.getTicker()))
                .findFirst()
                .orElseThrow();
    }
}
