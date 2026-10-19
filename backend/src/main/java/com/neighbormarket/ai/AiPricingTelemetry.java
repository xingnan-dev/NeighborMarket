package com.neighbormarket.ai;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.concurrent.TimeUnit;

@Component
public class AiPricingTelemetry {
    static final String REQUESTS_METRIC = "neighbormarket.ai.pricing.requests";
    static final String DURATION_METRIC = "neighbormarket.ai.pricing.duration";

    private static final Logger log = LoggerFactory.getLogger(AiPricingTelemetry.class);
    private static final int USER_KEY_BYTES = 12;

    private final MeterRegistry registry;
    private final byte[] userKeySalt;
    private final boolean logEvents;

    @Autowired
    public AiPricingTelemetry(MeterRegistry registry) {
        this(registry, randomSalt(), true);
    }

    AiPricingTelemetry(MeterRegistry registry, byte[] userKeySalt, boolean logEvents) {
        this.registry = registry;
        this.userKeySalt = userKeySalt.clone();
        this.logEvents = logEvents;
    }

    static AiPricingTelemetry noop() {
        return new AiPricingTelemetry(null, new byte[32], false);
    }

    void record(String username, String outcome, long durationNanos) {
        if (registry != null) {
            Counter.builder(REQUESTS_METRIC)
                    .description("AI pricing request outcomes")
                    .tag("outcome", outcome)
                    .register(registry)
                    .increment();
            Timer.builder(DURATION_METRIC)
                    .description("AI pricing request duration")
                    .tag("outcome", outcome)
                    .register(registry)
                    .record(durationNanos, TimeUnit.NANOSECONDS);
        }
        if (logEvents) {
            log.info("AI pricing completed; userKey={} outcome={} durationMs={}",
                    anonymousUserKey(username), outcome, TimeUnit.NANOSECONDS.toMillis(durationNanos));
        }
    }

    String anonymousUserKey(String username) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(userKeySalt);
            byte[] hashed = digest.digest(username.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashed, 0, USER_KEY_BYTES);
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to create anonymous AI metric user key", exception);
        }
    }

    private static byte[] randomSalt() {
        byte[] salt = new byte[32];
        new SecureRandom().nextBytes(salt);
        return salt;
    }
}
