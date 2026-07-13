package com.estapar.garage.shared.configuration;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfiguration {

    @Bean
    public OpenAPI garageOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Estapar Garage API")
                        .version("1.0.0")
                        .description("""
                                Garage management service: consumes ENTRY/PARKED/EXIT webhook events from the
                                simulator, applies occupancy-based dynamic pricing and time-based billing, and
                                exposes revenue by sector and business date. Errors use RFC-7807 ProblemDetail
                                and every response carries an X-Correlation-Id header.
                                """)
                        .contact(new Contact().name("Estapar Backend Test"))
                        .license(new License().name("Proprietary")));
    }
}
