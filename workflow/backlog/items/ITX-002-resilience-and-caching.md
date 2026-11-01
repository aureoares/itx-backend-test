---
id: ITX-002
title: Upstream Resilience, Timeouts & In-Memory Caching
status: enriched
discovery: similar-products
change: ~
priority: should
estimate: 2 SP
language: en
---

# ITX-002 — Upstream Resilience, Timeouts & In-Memory Caching

## Goal

Protect the similar products endpoint from upstream latency spikes and failure modes, ensuring high concurrency throughput (200 constant virtual users in k6) through defensive graceful degradation, strict client timeouts, and local in-memory caching.

## Context

- Extends `ITX-001` with resilience patterns and performance caching.
- Implements graceful degradation per [ADR-003](../../docs/technical-decisions.md#adr-003-downstream-partial-failure-handling-graceful-degradation): when an individual similar product returns 404, 500, or times out, omit that item and return available similar products.
- Enforces a 2.5s strict timeout per upstream call per [ADR-004](../../docs/technical-decisions.md#adr-004-timeout-strategy-for-upstream-dependencies) to prevent connection pool exhaustion under the 50s artificial delay on mock product 10000.
- Implements an in-memory Caffeine cache (10–30s TTL) per [ADR-006](../../docs/technical-decisions.md#adr-006-in-memory-caching-strategy-caffeine) for product details and similar ID lookups.
- Verified using the k6 load testing suite across all 5 test scenarios (`normal`, `notFound`, `error`, `slow`, `verySlow`) with 200 constant virtual users.
- Documentation impacts: updates `docs/README.md` with verification commands and benchmark execution guides.

## Acceptance criteria

```gherkin
Scenario: Graceful degradation when an individual similar product is not found (404)
  Given product "4" has similar IDs ["1", "2", "5"] upstream
  And products "1" and "2" exist, but product "5" returns 404 Not Found upstream
  When a GET request is sent to "/product/4/similar" on port 5000
  Then the response status code is 200 OK
  And the response array contains details for products "1" and "2"
  And product "5" is omitted from the response array

Scenario: Graceful degradation when an individual similar product fails upstream (500)
  Given product "5" has similar IDs ["1", "2", "6"] upstream
  And products "1" and "2" exist, but product "6" returns 500 Internal Server Error upstream
  When a GET request is sent to "/product/5/similar" on port 5000
  Then the response status code is 200 OK
  And the response array contains details for products "1" and "2"
  And product "6" is omitted from the response array

Scenario: Upstream delay exceeding client timeout is canceled and omitted
  Given product "3" has similar IDs ["100", "1000", "10000"] upstream
  And product "100" responds in 1 second
  And products "1000" and "10000" have artificial delays of 5 seconds and 50 seconds upstream
  When a GET request is sent to "/product/3/similar" on port 5000
  Then the overall response is returned within 3.0 seconds
  And the response status code is 200 OK
  And the response array contains details only for product "100"
  And products "1000" and "10000" are omitted due to exceeding the 2.5-second timeout

Scenario: All similar products fail or time out upstream
  Given product "8" has similar IDs ["5", "6"] upstream
  And product "5" returns 404 Not Found and product "6" returns 500 Internal Server Error
  When a GET request is sent to "/product/8/similar" on port 5000
  Then the response status code is 200 OK
  And the response body is an empty JSON array "[]"

Scenario: Repeated queries are served from in-memory Caffeine cache
  Given product "1" similar details have been fetched and cached
  When a subsequent GET request is sent to "/product/1/similar" within the cache TTL (10-30s)
  Then the response is returned in under 50 milliseconds
  And no new outgoing HTTP requests are issued to the upstream service on port 3001

Scenario: Cache eviction on expiration
  Given product "1" similar details are cached with a 20-second TTL
  And 21 seconds have elapsed since the entry was cached
  When a GET request is sent to "/product/1/similar" on port 5000
  Then fresh data is retrieved from the upstream service and the cache is updated

Scenario: Performance under high concurrency
  Given the k6 load test runs 200 constant virtual users across all 5 scenarios
  When executing "docker-compose run --rm k6 run scripts/test.js"
  Then the application server error rate (5xx) is 0% across all scenarios
  And p95 latency for the "normal" scenario is below 200 milliseconds
```

## Notes & edge cases

- **Timeout Granularity**: Timeouts are enforced per individual upstream HTTP request (connection timeout 500ms, read timeout 2500ms), ensuring that a single delayed item does not consume the entire request budget.
- **Connection Pool Sizing**: The underlying HTTP client connection manager must configure at least 200 connections per route and 500 total connections to avoid socket starvation during the 200 VU benchmark.
- **Cache Sizing & Eviction**: Uses Caffeine with `expireAfterWrite` (20s) and `maximumSize` (5000 entries) to guarantee bounded memory usage.
- **Error Filtering Semantics**: Only downstream errors on *similar* product lookups are gracefully omitted. If the *root* product lookup `/product/{productId}/similarids` returns 404, it must return 404 per `ITX-001`.

## Estimate

**2 Story Points**
- 1 SP: Caffeine cache configuration (`@EnableCaching`, cache manager with TTL and maximum size boundaries) for similar IDs and product details.
- 1 SP: RestClient timeout configuration, connection pooling setup, defensive fallback handling in the fan-out service, and k6 benchmark verification.

## Definition of Done

- [ ] Resilience acceptance criteria covered by automated tests (WireMock/MockRestServiceServer simulating 404, 500, and timeout delays)
- [ ] k6 benchmark passes with 200 constant virtual users across all 5 scenarios with 0% application crashes
- [ ] Spec deltas archived in `openspec/specs/`
- [ ] Work merged into the integration branch
