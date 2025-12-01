package com.example.ratelimiter.service;

import java.time.Instant;

public record RateLimitDecision(boolean allowed, int remaining, Instant resetAt) {
}
