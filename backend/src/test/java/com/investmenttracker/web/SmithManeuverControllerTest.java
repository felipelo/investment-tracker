package com.investmenttracker.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.investmenttracker.domain.Account;
import com.investmenttracker.domain.Action;
import com.investmenttracker.domain.CashPurpose;
import com.investmenttracker.domain.CashTransaction;
import com.investmenttracker.domain.CashTransactionType;
import com.investmenttracker.domain.Portfolio;
import com.investmenttracker.domain.Security;
import com.investmenttracker.domain.SecurityTransaction;
import com.investmenttracker.repository.AccountRepository;
import com.investmenttracker.repository.CashTransactionRepository;
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
class SmithManeuverControllerTest {

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

    private Portfolio defaultPortfolio() {
        return portfolioRepository.findAllByOrderByNameAsc().getFirst();
    }

    private Long portfolioId() {
        return defaultPortfolio().getId();
    }

    @Test
    void tracedFlowDrivesInvestmentUseBalanceAndDeductibleEstimate() throws Exception {
        var heloc = createAccount("TD HELOC", "HELOC");
        var margin = createAccount("Questrade Margin", "Margin");

        // 9,500 investment draw + 500 interest => owed 10,000, traced fraction 0.95.
        var draw = createCash(heloc, CashTransactionType.HELOC_DRAW, "2026-05-01", "-9500.00", CashPurpose.INVESTMENT);
        createCash(heloc, CashTransactionType.INTEREST_CHARGE, "2026-05-31", "-500.00", null);
        var buy = createBuy(margin, "2026-05-01", 500, "31.20");

        createFlow(heloc.getId(), "Flow #2024-03", "9500.00", List.of(draw.getId()), buy.getId())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("TRACED"))
                .andExpect(jsonPath("$.steps", hasSize(2)))
                .andExpect(jsonPath("$.steps[0].stepLabel").value("HELOC Draw"))
                .andExpect(jsonPath("$.steps[1].kind").value("SECURITY"))
                .andExpect(jsonPath("$.steps[1].ticker").value("TSE:XEI"));

        mockMvc.perform(get("/api/v1/portfolios/{id}/smith-maneuver", portfolioId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.investmentUseBalance").value(9500.0000))
                .andExpect(jsonPath("$.flows", hasSize(1)))
                .andExpect(jsonPath("$.flows[0].status").value("TRACED"))
                .andExpect(jsonPath("$.helocAccounts", hasSize(1)))
                .andExpect(jsonPath("$.helocAccounts[0].balance").value(10000.0000))
                .andExpect(jsonPath("$.helocAccounts[0].tracedPct").value(95.00))
                .andExpect(jsonPath("$.helocAccounts[0].status").value("Mostly traced"))
                .andExpect(jsonPath("$.interestLog", hasSize(1)))
                // 500 * (9500 / 10000) = 475
                .andExpect(jsonPath("$.interestLog[0].deductibleEstimate").value(475.0000));
    }

    @Test
    void drawWithoutBuyIsPartiallyTracedAndRaisesWarning() throws Exception {
        var heloc = createAccount("TD HELOC", "HELOC");
        var draw = createCash(heloc, CashTransactionType.HELOC_DRAW, "2026-05-01", "-8000.00", CashPurpose.INVESTMENT);

        createFlow(heloc.getId(), "Pending deployment", "8000.00", List.of(draw.getId()), null)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PARTIALLY_TRACED"));

        mockMvc.perform(get("/api/v1/portfolios/{id}/smith-maneuver", portfolioId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.warnings", hasSize(1)))
                .andExpect(jsonPath("$.warnings[0].title").value("Partially traced flow — Pending deployment"));
    }

    @Test
    void nonHelocSourceIsRejected() throws Exception {
        var chequing = createAccount("TD Chequing", "Chequing");
        var draw = createCash(chequing, CashTransactionType.DEPOSIT, "2026-05-01", "100.00", CashPurpose.INVESTMENT);

        createFlow(chequing.getId(), "Bad", "100.00", List.of(draw.getId()), null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.helocAccountId").value("Source account must be a HELOC account"));
    }

    @Test
    void emptyChainIsRejected() throws Exception {
        var heloc = createAccount("TD HELOC", "HELOC");

        createFlow(heloc.getId(), "Empty", "100.00", List.of(), null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.cashTransactionIds")
                        .value("At least one cash transaction (the HELOC draw) is required"));
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

    private Account createAccount(String label, String type) {
        var account = new Account();
        account.setPortfolio(defaultPortfolio());
        account.setLabel(label);
        account.setType(type);
        account.setCurrency("CAD");
        account.setOpeningBalance(BigDecimal.ZERO);
        return accountRepository.save(account);
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

    private SecurityTransaction createBuy(Account account, String date, long shares, String price) {
        var security = requireSecurity("TSE:XEI");
        var transaction = new SecurityTransaction();
        transaction.setSecurity(security);
        transaction.setAccount(account);
        transaction.setDate(LocalDate.parse(date));
        transaction.setAction(Action.BUY);
        transaction.setShares(BigDecimal.valueOf(shares));
        transaction.setPricePerShare(new BigDecimal(price));
        transaction.setCommission(BigDecimal.ZERO);
        return securityTransactionRepository.save(transaction);
    }

    private Security requireSecurity(String ticker) {
        return securityRepository.findAllByOrderByTickerAsc().stream()
                .filter(s -> ticker.equals(s.getTicker()))
                .findFirst()
                .orElseThrow();
    }
}
