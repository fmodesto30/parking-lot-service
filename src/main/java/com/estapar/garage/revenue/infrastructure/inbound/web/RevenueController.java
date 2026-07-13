package com.estapar.garage.revenue.infrastructure.inbound.web;

import com.estapar.garage.revenue.application.GetRevenueUseCase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Revenue by sector and date. The challenge sketches a GET-with-body; body-on-GET is
 * non-canonical and poorly supported, so the contract uses query parameters (README §API).
 */
@RestController
@Tag(name = "Revenue", description = "Revenue aggregated from immutable charges")
public class RevenueController {

    private final GetRevenueUseCase getRevenue;

    public RevenueController(GetRevenueUseCase getRevenue) {
        this.getRevenue = getRevenue;
    }

    @GetMapping("/revenue")
    @Operation(summary = "Total revenue for a sector on a business-day date")
    public RevenueResponse revenue(
            @Parameter(description = "Business date (YYYY-MM-DD)", example = "2025-01-01")
                    @RequestParam
                    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                    LocalDate date,
            @Parameter(description = "Sector code", example = "A") @RequestParam String sector) {
        return RevenueResponse.from(getRevenue.execute(sector, date));
    }
}
