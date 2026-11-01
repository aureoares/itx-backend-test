---
topic: similar-products
date: 2026-11-01
status: ready
language: en
---

# Discovery — similar-products

> Output of `/discover`. Raw material for `/architect`.

## Context

- **Stakeholders:** Front-end engineering team, store customers, and catalog discovery team.
- **Problem statement:** Front-end applications require an efficient, single-endpoint REST API to fetch enriched details of products similar to the one a customer is currently viewing, without making multiple cascading HTTP calls from the client.
- **Current behavior / pain:** Upstream services only provide raw similar product IDs (`/product/{productId}/similarids`) and individual product details (`/product/{productId}`) separately. Fetching N product details sequentially or from the client introduces high latency, duplicate roundtrips, and increased network overhead.
- **Business goal / success metric:**
  - High concurrency resilience: Sustain 200 concurrent virtual users under k6 load tests with zero cascading thread starvation.
  - Low latency: P95 response times under 200ms for standard requests.
  - Graceful degradation: Complete the customer journey with available items even when individual downstream similar items fail or time out.

## Functional needs

- **[Must] Similar Products Aggregation:** Expose `GET /product/{productId}/similar` on port `5000` returning a JSON array of `ProductDetail` (`id`, `name`, `price`, `availability`) matching [similarProducts.yaml](similarProducts.yaml).
- **[Must] Preserve Ordering:** The returned similar products must preserve the exact similarity ranking order specified by upstream `/product/{productId}/similarids`.
- **[Must] Root Product 404 Handling:** Return HTTP `404 Not Found` when the root product does not exist in upstream (`/product/{productId}/similarids` returns 404).
- **[Must] Empty Similar Products List:** Return HTTP `200 OK` with an empty array `[]` when the root product exists but has no similar items (empty ID list).
- **[Should] Graceful Degradation on Upstream Failures:** If an individual similar product returns 404, 500, or times out upstream, omit that item from the final response and return all successfully fetched similar items rather than failing the entire request.

## Non-functional needs

- **Performance & Concurrency:**
  - Fan out detail queries concurrently in parallel using Java 21 Virtual Threads (`spring.threads.virtual.enabled=true`) and Spring `RestClient`.
  - Handle 200 constant virtual users across all test scenarios (`normal`, `notFound`, `error`, `slow`, `verySlow`).
- **Resilience & Timeouts:**
  - Enforce a strict timeout (2.5s) per upstream product detail call to shield the application from extreme upstream delays (e.g., mock 50s delay).
  - Use connection pooling with sensible max-per-route and total connection limits.
- **Caching:**
  - Implement an in-memory Caffeine cache with a short TTL (10–30s) for product details and similar product ID lists to absorb traffic spikes and reduce redundant downstream requests.
- **Observability:**
  - Expose Spring Boot Actuator health endpoints (`/actuator/health`) and metrics.
  - Structured application logging with correlation IDs for tracing upstream request fan-out.

## System & documentation impacts

- **Domain (`docs/domain.md`):** Core entities (`Product`, `ProductDetail`, `SimilarProducts`) and similarity ranking value objects.
- **Topology (`docs/topology.md`):** Service boundary defining the Spring Boot backend on port 5000 communicating with upstream Simulado mocks on port 3001.
- **Flows (`docs/flows/`):** Concurrent fan-out flow diagram (Root ID lookup -> Parallel detail fetch -> Filter & Sort -> Client response).
- **Setup (`docs/README.md`):** Application build and run instructions with Java 21 and Docker Compose verification commands (`simulado`, `influxdb`, `grafana`, `k6`).

## Constraints & assumptions

- **Java & Framework:** Java 21 LTS with Spring Boot 3.x and Spring MVC with Virtual Threads per [ADR-001](docs/technical-decisions.md#adr-001-java-version) and [ADR-002](docs/technical-decisions.md#adr-002-framework-stack--concurrency-model-spring-mvc-with-virtual-threads-over-webflux).
- **Port:** Server must listen on port `5000` (`server.port=5000`).
- **Upstream Location:** Configurable upstream URL defaulting to `http://localhost:3001` (Simulado).
- **Contract Fidelity:** Must strictly adhere to [similarProducts.yaml](similarProducts.yaml) (downstream) and [existingApis.yaml](existingApis.yaml) (upstream).

## Out of scope

- Calculating similarity scores or running ML recommendation engines (delegated to upstream `/similarids`).
- Write operations (product creation, updates, or catalog management).
- User authentication and authorization (public catalog API).
- Distributed caching clusters (Redis/Memcached); local Caffeine cache is sufficient for this scope.

## Open questions

*None. All functional boundaries, error policies, timeout budgets, and caching behaviors were clarified during discovery.*
