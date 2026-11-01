# Proposal: Upstream Resilience, Timeouts & In-Memory Caching

## Why

Under production-like loads (evaluated with 200 concurrent virtual users in k6), the Similar Products aggregation service faces artificial upstream anomalies including severe latency delays (up to 50s on product 10000), HTTP 500 server errors, and HTTP 404 missing similar items. Without strict per-request client timeouts, defensive partial failure handling, and caching, slow or broken upstream calls exhaust connection pools and degrade service availability. This change provides defensive fault tolerance and high throughput to maintain reliable sub-second responses.

This change directly implements backlog item ITX-002-resilience-and-caching and enforces architectural decisions ADR-003, ADR-004, and ADR-006.

## What Changes

- **Strict Upstream Timeouts**: Configure a strict 2.5-second read timeout and 500ms connect timeout on Spring 6 `RestClient` to bound worst-case delays and cancel downstream hanging calls.
- **Graceful Degradation for Partial Failures**: In `SimilarProductsService` and outbound adapters, gracefully catch and omit downstream product detail lookup failures (404, 500, or timeout/unreachable), returning all successfully resolved products without aborting the parent request.
- **Empty Result on Total Similar Failure**: If all similar products for an existing root product fail or time out, return HTTP 200 with an empty array `[]` rather than failing the client request.
- **In-Memory Caffeine Caching**: Integrate Spring Cache with Caffeine (`expireAfterWrite=20s`, `maximumSize=5000`) for similar IDs lookups (`similarProductIds`) and product detail lookups (`productDetails`).
- **Comprehensive Resilience Testing**: Add automated unit/integration tests verifying 404 omission, 500 omission, timeout truncation, and cache hit/eviction lifecycles.
- **k6 Load Benchmark Validation**: Run and verify k6 load tests across all 5 scenarios (`normal`, `notFound`, `error`, `slow`, `verySlow`) with 200 constant virtual users.

### Scope Boundaries
- **In Scope**:
  - `RestClientConfig` timeout and connection pool tuning.
  - Defensive error handling and exception logging in `SimuladoProductCatalogAdapter` and `SimilarProductsService`.
  - Spring Boot caching configuration with Caffeine dependency in `pom.xml`.
  - Cache eviction and performance tests.
  - End-to-end load verification with k6.
- **Out of Scope**:
  - Distributed caching systems (e.g., Redis, Hazelcast).
  - Circuit breaker libraries (e.g., Resilience4j) — standard timeout + graceful degradation suffices for the assessment requirements.
  - Modification of external API contracts (`similarProducts.yaml` or `existingApis.yaml`).

## Capabilities

### New Capabilities
None.

### Modified Capabilities
- `similar-products`: Modify requirements to mandate graceful degradation (omitting failing, missing, or timed-out similar product details while preserving successfully resolved items) and add requirements for caching and strict 2.5s timeouts.

## Impact

- **Dependencies**: Add `org.springframework.boot:spring-boot-starter-cache` and `com.github.ben-manes.caffeine:caffeine` to `pom.xml`.
- **Application Services**: `SimilarProductsService` fan-out logic updated to catch exceptions per future and filter out failed/empty results.
- **Infrastructure / Outbound Adapters**: `SimuladoProductCatalogAdapter` annotated with `@Cacheable` and updated to handle read timeouts defensively.
- **Configuration**: `application.yml` updated with cache specifications and timeout settings.
