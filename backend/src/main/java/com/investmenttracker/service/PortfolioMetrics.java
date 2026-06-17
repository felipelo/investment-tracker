package com.investmenttracker.service;

import java.math.BigDecimal;

/** Derived dashboard-card figures for one portfolio. Money fields may be null when no prices exist. */
public record PortfolioMetrics(
        BigDecimal invested,
        BigDecimal marketValue,
        BigDecimal returnAmount,
        BigDecimal returnPct,
        int holdingsCount
) {
}
