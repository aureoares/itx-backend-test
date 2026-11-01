# Design: Similar Products REST Aggregation API

## Context

See [proposal.md](proposal.md) for background and motivation. This design establishes the core architecture for the Spring Boot application running on port 5000, adhering to the contracts in `similarProducts.yaml` and `existingApis.yaml`.

Key constraints:
- Java 21 LTS runtime.
- Spring Boot 3.x using Spring MVC with Virtual Threads (`spring.threads.virtual.enabled=true`) per [ADR-001](../../docs/technical-decisions.md#adr-001-java-version) and [ADR-002](../../docs/technical-decisions.md#adr-002-framework-stack--concurrency-model-spring-mvc-with-virtual-threads-over-webflux).
- Non-blocking parallel downstream fan-out.

## Goals / Non-Goals

**Goals:**
- Implement a clean **Hexagonal (Ports and Adapters)** architecture separating domain logic from web and HTTP transport.
- Orchestrate parallel fan-out for product detail retrieval across virtual threads, bounding total execution time by the slowest item rather than sequential accumulation.
- Preserve exact similarity ranking order regardless of individual thread completion order.
- Provide clean, centralized error handling translating domain outcomes to standard HTTP responses (200, 400, 404, 502).

**Non-Goals:**
- In-memory Caffeine caching (deferred to `ITX-002` / ADR-006).
- Partial failure graceful degradation and 2.5s strict timeout aborts (deferred to `ITX-002` / ADR-003, ADR-004).

## Package & Layer Structure (Hexagonal Architecture)

The codebase will follow a standard Hexagonal package structure:

```
com.itx.similarproducts
├── domain
│   ├── model
│   │   ├── ProductDetail.java       // Immutable record: id, name, price, availability
│   │   └── SimilarProducts.java     // Domain collection wrapper preserving ranking
│   └── exception
│       ├── ProductNotFoundException.java
│       ├── UpstreamServiceException.java
│       └── InvalidProductIdException.java
├── application
│   ├── port
│   │   ├── in
│   │   │   └── GetSimilarProductsUseCase.java  // Inbound port interface
│   │   └── out
│   │       └── ProductCatalogPort.java         // Outbound port for upstream queries
│   └── service
│       └── SimilarProductsService.java         // Coordinates parallel fan-out and ordering
└── infrastructure
    ├── adapter
    │   ├── in
    │   │   └── web
    │   │       ├── SimilarProductsController.java // REST controller on port 5000
    │   │       ├── GlobalExceptionHandler.java   // Translates domain exceptions to HTTP
    │   │       └── dto
    │   │           ├── ProductDetailResponse.java
    │   │           └── ErrorResponse.java
    │   └── out
    │       └── http
    │           ├── SimuladoProductCatalogAdapter.java // Outbound HTTP adapter for port 3001
    │           └── dto
    │               └── UpstreamProductDetailDto.java
    └── config
        ├── RestClientConfig.java              // Connection pool and HTTP factory setup
        └── VirtualThreadExecutorConfig.java   // Dedicated virtual-thread task executor
```

## Decisions

### 1. Hexagonal / Ports & Adapters Architecture
- **Decision**: Decouple domain entities (`ProductDetail`) and use cases from Spring Web MVC controllers and HTTP client adapters.
- **Rationale**: Isolates core aggregation and ranking logic from wire protocol changes (e.g. JSON annotations, HTTP headers). Simplifies isolated unit testing of business rules without starting Spring test contexts.
- **Alternatives Considered**: Traditional 3-tier layering (Controller -> Service -> Client). Rejected because it tends to leak Jackson annotations and HTTP transport concerns directly into core business models.

### 2. Concurrency Model: Virtual-Thread Fan-Out
- **Decision**: Implement parallel downstream fetching in `SimilarProductsService` using `CompletableFuture` dispatched on a dedicated Spring-managed virtual thread executor:
  ```java
  @Bean(name = "fanOutExecutor")
  public ExecutorService fanOutExecutor() {
      return Executors.newVirtualThreadPerTaskExecutor();
  }
  ```
- **Ordering Strategy**: The service maps each `productId` at index `i` into a future. Once `CompletableFuture.allOf(...)` completes, results are collected in the original index order:
  ```java
  List<CompletableFuture<ProductDetail>> futures = similarIds.stream()
      .map(id -> CompletableFuture.supplyAsync(() -> catalogPort.getProductDetail(id), fanOutExecutor))
      .toList();
  CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
  return futures.stream().map(CompletableFuture::join).toList();
  ```
- **Rationale**: Ensures non-blocking I/O and zero thread starvation while preserving similarity order deterministically.

### 3. HTTP Client with Spring 6 `RestClient`
- **Decision**: Configure Spring 6's synchronous fluent `RestClient` backed by an Apache HttpClient 5 or pooled JDK request factory.
- **Configuration**:
  - Base URL configurable via `upstream.catalog.base-url=http://localhost:3001`.
  - Max connections per route: 200; total max connections: 500 (matching 200 VU requirements).
- **Rationale**: `RestClient` offers a clean modern fluent API and blocks synchronously on virtual threads, allowing virtual threads to unmount during socket I/O.

### 4. Error Handling & Exception Translation
- **Decision**: Implement `@RestControllerAdvice` in `GlobalExceptionHandler` mapping:
  - `ProductNotFoundException` → `404 Not Found` with `{ "message": "Product not found" }`.
  - `InvalidProductIdException` / `MethodArgumentNotValidException` → `400 Bad Request`.
  - `UpstreamServiceException` (connection refused, 5xx from upstream `similarids`) → `502 Bad Gateway`.
- **Rationale**: Meets OpenAPI specifications defined in `similarProducts.yaml`.

## Risks / Trade-offs

- **[Risk] High Latency on Slow Downstream Call**: A single slow upstream product (e.g. 5s delay on product 1000) will slow down the overall response for product 2.
  - *Mitigation*: In this foundational change, parallel execution ensures duration is `max(t_i)` rather than `sum(t_i)`. `ITX-002` will add the strict 2.5s client timeout and graceful degradation to drop delayed items.
- **[Risk] Upstream Connection Exhaustion**: 200 concurrent requests making multiple parallel calls could saturate socket limits.
  - *Mitigation*: `RestClientConfig` defines a pooled connection manager configured for high concurrency.
