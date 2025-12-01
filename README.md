# Rate Limiter Prototype (Spring Boot)

A minimal Spring Boot service that demonstrates a fixed-window rate limiter. Great starting point for iterating on system design ideas before moving to distributed stores like Redis or token-bucket algorithms.

## Getting Started

```bash
cd "Rate Limitter/RateLimitter/ratelimiter"
./mvnw spring-boot:run
```

Visit `http://localhost:8080/api/demo?clientId=alice` — the limiter allows five requests per minute (see `src/main/resources/application.yml`). Exceeding the quota returns HTTP 429 along with standard `X-RateLimit-*` headers.

## Testing

```bash
cd "Rate Limitter/RateLimitter/ratelimiter"
./mvnw test
```

(Requires JDK 17+.) Tests cover the core limiter behavior so you can confidently evolve algorithms or add new backends.

## Next Ideas

- Extract the limiter into a servlet filter / interceptor so multiple controllers can share it.
- Plug in Redis or another shared store to handle multiple application nodes.
- Add metrics (Micrometer + Prometheus) and tracing to observe limiter decisions in real traffic.
