package com.neighbormarket.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.neighbormarket.domain.TradeType;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.*;

class AiPricingTelemetryTest {
    private final Clock clock = Clock.fixed(Instant.parse("2026-10-19T00:00:00Z"), ZoneOffset.UTC);

    @Test
    void recordsLowCardinalitySuccessAndDurationWithoutUserDataInTags() {
        var registry = new SimpleMeterRegistry();
        var telemetry = new AiPricingTelemetry(registry, new byte[32], false);
        var service = service(telemetry, validEstimate());

        service.estimate(request(), "neighbor-sensitive");

        assertEquals(1.0, registry.get(AiPricingTelemetry.REQUESTS_METRIC)
                .tag("outcome", "estimate").counter().count());
        assertEquals(1, registry.get(AiPricingTelemetry.DURATION_METRIC)
                .tag("outcome", "estimate").timer().count());
        assertTrue(registry.getMeters().stream().flatMap(meter -> meter.getId().getTags().stream())
                .noneMatch(tag -> tag.getValue().contains("neighbor-sensitive")));
    }

    @Test
    void classifiesLocalLimitAndProviderFailures() {
        var registry = new SimpleMeterRegistry();
        var telemetry = new AiPricingTelemetry(registry, new byte[32], false);
        var limited = service(telemetry, validEstimate());
        limited.estimate(request(), "same-user");
        assertThrows(PricingException.class, () -> limited.estimate(request(), "same-user"));
        assertEquals(1.0, registry.get(AiPricingTelemetry.REQUESTS_METRIC)
                .tag("outcome", "local_rate_limited").counter().count());

        PricingGateway timeoutGateway = ignored -> {
            throw new PricingException(HttpStatus.GATEWAY_TIMEOUT, "AI_TIMEOUT", "safe public message");
        };
        var timeout = new PricingService(timeoutGateway, new ObjectMapper(), clock,
                Duration.ofSeconds(60), telemetry);
        assertThrows(PricingException.class, () -> timeout.estimate(request(), "another-user"));
        assertEquals(1.0, registry.get(AiPricingTelemetry.REQUESTS_METRIC)
                .tag("outcome", "timeout").counter().count());
    }

    @Test
    void anonymousUserKeyIsStableScopedAndDoesNotRevealUsername() {
        var first = new AiPricingTelemetry(new SimpleMeterRegistry(), new byte[32], false);
        byte[] otherSalt = new byte[32];
        otherSalt[0] = 1;
        var second = new AiPricingTelemetry(new SimpleMeterRegistry(), otherSalt, false);

        String key = first.anonymousUserKey("neighbor_a");
        assertEquals(24, key.length());
        assertEquals(key, first.anonymousUserKey("neighbor_a"));
        assertNotEquals(key, first.anonymousUserKey("neighbor_b"));
        assertNotEquals(key, second.anonymousUserKey("neighbor_a"));
        assertFalse(key.contains("neighbor"));
    }

    private PricingService service(AiPricingTelemetry telemetry, String response) {
        return new PricingService(ignored -> response, new ObjectMapper(), clock,
                Duration.ofSeconds(60), telemetry);
    }

    private PricingModels.Request request() {
        return new PricingModels.Request(TradeType.FIXED_PRICE, "台灯", "家电", "功能正常",
                null, null, "一年", "轻微使用", "无", "电源线");
    }

    private String validEstimate() {
        return "{\"status\":\"ESTIMATE\",\"minPrice\":60,\"maxPrice\":100,\"recommendedPrice\":80,"
                + "\"rationale\":\"基于描述\",\"factors\":[\"使用一年\"],"
                + "\"uncertainty\":\"未查询实时行情\",\"confidence\":\"MEDIUM\"}";
    }
}
