package com.itx.similarproducts;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end verification tests running against the live Simulado mock service.
 */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = "upstream.catalog.base-url=${TEST_UPSTREAM_URL:http://simulado:80}"
)
class SimilarProductsE2ETest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    void getSimilarProducts_returnsProducts234InOrder_forProduct1() {
        ResponseEntity<String> response = restTemplate.getForEntity("/product/1/similar", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        String body = response.getBody();
        assertThat(body).isNotNull();

        // Verify product 2 details
        assertThat(body).contains("\"id\":\"2\"", "\"name\":\"Dress\"", "\"price\":19.99", "\"availability\":true");
        // Verify product 3 details
        assertThat(body).contains("\"id\":\"3\"", "\"name\":\"Blazer\"", "\"price\":29.99", "\"availability\":false");
        // Verify product 4 details
        assertThat(body).contains("\"id\":\"4\"", "\"name\":\"Boots\"", "\"price\":39.99", "\"availability\":true");

        // Verify ordering preservation: 2 before 3 before 4
        int idx2 = body.indexOf("\"id\":\"2\"");
        int idx3 = body.indexOf("\"id\":\"3\"");
        int idx4 = body.indexOf("\"id\":\"4\"");
        assertThat(idx2).isLessThan(idx3);
        assertThat(idx3).isLessThan(idx4);
    }

    @Test
    void getSimilarProducts_returns404NotFound_forMissingProduct999() {
        ResponseEntity<String> response = restTemplate.getForEntity("/product/999/similar", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).contains("Product not found: 999");
    }
}
