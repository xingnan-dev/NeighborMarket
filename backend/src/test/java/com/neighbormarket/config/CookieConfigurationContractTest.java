package com.neighbormarket.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CookieConfigurationContractTest {
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withInitializer(new ConfigDataApplicationContextInitializer());

    @Test
    void baseProfileSupportsLocalHttp() {
        contextRunner.run(context -> assertFalse(context.getEnvironment()
                .getProperty("server.servlet.session.cookie.secure", Boolean.class, true)));
    }

    @Test
    void productionProfileDefaultsToSecureCookie() {
        contextRunner.withPropertyValues("spring.profiles.active=prod")
                .run(context -> assertTrue(context.getEnvironment()
                        .getProperty("server.servlet.session.cookie.secure", Boolean.class, false)));
    }
}
