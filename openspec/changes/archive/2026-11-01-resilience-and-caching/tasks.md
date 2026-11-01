# Tasks: Upstream Resilience, Timeouts & In-Memory Caching

## 1. Dependencies & Timeout Configuration

- [x] 1.1 Add `spring-boot-starter-cache` and `com.github.ben-manes.caffeine:caffeine` dependencies to `pom.xml`, and verify the project compiles cleanly with `mvn compile`
- [x] 1.2 Update `RestClientConfig` and `application.yml` with strict timeouts (`connect-timeout-ms: 500`, `read-timeout-ms: 2500`) and connection pooling (`max-total: 500`, `max-per-route: 200`), and verify configuration properties via unit test

## 2. In-Memory Caching Implementation

- [x] 2.1 Implement `CacheConfig` with `@EnableCaching` and a Spring `CaffeineCacheManager` configuring `similarProductIds` and `productDetails` caches (20s TTL, maximum 5,000 entries), and verify cache manager bean initialization via application context test
- [x] 2.2 Annotate `SimuladoProductCatalogAdapter` methods with `@Cacheable` (`similarProductIds` and `productDetails` with conditional exclusion of empty results), and verify with test asserting that repeated calls hit the cache without making outbound HTTP requests

## 3. Defensive Graceful Degradation in Fan-Out

- [x] 3.1 Update `SimuladoProductCatalogAdapter.getProductDetail` to catch 5xx and read timeout (`ResourceAccessException`) errors defensively, returning `Optional.empty()` instead of throwing `UpstreamServiceException`, and verify with `MockRestServiceServer` tests
- [x] 3.2 Update `SimilarProductsService` virtual thread fan-out to handle task failures defensively with `.exceptionally(...)` and omit empty/failing products via `.flatMap(Optional::stream)`, and verify with unit test asserting partial results returned in order with failed items omitted
- [x] 3.3 Verify root product 404 propagation vs. total similar product failure (returning HTTP 200 with `[]` when all similar products fail/time out) via unit tests

## 4. Resilience & Caching Integration Tests

- [x] 4.1 Implement `SimilarProductsResilienceIntegrationTest` verifying graceful degradation when similar products return 404, 500, or simulate read timeout, and verify HTTP 200 response with remaining items
- [x] 4.2 Add integration tests verifying sub-50ms cache hits and cache eviction after the 20-second TTL expires

## 5. Live Mock Verification & k6 Benchmark

- [x] 5.1 Execute live verification test against Simulado on port 3001 asserting `GET /product/3/similar` truncates 5s and 50s delays and returns within 3.0s with product `100`
- [x] 5.2 Execute full k6 benchmark suite (`docker-compose run --rm k6 run scripts/test.js`) with 200 constant virtual users across all 5 scenarios (`normal`, `notFound`, `error`, `slow`, `verySlow`), and verify 0% 5xx server error rate
- [x] 5.3 Update performance and architecture documentation in `docs/` with benchmark metrics and links to ADR-003, ADR-004, and ADR-006, and verify links are valid
