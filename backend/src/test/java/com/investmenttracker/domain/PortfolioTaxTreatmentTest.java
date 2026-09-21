package com.investmenttracker.domain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PortfolioTaxTreatmentTest {

    @Test
    void nullBlankTaxableSmithManeuverAndOtherAreTaxable() {
        assertTrue(PortfolioTaxTreatment.isTaxable((String) null));
        assertTrue(PortfolioTaxTreatment.isTaxable(""));
        assertTrue(PortfolioTaxTreatment.isTaxable("   "));
        assertTrue(PortfolioTaxTreatment.isTaxable("Taxable"));
        assertTrue(PortfolioTaxTreatment.isTaxable("Smith Maneuver"));
        assertTrue(PortfolioTaxTreatment.isTaxable("Other"));
    }

    @Test
    void registeredTypesAreNotTaxableRegardlessOfCaseOrPadding() {
        assertFalse(PortfolioTaxTreatment.isTaxable("TFSA"));
        assertFalse(PortfolioTaxTreatment.isTaxable(" tfsa "));
        assertFalse(PortfolioTaxTreatment.isTaxable("RRSP"));
        assertFalse(PortfolioTaxTreatment.isTaxable("rrif"));
        assertFalse(PortfolioTaxTreatment.isTaxable("RESP"));
        assertFalse(PortfolioTaxTreatment.isTaxable("FHSA"));
        assertFalse(PortfolioTaxTreatment.isTaxable("LIRA"));
        assertFalse(PortfolioTaxTreatment.isTaxable("LIF"));
        assertFalse(PortfolioTaxTreatment.isTaxable("DPSP"));
    }

    @Test
    void readsTypeFromPortfolio() {
        var taxable = new Portfolio();
        taxable.setType("Taxable");
        assertTrue(PortfolioTaxTreatment.isTaxable(taxable));

        var registered = new Portfolio();
        registered.setType("TFSA");
        assertFalse(PortfolioTaxTreatment.isTaxable(registered));
    }
}
