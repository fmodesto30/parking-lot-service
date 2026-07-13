package com.estapar.garage.webhook.infrastructure.inbound.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.estapar.garage.garageconfiguration.domain.exception.GarageNotReadyException;
import com.estapar.garage.parking.application.EventOutcome;
import com.estapar.garage.parking.application.event.GarageEvent;
import com.estapar.garage.parking.domain.exception.ActiveSessionNotFoundException;
import com.estapar.garage.parking.domain.exception.GarageFullException;
import com.estapar.garage.shared.error.GlobalExceptionHandler;
import com.estapar.garage.shared.observability.CorrelationIdFilter;
import com.estapar.garage.webhook.application.WebhookEventProcessor;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Webhook HTTP contract (spec 03): status codes, ProblemDetail bodies and the correlation-id
 * header, exercised through the real mapper, filter and error advice with the processor mocked.
 */
@WebMvcTest(controllers = WebhookController.class)
@Import({WebhookEventMapper.class, GlobalExceptionHandler.class, CorrelationIdFilter.class})
class WebhookControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private WebhookEventProcessor processor;

    private static final String ENTRY = """
            {"license_plate":"ZUL0001","entry_time":"2025-01-01T12:00:00.000Z","event_type":"ENTRY"}""";

    @Test
    void processedEntryReturns200AndEchoesCorrelationId() throws Exception {
        when(processor.process(any(GarageEvent.class))).thenReturn(EventOutcome.PROCESSED);

        mockMvc.perform(post("/webhook")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Correlation-Id", "abc-123")
                        .content(ENTRY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result").value("PROCESSED"))
                .andExpect(header().string("X-Correlation-Id", "abc-123"));
    }

    @Test
    void duplicateEventReturns200() throws Exception {
        when(processor.process(any(GarageEvent.class))).thenReturn(EventOutcome.DUPLICATE);

        mockMvc.perform(post("/webhook").contentType(MediaType.APPLICATION_JSON).content(ENTRY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result").value("DUPLICATE"));
    }

    @Test
    void unknownEventTypeReturns400() throws Exception {
        String body = """
                {"license_plate":"ZUL0001","event_type":"FLYING"}""";

        mockMvc.perform(post("/webhook").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").exists())
                .andExpect(jsonPath("$.correlationId").exists());
    }

    @Test
    void entryMissingEntryTimeReturns400() throws Exception {
        String body = """
                {"license_plate":"ZUL0001","event_type":"ENTRY"}""";

        mockMvc.perform(post("/webhook").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void entryCarryingExitTimeIsRejectedAsAmbiguous() throws Exception {
        String body = """
                {"license_plate":"ZUL0001","entry_time":"2025-01-01T12:00:00Z",
                 "exit_time":"2025-01-01T13:00:00Z","event_type":"ENTRY"}""";

        mockMvc.perform(post("/webhook").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void parkedMissingCoordinatesReturns400() throws Exception {
        String body = """
                {"license_plate":"ZUL0001","event_type":"PARKED"}""";

        mockMvc.perform(post("/webhook").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void malformedJsonReturns400() throws Exception {
        mockMvc.perform(post("/webhook").contentType(MediaType.APPLICATION_JSON).content("{not json"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void garageFullReturns409ProblemDetail() throws Exception {
        when(processor.process(any(GarageEvent.class))).thenThrow(new GarageFullException(30));

        mockMvc.perform(post("/webhook").contentType(MediaType.APPLICATION_JSON).content(ENTRY))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value("https://estapar.local/problems/garage-full"))
                .andExpect(jsonPath("$.status").value(409));
    }

    @Test
    void unknownSpotReturns404() throws Exception {
        String parked = """
                {"license_plate":"ZUL0001","lat":-23.5,"lng":-46.6,"event_type":"PARKED"}""";
        when(processor.process(any(GarageEvent.class)))
                .thenThrow(new ActiveSessionNotFoundException(
                        new com.estapar.garage.shared.domain.LicensePlate("ZUL0001")));

        mockMvc.perform(post("/webhook").contentType(MediaType.APPLICATION_JSON).content(parked))
                .andExpect(status().isNotFound());
    }

    @Test
    void configurationNotReadyReturns503() throws Exception {
        when(processor.process(any(GarageEvent.class))).thenThrow(new GarageNotReadyException());

        mockMvc.perform(post("/webhook").contentType(MediaType.APPLICATION_JSON).content(ENTRY))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.type").value("https://estapar.local/problems/garage-not-ready"));
    }

    @Test
    void generatesCorrelationIdWhenNoneProvided() throws Exception {
        when(processor.process(any(GarageEvent.class))).thenReturn(EventOutcome.PROCESSED);

        mockMvc.perform(post("/webhook").contentType(MediaType.APPLICATION_JSON).content(ENTRY))
                .andExpect(header().exists("X-Correlation-Id"));
    }
}
