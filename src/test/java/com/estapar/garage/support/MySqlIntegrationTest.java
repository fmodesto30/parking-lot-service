package com.estapar.garage.support;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.MySQLContainer;

/**
 * Base for integration tests: one MySQL 8 container shared across the whole test JVM
 * (singleton-container pattern) so every IT runs against the real engine — real locks,
 * real unique constraints — without paying a container start per class.
 */
@SpringBootTest
@ActiveProfiles("test")
public abstract class MySqlIntegrationTest {

    @ServiceConnection
    protected static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4");

    static {
        MYSQL.start();
    }
}
