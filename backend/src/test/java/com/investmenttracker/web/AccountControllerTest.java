package com.investmenttracker.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.investmenttracker.domain.Account;
import com.investmenttracker.domain.Action;
import com.investmenttracker.domain.Portfolio;
import com.investmenttracker.domain.Security;
import com.investmenttracker.domain.SecurityTransaction;
import com.investmenttracker.repository.AccountRepository;
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
import java.util.Map;

import static org.hamcrest.Matchers.hasItem;
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
class AccountControllerTest {

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

    @Test
    void createPersistsAccountWithDefaults() throws Exception {
        var portfolio = defaultPortfolio();
        var body = Map.of(
                "portfolioId", portfolio.getId(),
                "label", "TD Chequing",
                "type", "Chequing"
        );

        mockMvc.perform(post("/api/v1/accounts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.portfolioId").value(portfolio.getId()))
                .andExpect(jsonPath("$.label").value("TD Chequing"))
                .andExpect(jsonPath("$.type").value("Chequing"))
                .andExpect(jsonPath("$.currency").value("CAD"))
                .andExpect(jsonPath("$.openingBalance").value(0))
                .andExpect(jsonPath("$.currentBalance").value(0))
                .andExpect(jsonPath("$.institution").doesNotExist())
                .andExpect(jsonPath("$.creditLimit").doesNotExist())
                .andExpect(jsonPath("$.interestRate").doesNotExist());
    }

    @Test
    void createPersistsHelocFields() throws Exception {
        var portfolio = defaultPortfolio();
        var body = Map.of(
                "portfolioId", portfolio.getId(),
                "label", "TD HELOC",
                "type", "HELOC",
                "institution", "TD Canada Trust",
                "openingBalance", 0,
                "openingBalanceDate", "2025-01-01",
                "creditLimit", 150000,
                "interestRate", 6.45
        );

        mockMvc.perform(post("/api/v1/accounts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.type").value("HELOC"))
                .andExpect(jsonPath("$.institution").value("TD Canada Trust"))
                .andExpect(jsonPath("$.openingBalanceDate").value("2025-01-01"))
                .andExpect(jsonPath("$.creditLimit").value(150000))
                .andExpect(jsonPath("$.interestRate").value(6.45))
                .andExpect(jsonPath("$.currentBalance").value(0));
    }

    @Test
    void createRejectsMissingPortfolio() throws Exception {
        var body = Map.of(
                "portfolioId", 999_999,
                "label", "Orphan",
                "type", "Other"
        );

        mockMvc.perform(post("/api/v1/accounts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.portfolioId").value("Portfolio not found"));
    }

    @Test
    void listScopedToPortfolio() throws Exception {
        var portfolio = defaultPortfolio();
        createAccount(portfolio, "Scoped Account", "Margin");

        mockMvc.perform(get("/api/v1/accounts").param("portfolioId", portfolio.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.label == 'Scoped Account')]").exists())
                .andExpect(jsonPath("$[0].currentBalance").isNumber());
    }

    @Test
    void updateChangesFields() throws Exception {
        var portfolio = defaultPortfolio();
        var account = createAccount(portfolio, "Before", "Margin");
        var body = Map.of(
                "label", "Questrade Margin",
                "type", "Margin",
                "institution", "Questrade",
                "currency", "CAD",
                "openingBalance", 10000,
                "openingBalanceDate", "2024-03-01"
        );

        mockMvc.perform(put("/api/v1/accounts/{id}", account.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.label").value("Questrade Margin"))
                .andExpect(jsonPath("$.institution").value("Questrade"))
                .andExpect(jsonPath("$.openingBalance").value(10000))
                .andExpect(jsonPath("$.currentBalance").value(10000));
    }

    @Test
    void updateClearsHelocFieldsWhenTypeChanges() throws Exception {
        var portfolio = defaultPortfolio();
        var account = createHelocAccount(portfolio);
        var body = Map.of(
                "label", "TD Chequing",
                "type", "Chequing",
                "openingBalance", 3500
        );

        mockMvc.perform(put("/api/v1/accounts/{id}", account.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type").value("Chequing"))
                .andExpect(jsonPath("$.creditLimit").doesNotExist())
                .andExpect(jsonPath("$.interestRate").doesNotExist());
    }

    @Test
    void currentBalanceReflectsDepositsAndWithdrawals() throws Exception {
        var portfolio = defaultPortfolio();
        var chequing = createAccount(portfolio, "Balance Chequing", "Chequing");
        postCash(cashBody(chequing.getId(), "DEPOSIT", "2026-02-01", "2400.00"));
        postCash(cashBody(chequing.getId(), "WITHDRAWAL", "2026-02-05", "400.00"));

        mockMvc.perform(get("/api/v1/accounts").param("portfolioId", portfolio.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == " + chequing.getId() + ")].currentBalance", hasItem(2000.0)));
    }

    @Test
    void transferMovesBalanceBetweenAccounts() throws Exception {
        var portfolio = defaultPortfolio();
        var source = createAccount(portfolio, "Transfer Source", "Chequing");
        var dest = createAccount(portfolio, "Transfer Dest", "Other");
        postCash(transferBody(source.getId(), dest.getId(), "TRANSFER", "2026-03-16", "15600.00"));

        mockMvc.perform(get("/api/v1/accounts").param("portfolioId", portfolio.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == " + source.getId() + ")].currentBalance", hasItem(-15600.0)))
                .andExpect(jsonPath("$[?(@.id == " + dest.getId() + ")].currentBalance", hasItem(15600.0)));
    }

    @Test
    void helocDrawShowsPositiveOwedBalance() throws Exception {
        var portfolio = defaultPortfolio();
        var heloc = createHelocAccount(portfolio);
        var chequing = createAccount(portfolio, "Draw Target", "Chequing");
        postCash(transferBody(heloc.getId(), chequing.getId(), "HELOC_DRAW", "2026-03-01", "15600.00"));

        mockMvc.perform(get("/api/v1/accounts").param("portfolioId", portfolio.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == " + heloc.getId() + ")].currentBalance", hasItem(15600.0)))
                .andExpect(jsonPath("$[?(@.id == " + chequing.getId() + ")].currentBalance", hasItem(15600.0)));
    }

    @Test
    void currentBalanceReflectsTradeCash() throws Exception {
        var portfolio = defaultPortfolio();
        var account = createAccount(portfolio, "Trade Cash Account", "Chequing");
        var security = securityRepository.findAllByOrderByTickerAsc().getFirst();
        postCash(cashBody(account.getId(), "DEPOSIT", "2026-02-01", "1000.00"));
        saveTrade(security, account, Action.BUY, "10", "20.00", "5.00");
        saveTrade(security, account, Action.SELL, "5", "30.00", "5.00");

        mockMvc.perform(get("/api/v1/accounts").param("portfolioId", portfolio.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == " + account.getId() + ")].currentBalance", hasItem(940.0)));
    }

    @Test
    void deleteUnusedAccountSucceeds() throws Exception {
        var portfolio = defaultPortfolio();
        var account = createAccount(portfolio, "Disposable", "Other");

        mockMvc.perform(delete("/api/v1/accounts/{id}", account.getId()))
                .andExpect(status().isNoContent());
    }

    @Test
    void deleteAccountWithTransactionsIsRejected() throws Exception {
        var account = accountRepository.findAllByOrderByLabelAsc().getFirst();
        var security = securityRepository.findAllByOrderByTickerAsc().getFirst();
        saveBuy(security, account);

        mockMvc.perform(delete("/api/v1/accounts/{id}", account.getId()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.account")
                        .value("Cannot delete an account that has security transactions"));
    }

    private Portfolio defaultPortfolio() {
        return portfolioRepository.findAllByOrderByNameAsc().getFirst();
    }

    private Account createAccount(Portfolio portfolio, String label, String type) {
        var account = new Account();
        account.setPortfolio(portfolio);
        account.setLabel(label);
        account.setType(type);
        account.setCurrency("CAD");
        account.setOpeningBalance(BigDecimal.ZERO);
        return accountRepository.save(account);
    }

    private Account createHelocAccount(Portfolio portfolio) {
        var account = new Account();
        account.setPortfolio(portfolio);
        account.setLabel("TD HELOC");
        account.setType("HELOC");
        account.setCurrency("CAD");
        account.setOpeningBalance(BigDecimal.ZERO);
        account.setCreditLimit(new BigDecimal("150000"));
        account.setInterestRate(new BigDecimal("6.45"));
        return accountRepository.save(account);
    }

    private void postCash(Map<String, Object> body) throws Exception {
        mockMvc.perform(post("/api/v1/cash-transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated());
    }

    private static Map<String, Object> cashBody(Long accountId, String type, String date, String amount) {
        var body = new LinkedHashMap<String, Object>();
        body.put("accountId", accountId);
        body.put("type", type);
        body.put("date", date);
        body.put("amount", amount);
        return body;
    }

    private static Map<String, Object> transferBody(
            Long accountId,
            Long counterpartyAccountId,
            String type,
            String date,
            String amount
    ) {
        var body = cashBody(accountId, type, date, amount);
        body.put("counterpartyAccountId", counterpartyAccountId);
        return body;
    }

    private void saveBuy(Security security, Account account) {
        var transaction = new SecurityTransaction();
        transaction.setSecurity(security);
        transaction.setAccount(account);
        transaction.setDate(LocalDate.of(2024, 1, 1));
        transaction.setAction(Action.BUY);
        transaction.setShares(BigDecimal.valueOf(10));
        transaction.setPricePerShare(new BigDecimal("20.00"));
        transaction.setCommission(BigDecimal.ZERO);
        securityTransactionRepository.save(transaction);
    }

    private void saveTrade(
            Security security,
            Account account,
            Action action,
            String shares,
            String price,
            String commission
    ) {
        var transaction = new SecurityTransaction();
        transaction.setSecurity(security);
        transaction.setAccount(account);
        transaction.setDate(LocalDate.of(2026, 2, 2));
        transaction.setAction(action);
        transaction.setShares(new BigDecimal(shares));
        transaction.setPricePerShare(new BigDecimal(price));
        transaction.setCommission(new BigDecimal(commission));
        securityTransactionRepository.save(transaction);
    }
}
