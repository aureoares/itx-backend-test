package com.itx.similarproducts.application.service;

import com.itx.similarproducts.application.port.out.ProductCatalogPort;
import com.itx.similarproducts.domain.exception.InvalidProductIdException;
import com.itx.similarproducts.domain.exception.ProductNotFoundException;
import com.itx.similarproducts.domain.model.ProductDetail;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SimilarProductsServiceTest {

    @Mock
    private ProductCatalogPort productCatalogPort;

    private ExecutorService fanOutExecutor;
    private SimilarProductsService service;

    @BeforeEach
    void setUp() {
        fanOutExecutor = Executors.newVirtualThreadPerTaskExecutor();
        service = new SimilarProductsService(productCatalogPort, fanOutExecutor);
    }

    @AfterEach
    void tearDown() {
        fanOutExecutor.close();
    }

    @Test
    void shouldReturnSimilarProductsInOriginalSimilarityOrderAndExecuteConcurrently() {
        when(productCatalogPort.getSimilarProductIds("1")).thenReturn(List.of("2", "3", "4"));

        ProductDetail product2 = new ProductDetail("2", "Dress", 19.99, true);
        ProductDetail product3 = new ProductDetail("3", "Blazer", 29.99, false);
        ProductDetail product4 = new ProductDetail("4", "Boots", 39.99, true);

        // Introduce simulated latency on each call to verify concurrent execution
        when(productCatalogPort.getProductDetail("2")).thenAnswer(inv -> {
            Thread.sleep(100);
            return Optional.of(product2);
        });
        when(productCatalogPort.getProductDetail("3")).thenAnswer(inv -> {
            Thread.sleep(100);
            return Optional.of(product3);
        });
        when(productCatalogPort.getProductDetail("4")).thenAnswer(inv -> {
            Thread.sleep(100);
            return Optional.of(product4);
        });

        long start = System.currentTimeMillis();
        List<ProductDetail> result = service.getSimilarProducts("1");
        long duration = System.currentTimeMillis() - start;

        // Verify ordering is preserved
        assertThat(result).hasSize(3);
        assertThat(result.get(0).id()).isEqualTo("2");
        assertThat(result.get(1).id()).isEqualTo("3");
        assertThat(result.get(2).id()).isEqualTo("4");

        // Verify parallel concurrency: total duration must be significantly less than 300ms sequential sum
        assertThat(duration).isLessThan(280);
    }

    @Test
    void shouldPropagateProductNotFoundExceptionWhenRootProductDoesNotExist() {
        when(productCatalogPort.getSimilarProductIds("999"))
            .thenThrow(new ProductNotFoundException("999"));

        assertThatThrownBy(() -> service.getSimilarProducts("999"))
            .isInstanceOf(ProductNotFoundException.class)
            .hasMessageContaining("999");
    }

    @Test
    void shouldReturnEmptyListWhenRootProductHasNoSimilarProducts() {
        when(productCatalogPort.getSimilarProductIds("10")).thenReturn(Collections.emptyList());

        List<ProductDetail> result = service.getSimilarProducts("10");

        assertThat(result).isEmpty();
        verify(productCatalogPort).getSimilarProductIds("10");
    }

    @Test
    void shouldThrowInvalidProductIdExceptionWhenProductIdIsBlankOrNull() {
        assertThatThrownBy(() -> service.getSimilarProducts(null))
            .isInstanceOf(InvalidProductIdException.class)
            .hasMessageContaining("blank");

        assertThatThrownBy(() -> service.getSimilarProducts("   "))
            .isInstanceOf(InvalidProductIdException.class)
            .hasMessageContaining("blank");
    }

    @Test
    void shouldOmitIndividualSimilarProductWhenDetailNotFound() {
        when(productCatalogPort.getSimilarProductIds("4")).thenReturn(List.of("1", "2", "5"));

        ProductDetail product1 = new ProductDetail("1", "Shirt", 9.99, true);
        ProductDetail product2 = new ProductDetail("2", "Dress", 19.99, true);

        when(productCatalogPort.getProductDetail("1")).thenReturn(Optional.of(product1));
        when(productCatalogPort.getProductDetail("2")).thenReturn(Optional.of(product2));
        when(productCatalogPort.getProductDetail("5")).thenReturn(Optional.empty());

        List<ProductDetail> result = service.getSimilarProducts("4");

        assertThat(result).containsExactly(product1, product2);
    }

    @Test
    void shouldOmitFailedProductWhenDetailCallThrowsExceptionDefensively() {
        when(productCatalogPort.getSimilarProductIds("5")).thenReturn(List.of("1", "2", "6"));

        ProductDetail product1 = new ProductDetail("1", "Shirt", 9.99, true);
        ProductDetail product2 = new ProductDetail("2", "Dress", 19.99, true);

        when(productCatalogPort.getProductDetail("1")).thenReturn(Optional.of(product1));
        when(productCatalogPort.getProductDetail("2")).thenReturn(Optional.of(product2));
        when(productCatalogPort.getProductDetail("6")).thenThrow(new RuntimeException("Simulated upstream 500 error"));

        List<ProductDetail> result = service.getSimilarProducts("5");

        // Failsafe: item 6 is omitted, items 1 and 2 returned in order
        assertThat(result).containsExactly(product1, product2);
    }

    @Test
    void shouldReturnEmptyListWhenAllSimilarProductsFailOrAreEmpty() {
        when(productCatalogPort.getSimilarProductIds("8")).thenReturn(List.of("5", "6"));

        when(productCatalogPort.getProductDetail("5")).thenReturn(Optional.empty());
        when(productCatalogPort.getProductDetail("6")).thenThrow(new RuntimeException("Simulated timeout"));

        List<ProductDetail> result = service.getSimilarProducts("8");

        assertThat(result).isEmpty();
    }
}
