package com.investmenttracker.web;

import com.investmenttracker.service.QuoteService;
import com.investmenttracker.web.dto.QuoteResponse;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class QuoteControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private QuoteService quoteService;

    @Test
    void returnsMixedAvailabilityForSymbols() throws Exception {
        when(quoteService.getQuotes(anyList())).thenReturn(List.of(
                QuoteResponse.of("BANK.TRT", new BigDecimal("42.50"), "CAD"),
                QuoteResponse.unavailable("FAKE")
        ));

        mockMvc.perform(get("/api/v1/quotes").param("symbols", "BANK.TRT", "FAKE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].symbol").value("BANK.TRT"))
                .andExpect(jsonPath("$[0].price").value(42.50))
                .andExpect(jsonPath("$[0].currency").value("CAD"))
                .andExpect(jsonPath("$[0].available").value(true))
                .andExpect(jsonPath("$[0].asOf").exists())
                .andExpect(jsonPath("$[1].symbol").value("FAKE"))
                .andExpect(jsonPath("$[1].price").value(nullValue()))
                .andExpect(jsonPath("$[1].available").value(false));
    }

    @Test
    @SuppressWarnings("unchecked")
    void parsesCommaSeparatedSymbols() throws Exception {
        when(quoteService.getQuotes(anyList())).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/quotes").param("symbols", "BANK.TRT,GOOG,ENB.TRT"))
                .andExpect(status().isOk());

        ArgumentCaptor<List<String>> captor = ArgumentCaptor.forClass(List.class);
        verify(quoteService).getQuotes(captor.capture());
        assertThat(captor.getValue()).containsExactly("BANK.TRT", "GOOG", "ENB.TRT");
    }

    @Test
    void missingSymbolsIsBadRequest() throws Exception {
        mockMvc.perform(get("/api/v1/quotes"))
                .andExpect(status().isBadRequest());
    }
}
