package com.neighbormarket.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ProductionConfigurationContractTest {
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withInitializer(new ConfigDataApplicationContextInitializer())
            .withPropertyValues("spring.profiles.active=prod");

    @Test
    void productionRequiresDatabasePasswordAndAllowedOrigin() {
        contextRunner.run(context -> {
            assertThrows(IllegalArgumentException.class,
                    () -> context.getEnvironment().getRequiredProperty("spring.datasource.password"));
            assertThrows(IllegalArgumentException.class,
                    () -> context.getEnvironment().getRequiredProperty("app.allowed-origins"));
        });
    }

    @Test
    void productionUsesExternalValuesAndBindsAllInterfaces() {
        contextRunner.withPropertyValues(
                        "DB_HOST=mysql",
                        "DB_NAME=neighbor_market",
                        "DB_USERNAME=neighbor_app",
                        "DB_PASSWORD=test-only",
                        "APP_ALLOWED_ORIGINS=https://market.example")
                .run(context -> {
                    assertEquals("test-only", context.getEnvironment()
                            .getRequiredProperty("spring.datasource.password"));
                    assertEquals("https://market.example", context.getEnvironment()
                            .getRequiredProperty("app.allowed-origins"));
                    assertEquals("0.0.0.0", context.getEnvironment()
                            .getRequiredProperty("server.address"));
                });
    }
}
