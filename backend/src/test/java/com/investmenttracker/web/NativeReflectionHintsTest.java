package com.investmenttracker.web;

import com.investmenttracker.web.dto.DividendSummaryResponse;
import org.junit.jupiter.api.Test;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.annotation.ReflectiveRuntimeHintsRegistrar;
import org.springframework.aot.hint.predicate.RuntimeHintsPredicates;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Guards the hand-written binding hints that the AOT type walk misses. Without them the native
 * image fails at serialization time with "Record components not available", which no JVM test
 * would ever catch.
 */
class NativeReflectionHintsTest {

    @Test
    void monthSliceIsRegisteredForBinding() {
        var hints = new RuntimeHints();
        new ReflectiveRuntimeHintsRegistrar().registerRuntimeHints(hints, PortfolioController.class);

        assertThat(RuntimeHintsPredicates.reflection().onType(DividendSummaryResponse.MonthSlice.class))
                .accepts(hints);
    }
}
