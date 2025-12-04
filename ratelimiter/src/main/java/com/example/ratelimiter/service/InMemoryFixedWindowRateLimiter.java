package com.example.ratelimiter.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import org.springframework.stereotype.Service;

import com.example.ratelimiter.config.RateLimiterProperties;

@Service
public class InMemoryFixedWindowRateLimiter {

    private final RateLimiterProperties properties;
    private final Clock clock;
    private final Map<String, RequestWindow> windows = new HashMap<>();

    public InMemoryFixedWindowRateLimiter(RateLimiterProperties properties) {
        this(properties, Clock.systemUTC());
    }

    InMemoryFixedWindowRateLimiter(RateLimiterProperties properties, Clock clock) {
        this.properties = Objects.requireNonNull(properties, "properties must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    public synchronized RateLimitDecision evaluate(String key) {
        return evaluate(key, 1);
    }

    public synchronized RateLimitDecision evaluate(String key, int permits) {
        validateKey(key);
        if (permits <= 0) {
            throw new IllegalArgumentException("permits must be greater than zero");
        }

        WindowState state = resolveWindowState(key);

        boolean allowed = permits <= state.limit() && state.current().count() + permits <= state.limit();
        int updatedCount = allowed ? state.current().count() + permits : state.current().count();
        RequestWindow updated = new RequestWindow(state.current().windowStart(), updatedCount);
        windows.put(key, updated);

        int remaining = Math.max(0, state.limit() - updated.count());
        Instant resetAt = Instant.ofEpochMilli(state.windowResetMillis());
        return new RateLimitDecision(allowed, remaining, resetAt);
    }

    /**
     * Returns a snapshot of the current rate limit window without consuming permits.
     */
    public synchronized RateLimitDecision inspect(String key) {
        validateKey(key);
        WindowState state = resolveWindowState(key);

        int remaining = Math.max(0, state.limit() - state.current().count());
        boolean allowed = remaining > 0;
        Instant resetAt = Instant.ofEpochMilli(state.windowResetMillis());
        return new RateLimitDecision(allowed, remaining, resetAt);
    }

    private void validateKey(String key) {
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("key must be provided");
        }
    }

    private WindowState resolveWindowState(String key) {
        int limit = properties.getLimit();
        Duration window = properties.getWindow();
        if (limit <= 0 || window.isZero() || window.isNegative()) {
            throw new IllegalStateException("Invalid rate limiter configuration");
        }

        long windowMillis = window.toMillis();
        if (windowMillis <= 0) {
            throw new IllegalStateException("Invalid rate limiter configuration");
        }

        long nowMillis = clock.instant().toEpochMilli();
        long currentWindowStart = nowMillis - (nowMillis % windowMillis);
        long windowResetMillis = currentWindowStart + windowMillis;

        RequestWindow current = windows.get(key);
        if (current == null || nowMillis >= current.windowStart + windowMillis) {
            current = new RequestWindow(currentWindowStart, 0);
        }

        return new WindowState(limit, windowResetMillis, current);
    }

    private record RequestWindow(long windowStart, int count) {
    }

    private record WindowState(int limit, long windowResetMillis, RequestWindow current) {
    }
}
