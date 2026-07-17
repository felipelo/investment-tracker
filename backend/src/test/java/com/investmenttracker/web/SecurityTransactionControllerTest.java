package com.investmenttracker.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.investmenttracker.domain.Account;
import com.investmenttracker.domain.Portfolio;
import com.investmenttracker.domain.Security;
import com.investmenttracker.repository.AccountRepository;
import com.investmenttracker.repository.PortfolioRepository;
import com.investmenttracker.repository.SecurityRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
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

    @Autowired
    private PortfolioRepository portfolioRepository;

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

    @Test
    void listTransactionsFiltersByPortfolioAndComposesWithSecurityFilter() throws Exception {
        var xei = security("TSE:XEI");
        var bank = security("TSE:BANK");
        var defaultAccount = account();
        var otherPortfolio = createPortfolio("Other Portfolio");
        var otherAccount = createAccount(otherPortfolio, "Other Margin");

        createTrade(defaultAccount.getId(), xei.getId(), "2024-04-01");
        createTrade(otherAccount.getId(), xei.getId(), "2024-04-02");
        createTrade(otherAccount.getId(), bank.getId(), "2024-04-03");

        mockMvc.perform(get("/api/v1/security-transactions")
                        .param("portfolioId", String.valueOf(otherPortfolio.getId()))
                        .param("securityId", String.valueOf(xei.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].accountId").value(otherAccount.getId()))
                .andExpect(jsonPath("$[0].securityId").value(xei.getId()));
    }

    private void createTrade(Long accountId, Long securityId, String date) throws Exception {
        mockMvc.perform(post("/api/v1/security-transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                tradeBody(accountId, securityId, "BUY", date, "1", "10.00"))))
                .andExpect(status().isCreated());
    }

    private Portfolio createPortfolio(String name) {
        var portfolio = new Portfolio();
        portfolio.setName(name);
        portfolio.setBaseCurrency("CAD");
        return portfolioRepository.save(portfolio);
    }

    private Account createAccount(Portfolio portfolio, String label) {
        var newAccount = new Account();
        newAccount.setPortfolio(portfolio);
        newAccount.setLabel(label);
        newAccount.setType("Margin");
        newAccount.setCurrency("CAD");
        newAccount.setOpeningBalance(BigDecimal.ZERO);
        return accountRepository.save(newAccount);
    }

    private Map<String, Object> tradeBody(Long securityId, String action, String date, String shares, String price) {
        return tradeBody(account().getId(), securityId, action, date, shares, price);
    }

    private Map<String, Object> tradeBody(
            Long accountId,
            Long securityId,
            String action,
            String date,
            String shares,
            String price
    ) {
        var body = new LinkedHashMap<String, Object>();
        body.put("date", date);
        body.put("securityId", securityId);
        body.put("accountId", accountId);
        body.put("action", action);
        body.put("shares", shares);
        body.put("pricePerShare", price);
        body.put("commission", "0");
        return body;
    }
}
