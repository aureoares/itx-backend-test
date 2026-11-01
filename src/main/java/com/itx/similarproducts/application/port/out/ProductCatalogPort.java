package com.itx.similarproducts.application.port.out;

import com.itx.similarproducts.domain.model.ProductDetail;

import java.util.List;
import java.util.Optional;

/**
 * Outbound port interface for querying upstream product catalog services.
 */
public interface ProductCatalogPort {

    /**
     * Gets similar product IDs for a given product ID in similarity ranking order.
     *
     * @param productId the root product ID
     * @return list of similar product IDs
     */
    List<String> getSimilarProductIds(String productId);

    /**
     * Gets details for a specific product ID.
     *
     * @param productId the product ID
     * @return an Optional containing the product detail, or empty if not found
     */
    Optional<ProductDetail> getProductDetail(String productId);
}
