package com.investmenttracker.domain;

import java.util.Locale;
import java.util.Set;

/**
 * Classifies a portfolio as taxable (non-registered) vs registered. CRA ACB
 * pooling applies only to taxable accounts; registered plans are excluded.
 */
public final class PortfolioTaxTreatment {

    private static final Set<String> REGISTERED_TYPES = Set.of(
            "TFSA",
            "RRSP",
            "RRIF",
            "RESP",
            "FHSA",
            "LIRA",
            "LIF",
            "DPSP"
    );

    private PortfolioTaxTreatment() {
    }

    public static boolean isTaxable(Portfolio portfolio) {
        return isTaxable(portfolio.getType());
    }

    public static boolean isTaxable(String type) {
        if (type == null || type.isBlank()) {
            return true;
        }
        return !REGISTERED_TYPES.contains(type.trim().toUpperCase(Locale.ROOT));
    }
}
