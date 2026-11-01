package com.itx.similarproducts.application.service;

import com.itx.similarproducts.application.port.in.GetSimilarProductsUseCase;
import com.itx.similarproducts.application.port.out.ProductCatalogPort;
import com.itx.similarproducts.domain.exception.InvalidProductIdException;
import com.itx.similarproducts.domain.model.ProductDetail;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;

/**
 * Application service implementing GetSimilarProductsUseCase.
 * Dispatches detail requests concurrently across virtual threads while strictly preserving
 * similarity ranking order and gracefully omitting failing items.
 */
@Service
public class SimilarProductsService implements GetSimilarProductsUseCase {

    private static final Logger log = LoggerFactory.getLogger(SimilarProductsService.class);

    private final ProductCatalogPort productCatalogPort;
    private final ExecutorService fanOutExecutor;

    public SimilarProductsService(
        ProductCatalogPort productCatalogPort,
        @Qualifier("fanOutExecutor") ExecutorService fanOutExecutor
    ) {
        this.productCatalogPort = productCatalogPort;
        this.fanOutExecutor = fanOutExecutor;
    }

    @Override
    public List<ProductDetail> getSimilarProducts(String productId) {
        if (productId == null || productId.isBlank()) {
            throw new InvalidProductIdException("Product ID must not be blank");
        }

        List<String> similarIds = productCatalogPort.getSimilarProductIds(productId);
        if (similarIds == null || similarIds.isEmpty()) {
            return Collections.emptyList();
        }

        // Fan out detail queries concurrently across virtual threads
        List<CompletableFuture<Optional<ProductDetail>>> futures = similarIds.stream()
            .map(id -> CompletableFuture.supplyAsync(
                () -> productCatalogPort.getProductDetail(id),
                fanOutExecutor
            ).exceptionally(ex -> {
                log.warn("Failed retrieving product detail for similar product {}: {}", id, ex.getMessage());
                return Optional.empty();
            }))
            .toList();

        // Wait for all concurrent queries to finish
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

        // Reassemble in original similarity order, omitting missing or failed products
        return futures.stream()
            .map(CompletableFuture::join)
            .flatMap(Optional::stream)
            .toList();
    }
}
