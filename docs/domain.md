# Domain Model & Ports

This document describes the core domain models, invariants, exceptions, and port contracts for the Similar Products aggregation service.

---

## 1. Architectural Role

In accordance with Hexagonal (Ports & Adapters) architecture, the domain layer represents the pure business concept of products, similarity collections, and aggregation rules, free from web framework, serialization, or transport infrastructure concerns.

Related Architecture Decision Records:
- [ADR-001: Java 21 LTS](technical-decisions.md#adr-001-java-version)
- [ADR-002: Spring MVC with Virtual Threads](docs/technical-decisions.md#adr-002-framework-stack--concurrency-model-spring-mvc-with-virtual-threads-over-webflux)
- [ADR-005: Root Product 404 vs Empty List Semantics](technical-decisions.md#adr-005-root-product-404-vs-empty-list-semantics)

---

## 2. Domain Models

### `ProductDetail` (Record)
An immutable record representing a catalog product item.

- **Package**: `com.itx.similarproducts.domain.model.ProductDetail`
- **Fields**:
  - `id` (`String`): Unique identifier of the product.
  - `name` (`String`): Display name of the product.
  - `price` (`BigDecimal`): Monetary price of the product.
  - `availability` (`boolean`): Stock availability flag.

#### Invariants & Validation Rules
- `id` must not be null or blank.
- `name` must not be null or blank.
- `price` must not be null and must be greater than or equal to zero.
- `availability` must not be null.

---

## 3. Domain Exceptions

The domain defines explicit unchecked exceptions to capture failure modes:

| Exception | Purpose | Mapped HTTP Status |
| :--- | :--- | :--- |
| `ProductNotFoundException` | Thrown when the root product does not exist upstream (`similarids` returns 404). | `404 Not Found` |
| `InvalidProductIdException` | Thrown when the provided product ID is blank, whitespace, or invalid. | `400 Bad Request` |
| `UpstreamServiceException` | Thrown when an upstream network failure, timeout, or 5xx error prevents resolution. | `502 Bad Gateway` |

---

## 4. Port Interfaces

### Inbound Port: `GetSimilarProductsUseCase`
Exposed to application drivers (web controllers, CLI, or test harnesses).

```java
package com.itx.similarproducts.application.port.in;

import com.itx.similarproducts.domain.model.ProductDetail;
import java.util.List;

public interface GetSimilarProductsUseCase {
    List<ProductDetail> getSimilarProducts(String productId);
}
```

### Outbound Port: `ProductCatalogPort`
Implemented by infrastructure adapters communicating with external catalog systems.

```java
package com.itx.similarproducts.application.port.out;

import com.itx.similarproducts.domain.model.ProductDetail;
import java.util.List;
import java.util.Optional;

public interface ProductCatalogPort {
    List<String> getSimilarProductIds(String productId);
    Optional<ProductDetail> getProductDetail(String productId);
}
```
