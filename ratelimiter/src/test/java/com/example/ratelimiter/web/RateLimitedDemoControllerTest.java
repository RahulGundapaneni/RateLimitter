package com.example.ratelimiter.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.example.ratelimiter.config.RateLimiterProperties;
import com.example.ratelimiter.service.InMemoryFixedWindowRateLimiter;
import com.example.ratelimiter.service.RateLimitDecision;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

class RateLimitedDemoControllerTest {

    private RateLimiterProperties properties;
    private StubRateLimiter rateLimiter;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        properties = new RateLimiterProperties();
        properties.setLimit(5);
        properties.setWindow(Duration.ofMinutes(1));
        rateLimiter = new StubRateLimiter(properties);
        RateLimitedDemoController controller = new RateLimitedDemoController(rateLimiter, properties);
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper))
                .build();
    }

    @Test
    void statusEndpointReturnsSnapshot() throws Exception {
        Instant reset = Instant.parse("2024-01-01T00:01:00Z");
        rateLimiter.setDecision(new RateLimitDecision(true, 3, reset));

        mockMvc.perform(get("/api/demo/status").param("clientId", "alice"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-RateLimit-Limit", "5"))
                .andExpect(header().string("X-RateLimit-Remaining", "3"))
                .andExpect(header().string("X-RateLimit-Reset", Long.toString(reset.getEpochSecond())))
                .andExpect(jsonPath("$.clientId").value("alice"))
                .andExpect(jsonPath("$.limit").value(5))
                .andExpect(jsonPath("$.window").value("PT1M"))
                .andExpect(jsonPath("$.remaining").value(3))
                .andExpect(jsonPath("$.resetAt").value("2024-01-01T00:01:00Z"));
    }

    private static final class StubRateLimiter extends InMemoryFixedWindowRateLimiter {

        private RateLimitDecision decision;

        StubRateLimiter(RateLimiterProperties properties) {
            super(properties);
        }

        void setDecision(RateLimitDecision decision) {
            this.decision = decision;
        }

        @Override
        public synchronized RateLimitDecision inspect(String key) {
            if (decision == null) {
                throw new IllegalStateException("No decision configured");
            }
            return decision;
        }
    }
}
