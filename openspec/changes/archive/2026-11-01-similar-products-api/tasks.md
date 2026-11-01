# Tasks: Similar Products REST Aggregation API

## 1. Project Initialization & Architecture Setup

- [x] 1.1 Bootstrap Spring Boot 3.x project with Java 21, Spring Web, Actuator, and test dependencies in `pom.xml`, and verify the project compiles with `./mvnw compile` or `mvn compile`
- [x] 1.2 Configure `application.yml` with `server.port=5000`, `spring.threads.virtual.enabled=true`, and upstream base URL `http://localhost:3001`, and verify application startup on port 5000 via test
- [x] 1.3 Scaffold Hexagonal package structure (`domain`, `application`, `infrastructure`) and verify clean layer separation

## 2. Domain Models & Ports

- [x] 2.1 Implement immutable `ProductDetail` record (`id`, `name`, `price`, `availability`) and domain exceptions (`ProductNotFoundException`, `UpstreamServiceException`, `InvalidProductIdException`), and verify with unit tests
- [x] 2.2 Define inbound port `GetSimilarProductsUseCase` and outbound port `ProductCatalogPort` interfaces, and verify compilation

## 3. Outbound HTTP Adapter (Upstream Simulado Client)

- [x] 3.1 Configure Spring 6 `RestClient` bean with pooled connection management in `RestClientConfig`, and verify bean creation in Spring application context test
- [x] 3.2 Implement `SimuladoProductCatalogAdapter` implementing `ProductCatalogPort` querying port 3001 (`/product/{id}/similarids` and `/product/{id}`), and verify with `MockRestServiceServer` unit tests covering 200, 404, and 500 responses

## 4. Application Service & Concurrency Fan-out

- [x] 4.1 Configure virtual-thread-per-task executor bean in `VirtualThreadExecutorConfig`, and verify task execution on virtual threads via unit test
- [x] 4.2 Implement `SimilarProductsService` executing parallel product detail queries via `CompletableFuture` across virtual threads while strictly preserving initial similarity ranking order, and verify with unit test asserting concurrent duration and ordering
- [x] 4.3 Implement root product 404 propagation and empty list handling in `SimilarProductsService`, and verify with unit tests

## 5. Inbound Web Adapter & Error Translation

- [x] 5.1 Implement `SimilarProductsController` exposing `GET /product/{productId}/similar` on port 5000, and verify with `MockMvc` slice tests returning 200 OK with ordered product array
- [x] 5.2 Implement `GlobalExceptionHandler` mapping `ProductNotFoundException` to 404, blank ID to 400, and `UpstreamServiceException` to 502, and verify with `MockMvc` error tests
- [x] 5.3 Validate response schema matches `similarProducts.yaml` contract types, and verify JSON output via integration test

## 6. End-to-End Verification & Architecture Documentation

- [x] 6.1 Start test infrastructure with `docker-compose up -d simulado` and execute integration test verifying `GET /product/1/similar` returns products 2, 3, and 4 in order
- [x] 6.2 Execute verification test asserting `GET /product/999/similar` returns 404 Not Found against mock infrastructure
- [x] 6.3 Document architecture in `docs/domain.md`, `docs/topology.md`, and `docs/flows/similar-products.md`, and verify documentation files exist and link to ADRs
