package com.itx.similarproducts.infrastructure.adapter.in.web;

import com.itx.similarproducts.application.port.in.GetSimilarProductsUseCase;
import com.itx.similarproducts.domain.exception.InvalidProductIdException;
import com.itx.similarproducts.infrastructure.adapter.in.web.dto.ProductDetailResponse;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * REST controller exposing GET /product/{productId}/similar conforming to similarProducts.yaml.
 */
@RestController
@RequestMapping("/product")
public class SimilarProductsController {

    private final GetSimilarProductsUseCase getSimilarProductsUseCase;

    public SimilarProductsController(GetSimilarProductsUseCase getSimilarProductsUseCase) {
        this.getSimilarProductsUseCase = getSimilarProductsUseCase;
    }

    @GetMapping(value = "/{productId}/similar", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<List<ProductDetailResponse>> getSimilarProducts(@PathVariable("productId") String productId) {
        if (productId == null || productId.trim().isEmpty()) {
            throw new InvalidProductIdException("Product ID must not be blank");
        }

        List<ProductDetailResponse> response = getSimilarProductsUseCase.getSimilarProducts(productId)
            .stream()
            .map(ProductDetailResponse::fromDomain)
            .toList();

        return ResponseEntity.ok(response);
    }
}
