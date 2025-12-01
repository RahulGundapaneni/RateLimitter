package com.example.ratelimiter.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "rate-limiter")
public class RateLimiterProperties {

    /** Maximum number of requests allowed within the configured window. */
    private int limit = 10;

    /** Duration of the evaluation window. */
    private Duration window = Duration.ofSeconds(60);

    public int getLimit() {
        return limit;
    }

    public void setLimit(int limit) {
        this.limit = limit;
    }

    public Duration getWindow() {
        return window;
    }

    public void setWindow(Duration window) {
        this.window = window;
    }
}
