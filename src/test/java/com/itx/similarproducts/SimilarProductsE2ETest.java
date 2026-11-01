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

    @Test
    void getSimilarProducts_forProduct3_truncatesDelaysAndReturnsProduct100Within3Seconds() {
        long start = System.currentTimeMillis();
        ResponseEntity<String> response = restTemplate.getForEntity("/product/3/similar", String.class);
        long elapsed = System.currentTimeMillis() - start;

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        String body = response.getBody();
        assertThat(body).isNotNull();

        // Product 100 (1000ms delay) succeeds
        assertThat(body).contains("\"id\":\"100\"", "\"name\":\"Trousers\"");

        // Products 1000 (5000ms delay) and 10000 (50000ms delay) exceed 2500ms timeout and are omitted
        assertThat(body).doesNotContain("\"id\":\"1000\"");
        assertThat(body).doesNotContain("\"id\":\"10000\"");

        // Total elapsed time must be bounded by the 2.5s read timeout (+ small overhead, < 4000ms)
        assertThat(elapsed).isLessThan(4000);
    }

    @Test
    void getSimilarProducts_forProduct4_gracefullyOmits404Product5() {
        ResponseEntity<String> response = restTemplate.getForEntity("/product/4/similar", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        String body = response.getBody();
        assertThat(body).isNotNull();

        // Products 1 and 2 succeed
        assertThat(body).contains("\"id\":\"1\"", "\"name\":\"Shirt\"");
        assertThat(body).contains("\"id\":\"2\"", "\"name\":\"Dress\"");

        // Product 5 (404) is omitted
        assertThat(body).doesNotContain("\"id\":\"5\"");
    }

    @Test
    void getSimilarProducts_forProduct5_gracefullyOmits500Product6() {
        ResponseEntity<String> response = restTemplate.getForEntity("/product/5/similar", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        String body = response.getBody();
        assertThat(body).isNotNull();

        // Products 1 and 2 succeed
        assertThat(body).contains("\"id\":\"1\"", "\"name\":\"Shirt\"");
        assertThat(body).contains("\"id\":\"2\"", "\"name\":\"Dress\"");

        // Product 6 (500) is omitted
        assertThat(body).doesNotContain("\"id\":\"6\"");
    }
}
