package com.itx.similarproducts;

import com.itx.similarproducts.application.port.out.ProductCatalogPort;
import com.itx.similarproducts.domain.exception.ProductNotFoundException;
import com.itx.similarproducts.domain.model.ProductDetail;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class SimilarProductsIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @MockBean
    private ProductCatalogPort productCatalogPort;

    @Test
    void shouldReturnSimilarProductsConformingToOpenApiContract() {
        ProductDetail product2 = new ProductDetail("2", "Dress", new BigDecimal("19.99"), true);
        ProductDetail product3 = new ProductDetail("3", "Blazer", new BigDecimal("29.99"), false);
        ProductDetail product4 = new ProductDetail("4", "Boots", new BigDecimal("39.99"), true);

        when(productCatalogPort.getSimilarProductIds("1")).thenReturn(List.of("2", "3", "4"));
        when(productCatalogPort.getProductDetail("2")).thenReturn(Optional.of(product2));
        when(productCatalogPort.getProductDetail("3")).thenReturn(Optional.of(product3));
        when(productCatalogPort.getProductDetail("4")).thenReturn(Optional.of(product4));

        ResponseEntity<String> response = restTemplate.getForEntity("/product/1/similar", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getHeaders().getContentType()).isNotNull();
        assertThat(response.getHeaders().getContentType().includes(MediaType.APPLICATION_JSON)).isTrue();

        String body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body).startsWith("[").endsWith("]");
        // Verify contract schema types and ordering
        assertThat(body).contains("\"id\":\"2\"", "\"name\":\"Dress\"", "\"price\":19.99", "\"availability\":true");
        assertThat(body).contains("\"id\":\"3\"", "\"name\":\"Blazer\"", "\"price\":29.99", "\"availability\":false");
        assertThat(body).contains("\"id\":\"4\"", "\"name\":\"Boots\"", "\"price\":39.99", "\"availability\":true");

        // Verify order is preserved: index of 2 < index of 3 < index of 4
        int idx2 = body.indexOf("\"id\":\"2\"");
        int idx3 = body.indexOf("\"id\":\"3\"");
        int idx4 = body.indexOf("\"id\":\"4\"");
        assertThat(idx2).isLessThan(idx3);
        assertThat(idx3).isLessThan(idx4);
    }

    @Test
    void shouldReturn404WhenRootProductNotFound() {
        when(productCatalogPort.getSimilarProductIds("999")).thenThrow(new ProductNotFoundException("999"));

        ResponseEntity<String> response = restTemplate.getForEntity("/product/999/similar", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).contains("Product not found: 999");
    }

    @Test
    void shouldReturn200WithEmptyArrayWhenRootProductHasNoSimilarProducts() {
        when(productCatalogPort.getSimilarProductIds("10")).thenReturn(Collections.emptyList());

        ResponseEntity<String> response = restTemplate.getForEntity("/product/10/similar", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo("[]");
    }
}
