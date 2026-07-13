package com.estapar.garage.garageconfiguration.infrastructure.outbound.simulator;

import com.estapar.garage.shared.configuration.GarageProperties;
import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.ClientHttpRequestFactorySettings;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/** Explicit connect/read timeouts — a bootstrap dependency must fail fast, not hang. */
@Configuration
public class SimulatorClientConfiguration {

    @Bean
    public RestClient simulatorRestClient(GarageProperties properties) {
        var simulator = properties.simulator();
        ClientHttpRequestFactorySettings settings = ClientHttpRequestFactorySettings.defaults()
                .withConnectTimeout(simulator.connectTimeout())
                .withReadTimeout(simulator.readTimeout());
        ClientHttpRequestFactory requestFactory =
                ClientHttpRequestFactoryBuilder.detect().build(settings);
        return RestClient.builder()
                .baseUrl(simulator.baseUrl())
                .requestFactory(requestFactory)
                .build();
    }
}
