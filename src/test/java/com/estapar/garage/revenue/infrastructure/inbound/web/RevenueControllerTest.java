package com.estapar.garage.revenue.infrastructure.inbound.web;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.estapar.garage.garageconfiguration.domain.exception.SectorNotFoundException;
import com.estapar.garage.revenue.application.GetRevenueUseCase;
import com.estapar.garage.revenue.application.RevenueView;
import com.estapar.garage.shared.domain.Money;
import com.estapar.garage.shared.error.GlobalExceptionHandler;
import com.estapar.garage.shared.observability.CorrelationIdFilter;
import java.time.Instant;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = RevenueController.class)
@Import({GlobalExceptionHandler.class, CorrelationIdFilter.class})
class RevenueControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private GetRevenueUseCase getRevenue;

    @Test
    void returnsAmountCurrencyAndTimestamp() throws Exception {
        when(getRevenue.execute(eq("A"), eq(LocalDate.of(2025, 1, 1))))
                .thenReturn(new RevenueView(Money.of("25.00"), Instant.parse("2025-01-01T15:00:00Z")));

        mockMvc.perform(get("/revenue").param("date", "2025-01-01").param("sector", "A"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.amount").value(25.00))
                .andExpect(jsonPath("$.currency").value("BRL"))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    void unknownSectorReturns404() throws Exception {
        when(getRevenue.execute(eq("Z"), eq(LocalDate.of(2025, 1, 1)))).thenThrow(new SectorNotFoundException("Z"));

        mockMvc.perform(get("/revenue").param("date", "2025-01-01").param("sector", "Z"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.type").value("https://estapar.local/problems/sector-not-found"));
    }

    @Test
    void missingDateReturns400() throws Exception {
        mockMvc.perform(get("/revenue").param("sector", "A")).andExpect(status().isBadRequest());
    }

    @Test
    void invalidDateFormatReturns400() throws Exception {
        mockMvc.perform(get("/revenue").param("date", "01-01-2025").param("sector", "A"))
                .andExpect(status().isBadRequest());
    }
}
