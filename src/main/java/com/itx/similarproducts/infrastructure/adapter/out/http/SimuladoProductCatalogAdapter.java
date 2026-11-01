package com.itx.similarproducts.infrastructure.adapter.out.http;

import com.itx.similarproducts.application.port.out.ProductCatalogPort;
import com.itx.similarproducts.domain.exception.ProductNotFoundException;
import com.itx.similarproducts.domain.exception.UpstreamServiceException;
import com.itx.similarproducts.domain.model.ProductDetail;
import com.itx.similarproducts.infrastructure.adapter.out.http.dto.UpstreamProductDetailDto;
import com.itx.similarproducts.infrastructure.config.CacheConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * Outbound HTTP adapter implementing ProductCatalogPort using Spring RestClient.
 */
@Component
public class SimuladoProductCatalogAdapter implements ProductCatalogPort {

    private static final Logger log = LoggerFactory.getLogger(SimuladoProductCatalogAdapter.class);

    private static final ParameterizedTypeReference<List<String>> STRING_LIST_TYPE =
        new ParameterizedTypeReference<>() {};

    private final RestClient restClient;

    public SimuladoProductCatalogAdapter(RestClient catalogRestClient) {
        this.restClient = catalogRestClient;
    }

    @Override
    @Cacheable(value = CacheConfig.SIMILAR_PRODUCT_IDS_CACHE, key = "#productId")
    public List<String> getSimilarProductIds(String productId) {
        try {
            List<String> ids = restClient.get()
                .uri("/product/{productId}/similarids", productId)
                .retrieve()
                .onStatus(status -> status.isSameCodeAs(HttpStatus.NOT_FOUND), (req, res) -> {
                    throw new ProductNotFoundException(productId);
                })
                .onStatus(HttpStatusCode::is5xxServerError, (req, res) -> {
                    throw new UpstreamServiceException("Upstream 5xx error on similar IDs for product " + productId);
                })
                .body(STRING_LIST_TYPE);

            return ids != null ? ids : Collections.emptyList();
        } catch (ProductNotFoundException e) {
            throw e;
        } catch (HttpClientErrorException.NotFound e) {
            throw new ProductNotFoundException(productId);
        } catch (UpstreamServiceException e) {
            throw e;
        } catch (HttpServerErrorException | ResourceAccessException e) {
            throw new UpstreamServiceException("Failed to retrieve similar IDs for product " + productId + ": " + e.getMessage(), e);
        } catch (Exception e) {
            throw new UpstreamServiceException("Unexpected error retrieving similar IDs for product " + productId + ": " + e.getMessage(), e);
        }
    }

    @Override
    @Cacheable(
        value = CacheConfig.PRODUCT_DETAILS_CACHE,
        key = "#productId",
        unless = "#result == null"
    )
    public Optional<ProductDetail> getProductDetail(String productId) {
        try {
            UpstreamProductDetailDto dto = restClient.get()
                .uri("/product/{productId}", productId)
                .retrieve()
                .onStatus(status -> status.isSameCodeAs(HttpStatus.NOT_FOUND), (req, res) -> {
                    throw new HttpClientErrorException(HttpStatus.NOT_FOUND);
                })
                .body(UpstreamProductDetailDto.class);

            if (dto == null || dto.id() == null) {
                return Optional.empty();
            }
            return Optional.of(dto.toDomain());
        } catch (HttpClientErrorException e) {
            return Optional.empty();
        } catch (HttpServerErrorException e) {
            log.warn("Upstream 5xx error for product detail {}: status {}", productId, e.getStatusCode());
            return Optional.empty();
        } catch (ResourceAccessException e) {
            log.warn("Upstream timeout or connection error for product detail {}: {}", productId, e.getMessage());
            return Optional.empty();
        } catch (Exception e) {
            log.warn("Unexpected downstream error for product detail {}: {}", productId, e.getMessage());
            return Optional.empty();
        }
    }
}
