# Proposal: Similar Products REST Aggregation API

**Traceability**:
- Backlog Item: [ITX-001](../../workflow/backlog/items/ITX-001-similar-products-api.md)
- Architectural Decisions:
  - [ADR-001: Java 21 LTS](../../docs/technical-decisions.md#adr-001-java-version)
  - [ADR-002: Spring MVC with Virtual Threads](../../docs/technical-decisions.md#adr-002-framework-stack--concurrency-model-spring-mvc-with-virtual-threads-over-webflux)
  - [ADR-005: Root Product 404 vs. Empty List Semantics](../../docs/technical-decisions.md#adr-005-root-product-404-vs-empty-list-semantics)

## Why

Front-end applications need a single consolidated REST API endpoint to retrieve enriched product details (`ProductDetail`) for products similar to a target product. Upstream catalog services provide only raw similar product IDs (`/product/{productId}/similarids`) and individual product details (`/product/{productId}`) across separate endpoints. Without this service, front-end clients must make multiple cascading network calls, introducing high latency, duplicate roundtrips, and increased network overhead.

## What Changes

This change implements the core aggregation service according to the agreed OpenAPI specification:
- Expose `GET /product/{productId}/similar` on port `5000` returning `200 OK` with a JSON array of `ProductDetail` (`id`, `name`, `price`, `availability`) matching `similarProducts.yaml`.
- Fan out downstream detail queries concurrently across Java 21 Virtual Threads (`spring.threads.virtual.enabled=true`) using Spring 6 `RestClient` with connection pooling.
- Strictly preserve the original similarity ranking order provided by the upstream `/product/{productId}/similarids` endpoint.
- Return `404 Not Found` when the root product does not exist upstream (`similarids` returns 404).
- Return `200 OK` with an empty array `[]` when the root product exists but has no similar items.
- Return `400 Bad Request` if the `productId` path parameter is blank or whitespace.
- Return `502 Bad Gateway` if the upstream service at port 3001 is unavailable or returns 500 on the root `similarids` lookup.

### Scope Boundaries

- **In Scope**:
  - Spring Boot 3.x project setup with Java 21 LTS.
  - Hexagonal / Clean Architecture structure: Domain, Application Use Cases, Infrastructure Adapters.
  - REST Controller on port 5000 conforming to `similarProducts.yaml`.
  - HTTP client adapter querying port 3001 conforming to `existingApis.yaml`.
  - Concurrency coordination via a virtual-thread-per-task executor.
  - Automated testing: Unit tests, slice tests, and integration tests with `MockRestServiceServer`.
- **Out of Scope (Deferred to ITX-002)**:
  - Advanced resilience fallbacks (graceful degradation omitting individual 404/500 similar products).
  - Strict 2.5s timeout cancellation for slow upstream mocks.
  - In-memory Caffeine caching and k6 load test optimization.

## Capabilities

### New Capabilities
- `similar-products`: Aggregation of similar product IDs and concurrent retrieval of individual product details ordered by similarity for a given root product ID.

### Modified Capabilities
None.

## Impact

- **New Application**: Spring Boot application running on port 5000.
- **APIs Exposed**: `GET /product/{productId}/similar` (contract: `similarProducts.yaml`).
- **Dependencies Consumed**: Upstream mock service running on port 3001 (contract: `existingApis.yaml`).
- **Documentation**: Establishes `docs/domain.md`, `docs/topology.md`, and `docs/flows/`.
