package com.estapar.garage.webhook.infrastructure.inbound.web;

import com.estapar.garage.parking.application.EventOutcome;
import com.estapar.garage.parking.application.event.GarageEvent;
import com.estapar.garage.webhook.application.WebhookEventProcessor;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** Receives simulator events. Happy path and exact duplicates both answer 200 (spec 03). */
@RestController
@Tag(name = "Webhook", description = "Vehicle events emitted by the garage simulator")
public class WebhookController {

    private final WebhookEventMapper mapper;
    private final WebhookEventProcessor processor;

    public WebhookController(WebhookEventMapper mapper, WebhookEventProcessor processor) {
        this.mapper = mapper;
        this.processor = processor;
    }

    @PostMapping(path = "/webhook", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Ingest an ENTRY, PARKED or EXIT event")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Event processed or recognized as a duplicate"),
        @ApiResponse(responseCode = "400", description = "Malformed or invalid payload"),
        @ApiResponse(responseCode = "404", description = "Referenced spot, session or sector not found"),
        @ApiResponse(responseCode = "409", description = "Conflicting state (garage/sector full, spot taken, ...)"),
        @ApiResponse(responseCode = "503", description = "Garage configuration not yet synchronized")
    })
    public WebhookResponse receive(@Valid @RequestBody WebhookRequest request) {
        GarageEvent event = mapper.toDomainEvent(request);
        EventOutcome outcome = processor.process(event);
        return WebhookResponse.of(outcome, event.type());
    }
}
