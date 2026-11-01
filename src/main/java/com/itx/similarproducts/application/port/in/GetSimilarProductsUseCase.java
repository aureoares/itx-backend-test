package com.itx.similarproducts.application.port.in;

import com.itx.similarproducts.domain.model.ProductDetail;

import java.util.List;

/**
 * Inbound port interface for retrieving similar products.
 */
public interface GetSimilarProductsUseCase {

    /**
     * Retrieves the details of similar products for the given root product ID,
     * maintaining the similarity ranking order.
     *
     * @param productId the root product ID
     * @return ordered list of similar product details
     */
    List<ProductDetail> getSimilarProducts(String productId);
}
