package com.example.ratelimiter.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.example.ratelimiter.config.RateLimiterProperties;

class InMemoryFixedWindowRateLimiterTest {

    private RateLimiterProperties properties;
    private Clock clock;
    private InMemoryFixedWindowRateLimiter rateLimiter;

    @BeforeEach
    void setUp() {
        properties = new RateLimiterProperties();
        properties.setLimit(2);
        properties.setWindow(Duration.ofSeconds(60));
        clock = Clock.fixed(Instant.parse("2024-01-01T00:00:00Z"), ZoneOffset.UTC);
        rateLimiter = new InMemoryFixedWindowRateLimiter(properties, clock);
    }

    @Test
    void allowsRequestsWithinTheWindow() {
        RateLimitDecision first = rateLimiter.evaluate("client");
        RateLimitDecision second = rateLimiter.evaluate("client");

        assertThat(first.allowed()).isTrue();
        assertThat(second.allowed()).isTrue();
        assertThat(second.remaining()).isZero();
    }

    @Test
    void blocksWhenLimitExceeded() {
        rateLimiter.evaluate("client");
        rateLimiter.evaluate("client");
        RateLimitDecision blocked = rateLimiter.evaluate("client");

        assertThat(blocked.allowed()).isFalse();
        assertThat(blocked.remaining()).isZero();
    }

    @Test
    void deductsMultiplePermitsAtOnce() {
        RateLimitDecision first = rateLimiter.evaluate("client", 2);
        RateLimitDecision second = rateLimiter.evaluate("client", 1);

        assertThat(first.allowed()).isTrue();
        assertThat(first.remaining()).isZero();
        assertThat(second.allowed()).isFalse();
    }

    @Test
    void resetsCounterWhenWindowRollsOver() {
        rateLimiter.evaluate("client");
        Clock nextMinuteClock = Clock.fixed(clock.instant().plusSeconds(60), ZoneOffset.UTC);
        rateLimiter = new InMemoryFixedWindowRateLimiter(properties, nextMinuteClock);

        RateLimitDecision decision = rateLimiter.evaluate("client");
        assertThat(decision.allowed()).isTrue();
        assertThat(decision.remaining()).isEqualTo(1);
    }

    @Test
    void inspectReturnsSnapshotWithoutConsumingPermits() {
        RateLimitDecision emptyWindow = rateLimiter.inspect("client");
        assertThat(emptyWindow.allowed()).isTrue();
        assertThat(emptyWindow.remaining()).isEqualTo(2);

        rateLimiter.evaluate("client");
        RateLimitDecision afterOneCall = rateLimiter.inspect("client");
        assertThat(afterOneCall.allowed()).isTrue();
        assertThat(afterOneCall.remaining()).isEqualTo(1);

        rateLimiter.evaluate("client");
        RateLimitDecision exhaustedWindow = rateLimiter.inspect("client");
        assertThat(exhaustedWindow.allowed()).isFalse();
        assertThat(exhaustedWindow.remaining()).isZero();
    }
}
