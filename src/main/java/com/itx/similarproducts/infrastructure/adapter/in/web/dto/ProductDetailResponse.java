package com.itx.similarproducts.infrastructure.adapter.in.web.dto;

import com.itx.similarproducts.domain.model.ProductDetail;

import java.math.BigDecimal;

/**
 * Web response DTO matching similarProducts.yaml ProductDetail schema.
 */
public record ProductDetailResponse(
    String id,
    String name,
    BigDecimal price,
    boolean availability
) {
    public static ProductDetailResponse fromDomain(ProductDetail product) {
        return new ProductDetailResponse(
            product.id(),
            product.name(),
            product.price(),
            product.availability()
        );
    }
}
