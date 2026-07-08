package com.investmenttracker.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.investmenttracker.domain.Account;
import com.investmenttracker.domain.Action;
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
import java.util.Map;

import static org.hamcrest.Matchers.hasItem;
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
class CashTransactionControllerTest {

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

    private Account createAccount(String label, String type) {
        var account = new Account();
        account.setPortfolio(defaultPortfolio());
        account.setLabel(label);
        account.setType(type);
        account.setCurrency("CAD");
        account.setOpeningBalance(BigDecimal.ZERO);
        return accountRepository.save(account);
    }

    @Test
    void createSingleLegDepositIsPositive() throws Exception {
        var chequing = createAccount("TD Chequing", "Chequing");
        var body = depositBody(chequing.getId(), "DEPOSIT", "2026-02-01", "2400.00");

        mockMvc.perform(post("/api/v1/cash-transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.type").value("DEPOSIT"))
                .andExpect(jsonPath("$.amount").value(2400.0000))
                .andExpect(jsonPath("$.counterpartyAccountId").doesNotExist())
                .andExpect(jsonPath("$.transferGroupId").doesNotExist());
    }

    @Test
    void withdrawalIsStoredNegative() throws Exception {
        var chequing = createAccount("TD Chequing", "Chequing");
        var body = depositBody(chequing.getId(), "WITHDRAWAL", "2026-02-02", "100.00");

        mockMvc.perform(post("/api/v1/cash-transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.amount").value(-100.0000));
    }

    @Test
    void createTransferPersistsTwoLegsWithSharedGroupAndOppositeSigns() throws Exception {
        var chequing = createAccount("TD Chequing", "Chequing");
        var margin = createAccount("Questrade Margin", "Margin");
        var body = transferBody(chequing.getId(), margin.getId(), "TRANSFER", "2026-03-16", "15600.00");

        var created = mockMvc.perform(post("/api/v1/cash-transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.amount").value(-15600.0000))
                .andExpect(jsonPath("$.counterpartyAccountId").value(margin.getId()))
                .andExpect(jsonPath("$.transferGroupId").isNotEmpty())
                .andReturn().getResponse().getContentAsString();

        var groupId = objectMapper.readTree(created).get("transferGroupId").asText();
        var legs = cashTransactionRepository.findByTransferGroupId(groupId);
        assert legs.size() == 2;

        mockMvc.perform(get("/api/v1/cash-transactions").param("portfolioId", String.valueOf(portfolioId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)));
    }

    @Test
    void transferWithoutCounterpartyIsRejected() throws Exception {
        var chequing = createAccount("TD Chequing", "Chequing");
        var body = depositBody(chequing.getId(), "TRANSFER", "2026-03-16", "100.00");

        mockMvc.perform(post("/api/v1/cash-transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.counterpartyAccountId")
                        .value("Counterparty account is required for this type"));
    }

    @Test
    void transferWithEqualCounterpartyIsRejected() throws Exception {
        var chequing = createAccount("TD Chequing", "Chequing");
        var body = transferBody(chequing.getId(), chequing.getId(), "TRANSFER", "2026-03-16", "100.00");

        mockMvc.perform(post("/api/v1/cash-transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.counterpartyAccountId")
                        .value("Counterparty must differ from the account"));
    }

    @Test
    void nonTransferWithCounterpartyIsRejected() throws Exception {
        var chequing = createAccount("TD Chequing", "Chequing");
        var margin = createAccount("Questrade Margin", "Margin");
        var body = transferBody(chequing.getId(), margin.getId(), "DEPOSIT", "2026-03-16", "100.00");

        mockMvc.perform(post("/api/v1/cash-transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.counterpartyAccountId")
                        .value("Counterparty is only allowed for transfer types"));
    }

    @Test
    void zeroAmountIsRejected() throws Exception {
        var chequing = createAccount("TD Chequing", "Chequing");
        var body = depositBody(chequing.getId(), "DEPOSIT", "2026-03-16", "0");

        mockMvc.perform(post("/api/v1/cash-transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.amount").value("Amount must be greater than zero"));
    }

    @Test
    void updateUnknownIdReturns404() throws Exception {
        var chequing = createAccount("TD Chequing", "Chequing");
        var body = depositBody(chequing.getId(), "DEPOSIT", "2026-03-16", "10.00");

        mockMvc.perform(put("/api/v1/cash-transactions/{id}", 999_999L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isNotFound());
    }

    @Test
    void deleteTransferRemovesBothLegs() throws Exception {
        var chequing = createAccount("TD Chequing", "Chequing");
        var margin = createAccount("Questrade Margin", "Margin");
        var body = transferBody(chequing.getId(), margin.getId(), "TRANSFER", "2026-03-16", "15600.00");

        var created = mockMvc.perform(post("/api/v1/cash-transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long id = objectMapper.readTree(created).get("id").asLong();

        mockMvc.perform(delete("/api/v1/cash-transactions/{id}", id))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/cash-transactions").param("portfolioId", String.valueOf(portfolioId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void listStampsPerAccountRunningBalance() throws Exception {
        var chequing = createAccount("Running Chequing", "Chequing");
        postCash(depositBody(chequing.getId(), "DEPOSIT", "2026-02-01", "2400.00"));
        postCash(depositBody(chequing.getId(), "WITHDRAWAL", "2026-02-05", "100.00"));

        mockMvc.perform(get("/api/v1/cash-transactions").param("portfolioId", String.valueOf(portfolioId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath(
                        "$[?(@.accountId == " + chequing.getId() + " && @.type == 'DEPOSIT')].balanceAfter",
                        hasItem(2400.0)))
                .andExpect(jsonPath(
                        "$[?(@.accountId == " + chequing.getId() + " && @.type == 'WITHDRAWAL')].balanceAfter",
                        hasItem(2300.0)));
    }

    @Test
    void transferLegsCarryPerAccountBalances() throws Exception {
        var source = createAccount("Ledger Source", "Chequing");
        var dest = createAccount("Ledger Dest", "Other");
        postCash(transferBody(source.getId(), dest.getId(), "TRANSFER", "2026-03-16", "15600.00"));

        mockMvc.perform(get("/api/v1/cash-transactions").param("portfolioId", String.valueOf(portfolioId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.accountId == " + source.getId() + ")].balanceAfter", hasItem(-15600.0)))
                .andExpect(jsonPath("$[?(@.accountId == " + dest.getId() + ")].balanceAfter", hasItem(15600.0)));
    }

    @Test
    void ledgerIncludesTradeRowsWithRunningBalance() throws Exception {
        var chequing = createAccount("Trade Ledger", "Chequing");
        postCash(depositBody(chequing.getId(), "DEPOSIT", "2026-02-01", "1000.00"));
        var security = securityRepository.findAllByOrderByTickerAsc().getFirst();
        saveBuy(security, chequing, "2026-02-02", "10", "9.00", "0");

        mockMvc.perform(get("/api/v1/cash-transactions").param("portfolioId", String.valueOf(portfolioId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.source == 'TRADE')].amount", hasItem(-90.0)))
                .andExpect(jsonPath("$[?(@.source == 'TRADE')].tradeAction", hasItem("BUY")))
                .andExpect(jsonPath("$[?(@.source == 'TRADE')].balanceAfter", hasItem(910.0)));
    }

    @Test
    void ledgerIncludesNonDripDividendAsCreditButExcludesDrip() throws Exception {
        var chequing = createAccount("Dividend Ledger", "Chequing");
        postCash(depositBody(chequing.getId(), "DEPOSIT", "2026-02-01", "1000.00"));
        var security = securityRepository.findAllByOrderByTickerAsc().getFirst();
        postDividend(dividendBody(security.getId(), chequing.getId(), "2026-02-03", "100.00", "10.00", false));
        postDividend(dividendBody(security.getId(), chequing.getId(), "2026-02-04", "50.00", "0", true));

        mockMvc.perform(get("/api/v1/cash-transactions").param("portfolioId", String.valueOf(portfolioId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.source == 'DIVIDEND')]", hasSize(1)))
                .andExpect(jsonPath("$[?(@.source == 'DIVIDEND')].amount", hasItem(90.0)))
                .andExpect(jsonPath("$[?(@.source == 'DIVIDEND')].balanceAfter", hasItem(1090.0)))
                .andExpect(jsonPath("$[?(@.source == 'DIVIDEND')].dividendId").exists());
    }

    private void postDividend(Map<String, Object> body) throws Exception {
        mockMvc.perform(post("/api/v1/dividends")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated());
    }

    private Map<String, Object> dividendBody(
            Long securityId,
            Long accountId,
            String date,
            String gross,
            String withholding,
            boolean drip
    ) {
        var body = new LinkedHashMap<String, Object>();
        body.put("portfolioId", portfolioId());
        body.put("securityId", securityId);
        body.put("accountId", accountId);
        body.put("paymentDate", date);
        body.put("grossAmount", gross);
        body.put("withholdingTax", withholding);
        body.put("drip", drip);
        return body;
    }

    private void saveBuy(
            Security security,
            Account account,
            String date,
            String shares,
            String price,
            String commission
    ) {
        var transaction = new SecurityTransaction();
        transaction.setSecurity(security);
        transaction.setAccount(account);
        transaction.setDate(LocalDate.parse(date));
        transaction.setAction(Action.BUY);
        transaction.setShares(new BigDecimal(shares));
        transaction.setPricePerShare(new BigDecimal(price));
        transaction.setCommission(new BigDecimal(commission));
        securityTransactionRepository.save(transaction);
    }

    private void postCash(Map<String, Object> body) throws Exception {
        mockMvc.perform(post("/api/v1/cash-transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated());
    }

    private static Map<String, Object> depositBody(Long accountId, String type, String date, String amount) {
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
        var body = depositBody(accountId, type, date, amount);
        body.put("counterpartyAccountId", counterpartyAccountId);
        return body;
    }
}
