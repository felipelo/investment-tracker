package com.investmenttracker.acb;

import com.investmenttracker.domain.Action;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AcbEngineTest {

    @Test
    void buyIncreasesSharesAndAcb() {
        var rows = AcbEngine.compute(List.of(
                txn(1, "2024-01-01", Action.BUY, 100, "25.00", "9.95")
        ));

        assertEquals(1, rows.size());
        var row = rows.getFirst();
        assertEquals(new BigDecimal("100.000000"), row.shareChange());
        assertEquals(new BigDecimal("100.000000"), row.shareBalance());
        assertEquals(new BigDecimal("2509.9500"), row.acbChange());
        assertEquals(new BigDecimal("2509.9500"), row.totalAcb());
        assertMoney("25.0995", row.acbPerShare());
        assertNull(row.proceeds());
        assertNull(row.capitalGainLoss());
    }

    @Test
    void sellComputesProceedsAndCapitalGain() {
        var rows = AcbEngine.compute(List.of(
                txn(1, "2024-01-01", Action.BUY, 100, "20.00", "0"),
                txn(2, "2024-06-01", Action.SELL, 40, "30.00", "5.00")
        ));

        var sell = rows.get(1);
        assertEquals(new BigDecimal("-40.000000"), sell.shareChange());
        assertEquals(new BigDecimal("60.000000"), sell.shareBalance());
        assertEquals(new BigDecimal("-800.0000"), sell.acbChange());
        assertEquals(new BigDecimal("1200.0000"), sell.totalAcb());
        assertEquals(new BigDecimal("1195.0000"), sell.proceeds());
        assertEquals(new BigDecimal("395.0000"), sell.capitalGainLoss());
    }

    @Test
    void returnOfCapitalReducesAcbWithoutChangingShares() {
        var rows = AcbEngine.compute(List.of(
                txn(1, "2024-01-01", Action.BUY, 500, "31.20", "10"),
                roc(2, "2024-11-02", 420)
        ));

        var roc = rows.get(1);
        assertEquals(zeroShares(), roc.shareChange());
        assertEquals(new BigDecimal("500.000000"), roc.shareBalance());
        assertEquals(new BigDecimal("-420.0000"), roc.acbChange());
        assertEquals(new BigDecimal("15190.0000"), roc.totalAcb());
        assertMoney("30.38", roc.acbPerShare());
    }

    @Test
    void reinvestedDistributionIncreasesAcbWithoutChangingShares() {
        var rows = AcbEngine.compute(List.of(
                txn(1, "2024-01-01", Action.BUY, 500, "31.20", "10"),
                roc(2, "2024-11-02", 420),
                reinv(3, "2025-06-10", 380)
        ));

        var reinv = rows.get(2);
        assertEquals(zeroShares(), reinv.shareChange());
        assertEquals(new BigDecimal("500.000000"), reinv.shareBalance());
        assertEquals(new BigDecimal("380.0000"), reinv.acbChange());
        assertEquals(new BigDecimal("15570.0000"), reinv.totalAcb());
        assertMoney("31.14", reinv.acbPerShare());
    }

    @Test
    void splitAdjustsSharesWithoutChangingTotalAcb() {
        var rows = AcbEngine.compute(List.of(
                txn(1, "2024-01-01", Action.BUY, 100, "10.00", "0"),
                split(2, "2024-03-01", "2")
        ));

        var split = rows.get(1);
        assertEquals(new BigDecimal("100.000000"), split.shareChange());
        assertEquals(new BigDecimal("200.000000"), split.shareBalance());
        assertEquals(zeroMoney(), split.acbChange());
        assertEquals(new BigDecimal("1000.0000"), split.totalAcb());
        assertMoney("5.00", split.acbPerShare());
    }

    @Test
    void threeToTwoSplitAdjustsSharesCorrectly() {
        var rows = AcbEngine.compute(List.of(
                txn(1, "2024-01-01", Action.BUY, 100, "15.00", "0"),
                split(2, "2024-03-01", "1.5")
        ));

        var split = rows.get(1);
        assertEquals(new BigDecimal("50.000000"), split.shareChange());
        assertEquals(new BigDecimal("150.000000"), split.shareBalance());
        assertEquals(new BigDecimal("1500.0000"), split.totalAcb());
        assertMoney("10.00", split.acbPerShare());
    }

    @Test
    void xeiMockSequenceMatchesHoldingsReference() {
        var rows = AcbEngine.compute(xeiTransactions());

        assertEquals(4, rows.size());

        var buy1 = rows.get(0);
        assertEquals(new BigDecimal("15610.0000"), buy1.acbChange());
        assertEquals(new BigDecimal("15610.0000"), buy1.totalAcb());
        assertMoney("31.22", buy1.acbPerShare());

        var roc = rows.get(1);
        assertEquals(new BigDecimal("15190.0000"), roc.totalAcb());
        assertMoney("30.38", roc.acbPerShare());

        var reinv = rows.get(2);
        assertEquals(new BigDecimal("15570.0000"), reinv.totalAcb());
        assertMoney("31.14", reinv.acbPerShare());

        var buy2 = rows.get(3);
        assertEquals(new BigDecimal("30069.0000"), buy2.acbChange());
        assertEquals(new BigDecimal("45639.0000"), buy2.totalAcb());
        assertEquals(new BigDecimal("1420.000000"), buy2.shareBalance());
        assertMoney("32.14", buy2.acbPerShare());

        var summary = AcbEngine.summarizeRows(rows);
        assertEquals(new BigDecimal("1420.000000"), summary.shareBalance());
        assertEquals(new BigDecimal("45639.0000"), summary.totalAcb());
        assertMoney("32.14", summary.acbPerShare());
        assertEquals(LocalDate.of(2025, 9, 20), summary.lastTransactionDate());
    }

    @Test
    void totalAcbFloorsAtZero() {
        var rows = AcbEngine.compute(List.of(
                txn(1, "2024-01-01", Action.BUY, 10, "5.00", "0"),
                roc(2, "2024-02-01", 100)
        ));

        assertEquals(zeroMoney(), rows.get(1).totalAcb());
        assertEquals(zeroAcbPerShare(), rows.get(1).acbPerShare());
    }

    @Test
    void deniedLossAdjustmentAddsBackToAcb() {
        var rows = AcbEngine.compute(List.of(
                SecurityTransactionInput.builder()
                        .id(1)
                        .date(LocalDate.of(2024, 1, 1))
                        .action(Action.BUY)
                        .shares(100)
                        .pricePerShare("20.00")
                        .commission("0")
                        .build(),
                SecurityTransactionInput.builder()
                        .id(2)
                        .date(LocalDate.of(2024, 2, 1))
                        .action(Action.SELL)
                        .shares(50)
                        .pricePerShare("15.00")
                        .commission("0")
                        .deniedLossAdjustment(new BigDecimal("250.00"))
                        .build()
        ));

        var sell = rows.get(1);
        assertEquals(new BigDecimal("-1000.0000"), sell.acbChange());
        assertEquals(new BigDecimal("250.0000"), sell.deniedLossAdjustment());
        assertEquals(new BigDecimal("1250.0000"), sell.totalAcb());
    }

    @Test
    void flagsSuperficialLossWhenBuyPrecedesLossSellWithin30Days() {
        var rows = AcbEngine.compute(List.of(
                txn(1, "2024-01-01", Action.BUY, 100, "20.00", "0"),
                txn(2, "2024-01-20", Action.SELL, 50, "15.00", "0")
        ));

        assertTrue(rows.get(1).capitalGainLoss().compareTo(BigDecimal.ZERO) < 0);
        assertTrue(rows.get(1).superficialLossFlag());
    }

    @Test
    void flagsSuperficialLossWhenBuyFollowsLossSellWithin30Days() {
        var rows = AcbEngine.compute(List.of(
                txn(1, "2024-01-01", Action.BUY, 100, "20.00", "0"),
                txn(2, "2024-06-01", Action.SELL, 50, "15.00", "0"),
                txn(3, "2024-06-15", Action.BUY, 10, "16.00", "0")
        ));

        assertTrue(rows.get(1).superficialLossFlag());
    }

    @Test
    void doesNotFlagWhenNearestBuyIsMoreThan30DaysAway() {
        var rows = AcbEngine.compute(List.of(
                txn(1, "2024-01-01", Action.BUY, 100, "20.00", "0"),
                txn(2, "2024-06-01", Action.SELL, 50, "15.00", "0")
        ));

        assertFalse(rows.get(1).superficialLossFlag());
    }

    @Test
    void doesNotFlagSellAtGainEvenWithNearbyBuy() {
        var rows = AcbEngine.compute(List.of(
                txn(1, "2024-01-01", Action.BUY, 100, "20.00", "0"),
                txn(2, "2024-01-20", Action.SELL, 50, "30.00", "0")
        ));

        assertTrue(rows.get(1).capitalGainLoss().compareTo(BigDecimal.ZERO) > 0);
        assertFalse(rows.get(1).superficialLossFlag());
    }

    @Test
    void deniedLossAdjustmentAndSuperficialFlagCombine() {
        var rows = AcbEngine.compute(List.of(
                txn(1, "2024-01-01", Action.BUY, 100, "20.00", "0"),
                SecurityTransactionInput.builder()
                        .id(2)
                        .date(LocalDate.of(2024, 1, 20))
                        .action(Action.SELL)
                        .shares(50)
                        .pricePerShare("15.00")
                        .commission("0")
                        .deniedLossAdjustment(new BigDecimal("250.00"))
                        .build()
        ));

        var sell = rows.get(1);
        assertTrue(sell.superficialLossFlag());
        assertEquals(new BigDecimal("250.0000"), sell.deniedLossAdjustment());
        // remaining 50 shares cost 1000, plus 250 add-back = 1250
        assertEquals(new BigDecimal("1250.0000"), sell.totalAcb());
    }

    @Test
    void sortsByDateThenId() {
        var rows = AcbEngine.compute(List.of(
                roc(3, "2024-06-01", 10),
                txn(1, "2024-01-01", Action.BUY, 10, "10.00", "0"),
                reinv(2, "2024-03-01", 5)
        ));

        assertEquals(LocalDate.of(2024, 1, 1), rows.get(0).date());
        assertEquals(LocalDate.of(2024, 3, 1), rows.get(1).date());
        assertEquals(LocalDate.of(2024, 6, 1), rows.get(2).date());
        assertEquals(new BigDecimal("95.0000"), rows.get(2).totalAcb());
    }

    @Test
    void summarizeEmptyTransactionsReturnsZeros() {
        var summary = AcbEngine.summarize(List.<SecurityTransactionInput>of());
        assertEquals(zeroShares(), summary.shareBalance());
        assertEquals(zeroMoney(), summary.totalAcb());
        assertEquals(zeroAcbPerShare(), summary.acbPerShare());
        assertNull(summary.lastTransactionDate());
    }

    @Test
    void fullSellLeavesZeroShareBalance() {
        var summary = AcbEngine.summarize(List.of(
                txn(1, "2024-01-01", Action.BUY, 50, "10.00", "0"),
                txn(2, "2024-06-01", Action.SELL, 50, "12.00", "0")
        ));

        assertEquals(zeroShares(), summary.shareBalance());
        assertEquals(zeroMoney(), summary.totalAcb());
    }

    @Test
    void buyWithoutSharesThrows() {
        assertThrows(IllegalArgumentException.class, () -> AcbEngine.compute(List.of(
                SecurityTransactionInput.builder()
                        .id(1)
                        .date(LocalDate.of(2024, 1, 1))
                        .action(Action.BUY)
                        .pricePerShare("10.00")
                        .build()
        )));
    }

    private static List<SecurityTransactionInput> xeiTransactions() {
        return List.of(
                txn(1, "2024-03-15", Action.BUY, 500, "31.20", "10"),
                roc(2, "2024-11-02", 420),
                reinv(3, "2025-06-10", 380),
                txn(4, "2025-09-20", Action.BUY, 920, "32.68", "3.40")
        );
    }

    private static SecurityTransactionInput txn(
            long id,
            String date,
            Action action,
            long shares,
            String price,
            String commission
    ) {
        return SecurityTransactionInput.builder()
                .id(id)
                .date(LocalDate.parse(date))
                .action(action)
                .shares(shares)
                .pricePerShare(price)
                .commission(commission)
                .build();
    }

    private static SecurityTransactionInput roc(long id, String date, long cash) {
        return SecurityTransactionInput.builder()
                .id(id)
                .date(LocalDate.parse(date))
                .action(Action.RETURN_OF_CAPITAL)
                .cashAmount(cash)
                .build();
    }

    private static SecurityTransactionInput reinv(long id, String date, long cash) {
        return SecurityTransactionInput.builder()
                .id(id)
                .date(LocalDate.parse(date))
                .action(Action.REINVESTED_DISTRIBUTION)
                .cashAmount(cash)
                .build();
    }

    private static SecurityTransactionInput split(long id, String date, String ratio) {
        return SecurityTransactionInput.builder()
                .id(id)
                .date(LocalDate.parse(date))
                .action(Action.SPLIT)
                .splitRatio(ratio)
                .build();
    }

    private static void assertMoney(String expected, BigDecimal actual) {
        assertEquals(0, new BigDecimal(expected).compareTo(actual.setScale(expectedScale(expected), java.math.RoundingMode.HALF_UP)),
                () -> "expected " + expected + " but was " + actual);
    }

    private static int expectedScale(String expected) {
        int dot = expected.indexOf('.');
        return dot < 0 ? 0 : expected.length() - dot - 1;
    }

    private static BigDecimal zeroShares() {
        return BigDecimal.ZERO.setScale(6, java.math.RoundingMode.UNNECESSARY);
    }

    private static BigDecimal zeroMoney() {
        return BigDecimal.ZERO.setScale(4, java.math.RoundingMode.UNNECESSARY);
    }

    private static BigDecimal zeroAcbPerShare() {
        return BigDecimal.ZERO.setScale(8, java.math.RoundingMode.UNNECESSARY);
    }
}
