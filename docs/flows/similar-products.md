# Execution Flow: Similar Products Retrieval

This document outlines the detailed request/response lifecycle for retrieving similar products via `GET /product/{productId}/similar`.

---

## 1. Sequence Diagram

```mermaid
sequenceDiagram
    autonumber
    actor Client as Front-End Client
    participant Controller as SimilarProductsController
    participant Service as SimilarProductsService
    participant Executor as VirtualThreadExecutor
    participant Port as ProductCatalogPort
    participant Simulado as Upstream Simulado (:3001)

    Client->>Controller: GET /product/{productId}/similar
    alt productId is blank or whitespace
        Controller-->>Client: 400 Bad Request
    else Valid productId
        Controller->>Service: getSimilarProducts(productId)
        Service->>Port: getSimilarProductIds(productId)
        Port->>Simulado: GET /product/{productId}/similarids

        alt Root product not found (404)
            Simulado-->>Port: 404 Not Found
            Port-->>Service: throw ProductNotFoundException
            Service-->>Controller: throw ProductNotFoundException
            Controller-->>Client: 404 Not Found
        else Upstream error / network unreachable
            Simulado-->>Port: 500 / Network Error
            Port-->>Service: throw UpstreamServiceException
            Service-->>Controller: throw UpstreamServiceException
            Controller-->>Client: 502 Bad Gateway
        else Root product exists with zero similar products
            Simulado-->>Port: 200 OK []
            Port-->>Service: []
            Service-->>Controller: []
            Controller-->>Client: 200 OK []
        else Root product exists with similar IDs (e.g., ["2", "3", "4"])
            Simulado-->>Port: 200 OK ["2", "3", "4"]
            Port-->>Service: ["2", "3", "4"]
            
            par Fetch Detail for ID 2
                Service->>Executor: submit(fetchDetail("2"))
                Executor->>Port: getProductDetail("2")
                Port->>Simulado: GET /product/2
                Simulado-->>Port: 200 OK ProductDetail(2)
                Port-->>Executor: ProductDetail(2)
            and Fetch Detail for ID 3
                Service->>Executor: submit(fetchDetail("3"))
                Executor->>Port: getProductDetail("3")
                Port->>Simulado: GET /product/3
                Simulado-->>Port: 200 OK ProductDetail(3)
                Port-->>Executor: ProductDetail(3)
            and Fetch Detail for ID 4
                Service->>Executor: submit(fetchDetail("4"))
                Executor->>Port: getProductDetail("4")
                Port->>Simulado: GET /product/4
                Simulado-->>Port: 200 OK ProductDetail(4)
                Port-->>Executor: ProductDetail(4)
            end

            Note over Service: CompletableFuture.allOf().join()<br/>Preserve original index order [2, 3, 4]
            Service-->>Controller: List[ProductDetail(2), ProductDetail(3), ProductDetail(4)]
            Controller-->>Client: 200 OK [ProductDetailResponse(2), ProductDetailResponse(3), ProductDetailResponse(4)]
        end
    end
```

---

## 2. Key Execution Phases

### Phase 1: Input Validation
- The `SimilarProductsController` inspects `productId`.
- If the ID is empty or whitespace-only, `InvalidProductIdException` is thrown and mapped to `400 Bad Request`.

### Phase 2: Root Product Resolution
- `SimilarProductsService` invokes `ProductCatalogPort.getSimilarProductIds(productId)`.
- If the upstream service returns HTTP 404, `ProductNotFoundException` propagates to the controller, returning `404 Not Found`.
- If the upstream returns `[]`, the service immediately short-circuits and returns an empty list `[]`, avoiding unnecessary fan-out.

### Phase 3: Parallel Virtual-Thread Fan-Out
- Each similar product ID is mapped to a `CompletableFuture` executed asynchronously by `fanOutExecutor` (a dedicated Java 21 `Executors.newVirtualThreadPerTaskExecutor()`).
- Because tasks run on lightweight virtual threads, blocking network I/O does not pin carrier platform threads.
- `CompletableFuture.allOf(...)` coordinates concurrent joining across all dispatched tasks.

### Phase 4: Order Preservation
- Results are reassembled into the response list using the initial stream index order:
  ```java
  List<CompletableFuture<Optional<ProductDetail>>> futures = similarIds.stream()
      .map(id -> CompletableFuture.supplyAsync(() -> catalogPort.getProductDetail(id), fanOutExecutor))
      .toList();

  CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

  return futures.stream()
      .map(CompletableFuture::join)
      .flatMap(Optional::stream)
      .toList();
  ```
- This guarantees that the ranking order returned by upstream `/similarids` is preserved deterministically in the final response.

---

## 3. Related Architecture Decisions

- [ADR-002: Framework Stack & Concurrency Model](../technical-decisions.md#adr-002-framework-stack--concurrency-model-spring-mvc-with-virtual-threads-over-webflux)
- [ADR-005: Root Product 404 vs Empty List Semantics](../technical-decisions.md#adr-005-root-product-404-vs-empty-list-semantics)
