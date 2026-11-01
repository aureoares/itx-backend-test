package com.itx.similarproducts.domain.model;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * Immutable domain record representing a product detail.
 */
public record ProductDetail(
    String id,
    String name,
    BigDecimal price,
    boolean availability
) {
    public ProductDetail {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(name, "name must not be null");
        Objects.requireNonNull(price, "price must not be null");
        if (id.isBlank()) {
            throw new IllegalArgumentException("id must not be blank");
        }
        if (name.isBlank()) {
            throw new IllegalArgumentException("name must not be blank");
        }
    }

    public ProductDetail(String id, String name, double price, boolean availability) {
        this(id, name, BigDecimal.valueOf(price), availability);
    }
}
