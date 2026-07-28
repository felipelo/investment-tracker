package com.investmenttracker.web;

import com.investmenttracker.domain.Account;
import com.investmenttracker.domain.CashTransaction;
import com.investmenttracker.domain.CashTransactionType;
import com.investmenttracker.domain.Dividend;
import com.investmenttracker.domain.Portfolio;
import com.investmenttracker.domain.Security;
import com.investmenttracker.repository.AccountRepository;
import com.investmenttracker.repository.CashTransactionRepository;
import com.investmenttracker.repository.DividendRepository;
import com.investmenttracker.repository.PortfolioRepository;
import com.investmenttracker.repository.SecurityRepository;
import com.investmenttracker.web.dto.CashFlowOutlookResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.Comparator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The projection reads the real clock, so every seeded date is expressed relative to today rather
 * than as a literal, and the assertions lean on invariants that hold whatever today happens to be.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class CashFlowOutlookControllerTest {

    private static final LocalDate TODAY = LocalDate.now();
    /** Anchor plus one month lands ~30 days out (inside the horizon); plus two months never does. */
    private static final LocalDate LAST_DIVIDEND = TODAY.minusDays(1);
    private static final int BASELINE_DAY = 10;

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
    private DividendRepository dividendRepository;

    @Autowired
    private SecurityRepository securityRepository;

    @Test
    void monthlyHistoryProjectsOnePaymentAtTheMostRecentNetAmount() throws Exception {
        var xei = requireSecurity("TSE:XEI");
        createDividend(xei, TODAY.minusDays(61), "100.00", "0.00");
        createDividend(xei, TODAY.minusDays(31), "110.00", "0.00");
        createDividend(xei, LAST_DIVIDEND, "130.00", "9.60");

        var outlook = outlook();

        assertThat(outlook.events()).singleElement().satisfies(event -> {
            assertThat(event.date()).isEqualTo(LAST_DIVIDEND.plusMonths(1));
            assertThat(event.label()).isEqualTo("TSE:XEI dividend");
            assertThat(event.category()).isEqualTo("DIVIDEND");
            assertThat(event.amount()).isEqualByComparingTo("120.40");
        });
        assertThat(outlook.moneyIn()).isEqualByComparingTo("120.40");
        assertThat(outlook.warnings()).isEmpty();
    }

    @Test
    void feeAndInterestRepeatOnTheBaselineDayOfMonth() throws Exception {
        var heloc = createAccount("TD HELOC", "HELOC");
        createCash(heloc, CashTransactionType.INTEREST_CHARGE, baselineDate(BASELINE_DAY), "244.00");
        createCash(heloc, CashTransactionType.FEE, baselineDate(BASELINE_DAY), "3.95");

        var outlook = outlook();

        assertThat(outlook.events()).isNotEmpty().allSatisfy(event ->
                assertThat(event.date().getDayOfMonth()).isEqualTo(BASELINE_DAY));
        assertThat(outlook.events()).anySatisfy(event -> {
            assertThat(event.label()).isEqualTo("TD HELOC interest");
            assertThat(event.category()).isEqualTo("INTEREST");
            assertThat(event.amount()).isEqualByComparingTo("-244.00");
        });
        assertThat(outlook.events()).anySatisfy(event -> {
            assertThat(event.label()).isEqualTo("TD HELOC fee");
            assertThat(event.category()).isEqualTo("FEE");
            assertThat(event.amount()).isEqualByComparingTo("-3.95");
        });
        assertThat(outlook.moneyIn()).isEqualByComparingTo("0");
        assertThat(outlook.net()).isEqualByComparingTo(outlook.moneyOut().negate());
    }

    @Test
    void interestPaymentAlongsideAChargeDoesNotDoubleTheOutflow() throws Exception {
        var heloc = createAccount("TD HELOC", "HELOC");
        createCash(heloc, CashTransactionType.INTEREST_CHARGE, baselineDate(BASELINE_DAY), "244.00");
        var chargeOnly = outlook().moneyOut();

        createCash(heloc, CashTransactionType.INTEREST_PAYMENT, baselineDate(BASELINE_DAY + 3), "244.00");

        assertThat(chargeOnly).isGreaterThan(BigDecimal.ZERO);
        assertThat(outlook().moneyOut()).isEqualByComparingTo(chargeOnly);
    }

    @Test
    void aSingleRecordedPaymentIsWarnedAboutAndLeftOut() throws Exception {
        createDividend(requireSecurity("TSE:ENB"), TODAY.minusDays(20), "142.20", "0.00");

        var outlook = outlook();

        assertThat(outlook.warnings())
                .containsExactly("TSE:ENB has only one recorded payment, so it is left out.");
        assertThat(outlook.events()).isEmpty();
        assertThat(outlook.moneyIn()).isEqualByComparingTo("0");
    }

    @Test
    void withdrawalsAreNeverAnOutflow() throws Exception {
        var chequing = createAccount("Chequing", "Chequing");
        createCash(chequing, CashTransactionType.WITHDRAWAL, baselineDate(BASELINE_DAY), "247.95");

        assertThat(outlook().moneyOut()).isEqualByComparingTo("0");
        assertThat(outlook().events()).isEmpty();
    }

    @Test
    void twoFeesOnOneDayProjectAsOneCharge() throws Exception {
        var heloc = createAccount("TD HELOC", "HELOC");
        createCash(heloc, CashTransactionType.FEE, baselineDate(BASELINE_DAY), "3.95");
        createCash(heloc, CashTransactionType.FEE, baselineDate(BASELINE_DAY), "0.01");

        var outlook = outlook();

        assertThat(outlook.events()).isNotEmpty().allSatisfy(event ->
                assertThat(event.amount()).isEqualByComparingTo("-3.96"));
        assertThat(outlook.events().stream().map(CashFlowOutlookResponse.CashFlowEvent::date).distinct())
                .hasSameSizeAs(outlook.events());
    }

    /**
     * Money in and money out on one day settle that day, so the dip between them is an artifact of
     * the sort order rather than a shortfall the user would ever see.
     */
    @Test
    void theTightestPointIsMeasuredAtTheEndOfADayNotBetweenTwoEventsOnIt() throws Exception {
        // The 15th of next month, reachable by a monthly step from the 15th of last month, so a
        // dividend and an interest charge anchored there both project onto the same date.
        var anchor = TODAY.minusMonths(1).withDayOfMonth(15);
        var xei = requireSecurity("TSE:XEI");
        createDividend(xei, anchor.minusMonths(2), "100.00", "0.00");
        createDividend(xei, anchor.minusMonths(1), "100.00", "0.00");
        createDividend(xei, anchor, "100.00", "0.00");
        createCash(createAccount("TD HELOC", "HELOC"), CashTransactionType.INTEREST_CHARGE, anchor, "500.00");

        var outlook = outlook();
        var onLowestDay = outlook.events().stream()
                .filter(event -> event.date().equals(outlook.lowestOn()))
                .toList();

        assertThat(onLowestDay).hasSizeGreaterThan(1);
        assertThat(outlook.lowestRunningTotal())
                .isEqualByComparingTo(onLowestDay.getLast().runningTotal());
    }

    @Test
    void runningTotalOnTheFinalEventEqualsNet() throws Exception {
        var xei = requireSecurity("TSE:XEI");
        createDividend(xei, TODAY.minusDays(61), "100.00", "0.00");
        createDividend(xei, TODAY.minusDays(31), "110.00", "0.00");
        createDividend(xei, LAST_DIVIDEND, "130.00", "9.60");
        var heloc = createAccount("TD HELOC", "HELOC");
        createCash(heloc, CashTransactionType.INTEREST_CHARGE, baselineDate(BASELINE_DAY), "244.00");
        createCash(heloc, CashTransactionType.FEE, baselineDate(BASELINE_DAY), "3.95");

        var outlook = outlook();

        var firstWeekStart = TODAY.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        assertThat(outlook.from()).isEqualTo(TODAY);
        assertThat(outlook.to()).isEqualTo(firstWeekStart.plusWeeks(8).minusDays(1));
        assertThat(outlook.weeks()).hasSize(8);
        assertThat(outlook.weeks().getFirst().weekStart()).isEqualTo(firstWeekStart);

        assertThat(outlook.net()).isEqualByComparingTo(outlook.moneyIn().subtract(outlook.moneyOut()));
        assertThat(outlook.events().getLast().runningTotal()).isEqualByComparingTo(outlook.net());
        assertThat(outlook.weeks().getLast().runningTotal()).isEqualByComparingTo(outlook.net());
        assertThat(outlook.lowestRunningTotal())
                .isEqualByComparingTo(outlook.events().stream()
                        .map(CashFlowOutlookResponse.CashFlowEvent::runningTotal)
                        .min(BigDecimal::compareTo)
                        .orElseThrow());
        assertThat(outlook.events()).isSortedAccordingTo(
                Comparator.comparing(CashFlowOutlookResponse.CashFlowEvent::date));
    }

    @Test
    void thePortfolioScopeIsOptional() throws Exception {
        var heloc = createAccount("TD HELOC", "HELOC");
        createCash(heloc, CashTransactionType.FEE, baselineDate(BASELINE_DAY), "3.95");

        var unscoped = objectMapper.readValue(
                mockMvc.perform(get("/api/v1/cash-flow-outlook"))
                        .andExpect(status().isOk())
                        .andReturn().getResponse().getContentAsString(),
                CashFlowOutlookResponse.class
        );

        assertThat(unscoped.moneyOut()).isEqualByComparingTo(outlook().moneyOut());
    }

    @Test
    void unknownPortfolioIsNotFound() throws Exception {
        mockMvc.perform(get("/api/v1/cash-flow-outlook").param("portfolioId", "999999"))
                .andExpect(status().isNotFound());
    }

    private CashFlowOutlookResponse outlook() throws Exception {
        var body = mockMvc.perform(get("/api/v1/cash-flow-outlook").param("portfolioId", portfolioId().toString()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readValue(body, CashFlowOutlookResponse.class);
    }

    /** The given day of the previous month: always a real date, and always before today. */
    private static LocalDate baselineDate(int dayOfMonth) {
        return TODAY.minusMonths(1).withDayOfMonth(dayOfMonth);
    }

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

    private Security requireSecurity(String ticker) {
        return securityRepository.findAllByOrderByTickerAsc().stream()
                .filter(s -> ticker.equals(s.getTicker()))
                .findFirst()
                .orElseThrow();
    }

    /** Fees and interest are stored as negative amounts, matching CashTransactionService. */
    private CashTransaction createCash(
            Account account,
            CashTransactionType type,
            LocalDate date,
            String magnitude
    ) {
        var cash = new CashTransaction();
        cash.setAccount(account);
        cash.setType(type);
        cash.setDate(date);
        cash.setAmount(new BigDecimal(magnitude).negate());
        return cashTransactionRepository.save(cash);
    }

    private Dividend createDividend(Security security, LocalDate paymentDate, String gross, String withholding) {
        var dividend = new Dividend();
        dividend.setPortfolio(defaultPortfolio());
        dividend.setSecurity(security);
        dividend.setPaymentDate(paymentDate);
        dividend.setGrossAmount(new BigDecimal(gross));
        dividend.setWithholdingTax(new BigDecimal(withholding));
        dividend.setCurrency("CAD");
        dividend.setDrip(false);
        return dividendRepository.save(dividend);
    }
}
