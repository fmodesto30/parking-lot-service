package com.estapar.garage.garageconfiguration.infrastructure;

import com.estapar.garage.garageconfiguration.application.GarageReadiness;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Configuration
public class GarageConfigurationBeans {

    @Bean
    public GarageReadiness garageReadiness() {
        return new GarageReadiness();
    }

    @Bean
    public TransactionTemplate transactionTemplate(PlatformTransactionManager transactionManager) {
        return new TransactionTemplate(transactionManager);
    }
}
