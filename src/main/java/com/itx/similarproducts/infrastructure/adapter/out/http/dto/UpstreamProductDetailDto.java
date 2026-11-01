package com.itx.similarproducts.infrastructure.adapter.out.http.dto;

import com.itx.similarproducts.domain.model.ProductDetail;

import java.math.BigDecimal;

/**
 * DTO matching upstream GET /product/{productId} response.
 */
public record UpstreamProductDetailDto(
    String id,
    String name,
    BigDecimal price,
    Boolean availability
) {
    public ProductDetail toDomain() {
        return new ProductDetail(
            id,
            name,
            price,
            availability != null && availability
        );
    }
}
