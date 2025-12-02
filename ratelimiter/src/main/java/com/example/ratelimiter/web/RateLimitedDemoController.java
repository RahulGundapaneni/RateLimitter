package com.example.ratelimiter.web;

import java.time.Instant;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.ratelimiter.config.RateLimiterProperties;
import com.example.ratelimiter.service.InMemoryFixedWindowRateLimiter;
import com.example.ratelimiter.service.RateLimitDecision;

@RestController
public class RateLimitedDemoController {

    private final InMemoryFixedWindowRateLimiter rateLimiter;
    private final RateLimiterProperties properties;

    public RateLimitedDemoController(InMemoryFixedWindowRateLimiter rateLimiter,
            RateLimiterProperties properties) {
        this.rateLimiter = rateLimiter;
        this.properties = properties;
    }

    @GetMapping("/api/demo")
    public ResponseEntity<String> demo(
            @RequestParam(defaultValue = "anonymous") String clientId,
            @RequestParam(name = "cost", defaultValue = "1") int cost) {
        if (cost <= 0) {
            return ResponseEntity.badRequest().body("cost must be greater than zero");
        }

        RateLimitDecision decision = rateLimiter.evaluate(clientId, cost);
        ResponseEntity.BodyBuilder builder = decision.allowed() ? ResponseEntity.ok() : ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS);
        builder.header("X-RateLimit-Limit", Integer.toString(properties.getLimit()))
                .header("X-RateLimit-Remaining", Integer.toString(decision.remaining()))
                .header("X-RateLimit-Reset", Long.toString(decision.resetAt().getEpochSecond()));

        if (decision.allowed()) {
            return builder.body("Request accepted for client " + clientId);
        }

        long retryAfterSeconds = Math.max(0, decision.resetAt().getEpochSecond() - Instant.now().getEpochSecond());
        builder.header("Retry-After", Long.toString(retryAfterSeconds));
        return builder.body("Too many requests. Try again after " + decision.resetAt());
    }
}
