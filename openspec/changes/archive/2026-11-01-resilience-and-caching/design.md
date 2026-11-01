# Design: Upstream Resilience, Timeouts & In-Memory Caching

## Context

See [proposal.md](proposal.md) for background and motivation. This design details the resilience and caching mechanisms that protect the `GET /product/{productId}/similar` aggregation service under high concurrency (200 constant virtual users in k6).

The service builds upon the Hexagonal architecture established in `similar-products-api`:
- Inbound adapter: `SimilarProductsController` (`:5000`)
- Application service: `SimilarProductsService` (virtual-thread fan-out)
- Outbound adapter: `SimuladoProductCatalogAdapter` (calling Simulado on `:3001` via `RestClient`)

## Goals / Non-Goals

**Goals:**
- Enforce strict 2.5s read timeouts per upstream HTTP call, preventing cascading latency from artificial 5s and 50s mock delays (ADR-004).
- Implement defensive graceful degradation: omit downstream product detail failures (404, 500, timeout) without aborting the parent response (ADR-003).
- Maintain strict distinction between root product missing (404) vs root product with failed/empty similar items (200 with `[]`) (ADR-005).
- Accelerate repetitive lookups using Caffeine in-memory caching with 20s TTL and 5,000 entry maximum capacity (ADR-006).
- Successfully pass the k6 performance benchmark with 200 virtual users across all 5 test scenarios (`normal`, `notFound`, `error`, `slow`, `verySlow`) with 0% application crashes.

**Non-Goals:**
- External distributed cache clusters (e.g. Redis).
- Heavy circuit-breaker frameworks (e.g. Resilience4j), as timeout + graceful omission satisfies test specifications with lower complexity.

---

## Decisions

### 1. Per-Request Client Timeout Configuration
- **Decision**: Configure Apache HttpClient 5 request settings with:
  - Connect Timeout: 500ms
  - Response (Read) Timeout: 2500ms (2.5s)
  - Connection Request Timeout: 1000ms
- **Rationale**: The mock service injects delays of 100ms (`normal`), 1s (`slow`), 5s (`verySlow`), and 50s (product 10000). A 2.5s timeout permits normal and 1s requests to succeed while aborting 5s and 50s stalls, keeping total fan-out time bounded under 3.0s.
- **Alternatives Considered**: 5s timeout. Rejected because 5s delays on multiple items would degrade user experience and risk pool saturation under 200 concurrent VUs.

### 2. Graceful Degradation in Application & Outbound Layer
- **Decision**: Catch upstream exceptions defensively on individual product detail lookups:
  - In `SimuladoProductCatalogAdapter.getProductDetail(productId)`:
    - HTTP 404 (Not Found) -> return `Optional.empty()`
    - HTTP 5xx, `ResourceAccessException` (timeout/connection failure) -> log warning with correlation context and return `Optional.empty()`
  - In `SimilarProductsService`:
    - Futures execute `catalogPort.getProductDetail(id)` asynchronously on `fanOutExecutor`.
    - Use `CompletableFuture.supplyAsync(...).exceptionally(ex -> Optional.empty())` to guarantee individual task failures never abort `CompletableFuture.allOf(...)`.
    - Flatten results with `.flatMap(Optional::stream).toList()`, preserving the original sequence order of successful items.
- **Rationale**: Isolates failures so that a single broken or slow similar item does not fail the entire aggregation.
- **Alternatives Considered**: Fail-fast (aborting whole request if any similar item fails). Rejected because contract and ADR-003 explicitly mandate returning all available items.

### 3. Root vs. Similar Error Propagation
- **Decision**:
  - `getSimilarProductIds(productId)`: If root product returns HTTP 404, throw `ProductNotFoundException` which `GlobalExceptionHandler` translates to HTTP 404.
  - `getProductDetail(productId)`: If similar product detail is missing or failing, return `Optional.empty()`.
  - If all similar product lookups fail or time out, `SimilarProductsService` returns an empty list `[]`, mapped to HTTP 200 `[]`.
- **Rationale**: Adheres strictly to ADR-005 and OpenAPI specification in `similarProducts.yaml`.

### 4. Caffeine In-Memory Caching Strategy
- **Decision**:
  - Configure Spring Cache with Caffeine:
    ```java
    @Configuration
    @EnableCaching
    public class CacheConfig {
        @Bean
        public CacheManager cacheManager() {
            CaffeineCacheManager manager = new CaffeineCacheManager("similarProductIds", "productDetails");
            manager.setCaffeine(Caffeine.newBuilder()
                .expireAfterWrite(20, TimeUnit.SECONDS)
                .maximumSize(5000)
                .recordStats());
            return manager;
        }
    }
    ```
  - Annotate outbound adapter:
    - `@Cacheable(value = "similarProductIds", key = "#productId")` on `getSimilarProductIds`.
    - `@Cacheable(value = "productDetails", key = "#productId", unless = "#result == null || !#result.isPresent()")` on `getProductDetail` (avoids caching empty/failed items).
- **Rationale**: 20s TTL keeps catalog data fresh while absorbing high-frequency duplicate queries generated during peak load (e.g. 200 VUs repeatedly querying products 1, 2, 3, etc.).

### 5. Connection Pool Tuning
- **Decision**: Configure `PoolingHttpClientConnectionManager`:
  - `maxTotal = 500`
  - `defaultMaxPerRoute = 200`
- **Rationale**: Under 200 concurrent VUs each making up to 3 parallel downstream calls, peak concurrency can generate hundreds of simultaneous in-flight sockets. Sizing for 200 per route prevents socket queuing.

---

## Architectural Structure

```
com.itx.similarproducts
├── domain (unchanged)
├── application
│   ├── port (unchanged)
│   └── service
│       └── SimilarProductsService.java         // Updated with defensive CompletableFuture handling
└── infrastructure
    ├── config
    │   ├── CacheConfig.java                   // NEW: Caffeine cache manager & specs
    │   ├── RestClientConfig.java              // UPDATED: 2.5s timeouts & connection pooling
    │   └── VirtualThreadExecutorConfig.java   // (unchanged)
    └── adapter
        └── out
            └── http
                └── SimuladoProductCatalogAdapter.java // UPDATED: @Cacheable & defensive error handling
```

---

## Risks / Trade-offs

- **[Risk] Delayed Cache Expiration on Price Updates**:
  - *Mitigation*: 20-second TTL is sufficiently short that price or availability updates propagate rapidly without requiring complex cache invalidation webhooks.
- **[Risk] Cache Memory Growth under High ID Cardinality**:
  - *Mitigation*: `maximumSize(5000)` enforces LRU eviction, capping memory consumption to a few megabytes.
- **[Risk] High Latency on Uncached Slow Items**:
  - *Mitigation*: 2.5s read timeout strictly terminates delays before client connections time out.
