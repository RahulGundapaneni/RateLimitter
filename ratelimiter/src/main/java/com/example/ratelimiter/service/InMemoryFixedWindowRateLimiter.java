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
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("key must be provided");
        }
        if (permits <= 0) {
            throw new IllegalArgumentException("permits must be greater than zero");
        }

        int limit = properties.getLimit();
        Duration window = properties.getWindow();
        if (limit <= 0 || window.isZero() || window.isNegative()) {
            throw new IllegalStateException("Invalid rate limiter configuration");
        }

        long windowMillis = window.toMillis();
        long nowMillis = clock.instant().toEpochMilli();
        long currentWindowStart = nowMillis - (nowMillis % windowMillis);
        long windowResetMillis = currentWindowStart + windowMillis;

        RequestWindow current = windows.get(key);
        if (current == null || nowMillis >= current.windowStart + windowMillis) {
            current = new RequestWindow(currentWindowStart, 0);
        }

        boolean allowed = permits <= limit && current.count + permits <= limit;
        int updatedCount = allowed ? current.count + permits : current.count;
        RequestWindow updated = new RequestWindow(currentWindowStart, updatedCount);
        windows.put(key, updated);

        int remaining = Math.max(0, limit - updated.count);
        Instant resetAt = Instant.ofEpochMilli(windowResetMillis);
        return new RateLimitDecision(allowed, remaining, resetAt);
    }

    private record RequestWindow(long windowStart, int count) {
    }
}
