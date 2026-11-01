# Technical Decisions

Architectural Decision Records (ADR) and technical choices for the ITX Back-End technical test.

---

## ADR-001: Java Version

- **Status**: Accepted
- **Context**: The technical test specification requires building a high-concurrency, resilient Spring Boot application capable of handling 200 concurrent virtual users and parallel calls to downstream services with varying latencies (up to 50s). The specification does not mandate an exact Java version, leaving it to senior engineering discretion.
- **Decision**: Adopt **Java 21 (LTS)**.
- **Rationale**:
  - **Virtual Threads (Project Loom - JEP 444)**: First-class feature in Java 21 LTS and seamlessly integrated into Spring Boot 3.x (`spring.threads.virtual.enabled=true`). Provides high-throughput, lightweight thread-per-task concurrency that prevents platform thread pool starvation under slow I/O (e.g., downstream mock delays up to 50s).
  - **Spring Boot 3.x Baseline Alignment**: Spring Boot 3.x requires a minimum of Java 17, but is optimized for Java 21 runtime enhancements, improved G1 garbage collection, and reduced memory overhead under heavy load.
  - **Modern Language Ergonomics**: Record patterns (JEP 440), pattern matching for switch (JEP 441), and Sequenced Collections (JEP 431) enable clean, expressive, and type-safe domain modeling and error-handling pipelines.
  - **Enterprise Senior Standards**: Java 21 is the current enterprise LTS standard, balancing long-term stability with modern concurrency and performance primitives required for the benchmark (200 constant VUs).
- **Justification over Java 25 (LTS)**:
  - **Ecosystem & Tooling Maturity**: Spring Boot 3.x and foundational Java libraries (bytecode manipulators like ByteBuddy, testing frameworks like Mockito, APM agents, and build plugins) have mature, battle-tested production certification on Java 21. Java 25 (the subsequent LTS) often introduces transient build-tool or annotation-processor compatibility friction.
  - **Sufficiency of Concurrency Primitives**: While Java 25 introduces unpinned synchronized blocks (JEP 491), our architecture relies on non-blocking HTTP clients (Spring `WebClient` / `RestClient` or `java.net.http.HttpClient`) and `java.util.concurrent` constructs, neither of which trigger carrier thread pinning in Java 21. Java 21 fully satisfies all high-concurrency (200 VUs) requirements.
  - **Evaluator & Container Portability**: Java 21 container base images (e.g., `eclipse-temurin:21-jre`) and developer tooling are universally available across local evaluation setups and CI pipelines, ensuring zero-friction reproducibility.

---

## ADR-002: Framework Stack & Concurrency Model (Spring MVC with Virtual Threads over WebFlux)

- **Status**: Accepted
- **Context**: The application must handle 200 concurrent virtual users under varying latencies (up to 50s delays) and fan out parallel requests to fetch similar product details. Spring Boot 3.x supports two primary concurrency paradigms for this: reactive programming via Spring WebFlux (Project Reactor / Netty), or imperative programming via Spring MVC enabled with Java 21 Virtual Threads (Project Loom / Tomcat).
- **Decision**: Adopt **Spring MVC with Java 21 Virtual Threads** (`spring.threads.virtual.enabled=true`) using Spring 6's **`RestClient`** (with HTTP connection pooling and strict timeouts) and parallel downstream fan-out orchestrated via a virtual-thread executor (`Executors.newVirtualThreadPerTaskExecutor()`).
- **Rationale**:
  - **Direct Coherence with ADR-001**: Capitalizes directly on the Java 21 decision. Virtual threads eliminate the traditional OS thread pool bottleneck in Spring MVC, allowing high-throughput concurrent I/O without reactive complexity.
  - **Code Clarity & Maintainability (Pillar 1)**: Eliminates the "colored function" problem and cognitive tax of Project Reactor (`Mono`/`Flux`). Imperative Java yields straightforward domain models, transparent stack traces, standard exception handling (`@ExceptionHandler`), and standard testing without reactive harness wrappers (`StepVerifier`).
  - **Throughput & Resilience (Pillars 2 & 3)**: During blocking I/O calls to downstream mocks (including the 50s delay scenario), virtual threads unmount from carrier threads, sustaining 200+ concurrent requests effortlessly without thread starvation.
  - **Clean Parallel Fan-out**: Querying multiple similar product details in parallel is cleanly orchestrated via `CompletableFuture` running on a virtual thread executor, achieving the same non-blocking fan-out as reactive `flatMap` while keeping domain entities and ports completely agnostic of reactive frameworks.
  - **Comparison with WebFlux**: WebFlux runs on Netty event loops and does not inherently leverage Java 21 virtual threads. Additionally, reactive abstractions propagate through all architectural boundaries (domain, application, persistence/client ports), creating framework coupling and increased testing friction.

---

## ADR-003: Downstream Partial Failure Handling (Graceful Degradation)

- **Status**: Accepted
- **Context**: When fetching details for multiple similar product IDs (e.g., `[1, 2, 5]` or `[1, 2, 6]`), upstream endpoints may return HTTP 404 (e.g. missing product 5), HTTP 500 (e.g. internal error on product 6), or exceed timeouts. We must determine whether to fail the entire composite request or return available products.
- **Decision**: Adopt a **graceful degradation** strategy. Any individual similar product detail request that fails (4xx, 5xx, or timeout) is logged defensively and omitted from the final list. The endpoint returns HTTP 200 with all successfully retrieved similar products.
- **Rationale**:
  - **User Experience & Conversion**: In e-commerce recommendations, presenting partial similar products is significantly better than rendering a broken UI block or returning a 500 error to the client.
  - **Fault Isolation**: A corrupted or missing individual catalog record does not cascade into a total outage for the recommendation endpoint.
  - **Contract Compliance**: [similarProducts.yaml](../similarProducts.yaml) specifies an array of `ProductDetail`; returning an array containing the available products preserves contract validity.

---

## ADR-004: Timeout Strategy for Upstream Dependencies

- **Status**: Accepted
- **Context**: The upstream mock service simulates severe latency spikes, including delays up to 50 seconds (e.g., `/product/10000`). Holding connections and waiting 50 seconds under 200 concurrent virtual users would exhaust connection pools and violate client SLAs.
- **Decision**: Enforce a **strict client timeout of 2.5 seconds** per upstream product detail request. Requests exceeding this threshold are canceled, and the item is gracefully omitted per ADR-003.
- **Rationale**:
  - **SLA & Latency Protection**: Client applications typically enforce timeouts between 3–5 seconds. Waiting 50s guarantees frontend connection aborts and unacceptable user wait times.
  - **Connection Pool Conservation**: Unbounding or setting high timeouts holds HTTP client sockets and connection leases open, risking pool exhaustion during traffic surges.
  - **Realistic Latency Budget**: 2.5 seconds safely accommodates normal responses (0–100ms) and moderate mock latencies (1s for product 100), while terminating extreme outliers (5s for product 1000 and 50s for product 10000).

---

## ADR-005: Root Product 404 vs. Empty List Semantics

- **Status**: Accepted
- **Context**: The endpoint must differentiate between a non-existent root product and an existing product that has no similar products available.
- **Decision**:
  - If the upstream call `GET /product/{productId}/similarids` returns HTTP `404 Not Found`, the application returns HTTP `404 Not Found`.
  - If the upstream call returns HTTP `200 OK` with an empty array `[]` (or if all similar products are omitted due to upstream failures), the application returns HTTP `200 OK` with an empty array `[]`.
- **Rationale**:
  - **Contract Fidelity**: [similarProducts.yaml](file:///c:/dev/repos/PHPCE/itx-backend-test/similarProducts.yaml) mandates returning `404 Product Not found` when the target product does not exist.
  - **Semantic Precision**: Requesting recommendations for a non-existent product indicates an invalid entity reference (404), whereas an existing product with no related items is a valid domain outcome (200 OK with `[]`).

---

## ADR-006: In-Memory Caching Strategy (Caffeine)

- **Status**: Accepted
- **Context**: The system is evaluated under high concurrency (200 constant virtual users in k6) making repetitive queries against the same set of products and similar ID relations.
- **Decision**: Implement in-memory caching using **Caffeine** with a short TTL (10–30 seconds) for both similar product IDs and individual product details.
- **Rationale**:
  - **Throughput & Concurrency**: Absorbs repetitive read traffic during load testing, reducing redundant HTTP roundtrips and significantly lowering P95/P99 response latencies.
  - **Data Freshness**: A short TTL (10–30s) guarantees catalog freshness (e.g. price and availability changes) without requiring complex distributed cache invalidation infrastructure.
  - **Zero Infrastructure Overhead**: Caffeine is the premier in-memory cache for the JVM (Window TinyLFU eviction) and requires no external processes (e.g., Redis), keeping the deployment self-contained and reproducible.

---
