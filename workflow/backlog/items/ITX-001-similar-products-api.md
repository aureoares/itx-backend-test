---
id: ITX-001
title: Similar Products REST Aggregation API
status: enriched
discovery: similar-products
change: ~
priority: must
estimate: 3 SP
language: en
---

# ITX-001 — Similar Products REST Aggregation API

## Goal

Expose `GET /product/{productId}/similar` on port `5000` to aggregate and return detailed similar products (`ProductDetail` items with `id`, `name`, `price`, `availability`) for a given product ID in the exact similarity ranking order specified by upstream services.

## Context

- Connects to upstream Simulado mock service on port `3001` (`/product/{productId}/similarids` and `/product/{productId}`) documented in [existingApis.yaml](../../existingApis.yaml).
- Implements the contract defined in [similarProducts.yaml](../../similarProducts.yaml).
- Built with Spring Boot 3.x and Spring MVC enabled with Java 21 Virtual Threads (`spring.threads.virtual.enabled=true`) per [ADR-001](../../docs/technical-decisions.md#adr-001-java-version) and [ADR-002](../../docs/technical-decisions.md#adr-002-framework-stack--concurrency-model-spring-mvc-with-virtual-threads-over-webflux).
- Downstream product detail queries execute concurrently in parallel using a virtual-thread-per-task executor.
- Documentation impacts: introduces `docs/domain.md` (Product entities), `docs/topology.md` (service boundaries), and `docs/flows/` (fan-out sequence).

## Acceptance criteria

```gherkin
Scenario: Successfully return ordered similar products for an existing product
  Given an upstream service at port 3001 has similar IDs ["2", "3", "4"] for product "1"
  And product details exist for "2" (Dress, 19.99, true), "3" (Blazer, 29.99, false), and "4" (Boots, 39.99, true)
  When a GET request is sent to "/product/1/similar" on port 5000
  Then the response status code is 200 OK
  And the response body contains a JSON array with 3 products
  And the products appear in the exact similarity order: "2", "3", "4"
  And each product detail contains "id", "name", "price", and "availability"

Scenario: Root product does not exist upstream
  Given an upstream service returns 404 Not Found for "/product/999/similarids"
  When a GET request is sent to "/product/999/similar" on port 5000
  Then the response status code is 404 Not Found

Scenario: Root product has no similar products
  Given an upstream service returns an empty list "[]" for "/product/10/similarids"
  When a GET request is sent to "/product/10/similar" on port 5000
  Then the response status code is 200 OK
  And the response body is an empty JSON array "[]"

Scenario: Concurrency performance on multiple similar products
  Given an upstream service has similar IDs ["2", "3", "4"] for product "1"
  And each individual product detail call takes 100 milliseconds to respond upstream
  When a GET request is sent to "/product/1/similar" on port 5000
  Then the total response time is less than 250 milliseconds rather than the cumulative sequential sum of 300 milliseconds
  And all 3 product details are included in the response body

Scenario: Upstream service failure when fetching similar IDs
  Given the upstream service at port 3001 returns 500 Internal Server Error when querying "/product/1/similarids"
  When a GET request is sent to "/product/1/similar" on port 5000
  Then the response status code is 502 Bad Gateway
  And the response body contains a structured error payload detailing upstream failure

Scenario: Blank or invalid product ID parameter
  Given a client request with a blank or whitespace-only product ID " "
  When a GET request is sent to "/product/%20/similar" on port 5000
  Then the response status code is 400 Bad Request
  And the response body contains a descriptive validation error
```

## Notes & edge cases

- **Ordering Preservation**: Because downstream product detail calls complete asynchronously in parallel, results must be reassembled and returned in the exact index order of the initial similar IDs array.
- **Contract Conformance**: Schema must strictly adhere to `similarProducts.yaml`. Properties are `id` (String), `name` (String), `price` (Number), and `availability` (Boolean).
- **Hexagonal Architecture**: Business logic (aggregating and ordering similar products) resides in the domain/application core, completely decoupled from HTTP client and web controller infrastructure.
- **Resource Cleanup**: The virtual-thread executor used for fan-out tasks must be cleanly managed as a Spring-managed bean with proper shutdown lifecycle hooks.

## Estimate

**3 Story Points**
- 1 SP: Domain model (`ProductDetail`, `SimilarProducts`), port definitions, and Hexagonal layer architecture.
- 1 SP: HTTP adapter implementation with Spring 6 `RestClient`, connection pooling, and virtual-thread fan-out orchestration.
- 1 SP: Web controller (`GET /product/{productId}/similar` on port 5000), error handlers (`@RestControllerAdvice`), and full integration test harness using `MockRestServiceServer`.

## Definition of Done

- [ ] Acceptance criteria covered by automated tests (unit + integration with MockRestServiceServer/WireMock)
- [ ] Contract verified against `similarProducts.yaml`
- [ ] Architectural boundaries verified (Domain, Application, Infrastructure)
- [ ] Spec deltas archived in `openspec/specs/`
- [ ] Work merged into the integration branch
