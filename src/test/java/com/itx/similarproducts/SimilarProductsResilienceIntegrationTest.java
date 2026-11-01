package com.itx.similarproducts;

import com.itx.similarproducts.infrastructure.config.CacheConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.client.ExpectedCount;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class SimilarProductsResilienceIntegrationTest {

    private static MockRestServiceServer mockServer;

    @TestConfiguration
    static class ResilienceTestConfig {
        @Bean
        @Primary
        public RestClient testCatalogRestClient() {
            RestClient.Builder builder = RestClient.builder().baseUrl("http://localhost:3001");
            mockServer = MockRestServiceServer.bindTo(builder)
                .ignoreExpectOrder(true)
                .build();
            return builder.build();
        }
    }

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private CacheManager cacheManager;

    @BeforeEach
    void setUp() {
        mockServer.reset();
        cacheManager.getCache(CacheConfig.SIMILAR_PRODUCT_IDS_CACHE).clear();
        cacheManager.getCache(CacheConfig.PRODUCT_DETAILS_CACHE).clear();
    }

    @Test
    void shouldGracefullyOmitSimilarProductsWhen404And500Occur() {
        mockServer.expect(requestTo("http://localhost:3001/product/4/similarids"))
            .andExpect(method(HttpMethod.GET))
            .andRespond(withSuccess("[\"1\", \"2\", \"5\", \"6\"]", MediaType.APPLICATION_JSON));

        // 1 & 2 succeed
        mockServer.expect(requestTo("http://localhost:3001/product/1"))
            .andExpect(method(HttpMethod.GET))
            .andRespond(withSuccess("{\"id\":\"1\",\"name\":\"Shirt\",\"price\":9.99,\"availability\":true}", MediaType.APPLICATION_JSON));

        mockServer.expect(requestTo("http://localhost:3001/product/2"))
            .andExpect(method(HttpMethod.GET))
            .andRespond(withSuccess("{\"id\":\"2\",\"name\":\"Dress\",\"price\":19.99,\"availability\":true}", MediaType.APPLICATION_JSON));

        // 5 returns 404
        mockServer.expect(requestTo("http://localhost:3001/product/5"))
            .andExpect(method(HttpMethod.GET))
            .andRespond(withStatus(HttpStatus.NOT_FOUND));

        // 6 returns 500
        mockServer.expect(requestTo("http://localhost:3001/product/6"))
            .andExpect(method(HttpMethod.GET))
            .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

        ResponseEntity<String> response = restTemplate.getForEntity("/product/4/similar", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        String body = response.getBody();
        assertThat(body).isNotNull();

        // 1 and 2 present in order
        assertThat(body).contains("\"id\":\"1\"", "\"name\":\"Shirt\"");
        assertThat(body).contains("\"id\":\"2\"", "\"name\":\"Dress\"");
        int idx1 = body.indexOf("\"id\":\"1\"");
        int idx2 = body.indexOf("\"id\":\"2\"");
        assertThat(idx1).isLessThan(idx2);

        // 5 and 6 omitted
        assertThat(body).doesNotContain("\"id\":\"5\"");
        assertThat(body).doesNotContain("\"id\":\"6\"");

        mockServer.verify();
    }

    @Test
    void shouldOmitProductWhenTimeoutOccurs() {
        mockServer.expect(requestTo("http://localhost:3001/product/3/similarids"))
            .andExpect(method(HttpMethod.GET))
            .andRespond(withSuccess("[\"100\", \"1000\"]", MediaType.APPLICATION_JSON));

        mockServer.expect(requestTo("http://localhost:3001/product/100"))
            .andExpect(method(HttpMethod.GET))
            .andRespond(withSuccess("{\"id\":\"100\",\"name\":\"Watch\",\"price\":99.99,\"availability\":true}", MediaType.APPLICATION_JSON));

        mockServer.expect(requestTo("http://localhost:3001/product/1000"))
            .andExpect(method(HttpMethod.GET))
            .andRespond(request -> {
                throw new ResourceAccessException("Read timed out after 2500ms");
            });

        ResponseEntity<String> response = restTemplate.getForEntity("/product/3/similar", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        String body = response.getBody();
        assertThat(body).isNotNull();

        assertThat(body).contains("\"id\":\"100\"", "\"name\":\"Watch\"");
        assertThat(body).doesNotContain("\"id\":\"1000\"");

        mockServer.verify();
    }

    @Test
    void shouldReturnEmptyArrayWhenAllSimilarProductsFail() {
        mockServer.expect(requestTo("http://localhost:3001/product/8/similarids"))
            .andExpect(method(HttpMethod.GET))
            .andRespond(withSuccess("[\"5\", \"6\"]", MediaType.APPLICATION_JSON));

        mockServer.expect(requestTo("http://localhost:3001/product/5"))
            .andExpect(method(HttpMethod.GET))
            .andRespond(withStatus(HttpStatus.NOT_FOUND));

        mockServer.expect(requestTo("http://localhost:3001/product/6"))
            .andExpect(method(HttpMethod.GET))
            .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

        ResponseEntity<String> response = restTemplate.getForEntity("/product/8/similar", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo("[]");

        mockServer.verify();
    }

    @Test
    void shouldServeSubsequentRequestsFromCacheInSub50msAndSupportEviction() {
        // Initial lookup expectations (only once)
        mockServer.expect(ExpectedCount.once(), requestTo("http://localhost:3001/product/1/similarids"))
            .andExpect(method(HttpMethod.GET))
            .andRespond(withSuccess("[\"2\"]", MediaType.APPLICATION_JSON));

        mockServer.expect(ExpectedCount.once(), requestTo("http://localhost:3001/product/2"))
            .andExpect(method(HttpMethod.GET))
            .andRespond(withSuccess("{\"id\":\"2\",\"name\":\"Dress\",\"price\":19.99,\"availability\":true}", MediaType.APPLICATION_JSON));

        // First call populates cache
        ResponseEntity<String> firstResponse = restTemplate.getForEntity("/product/1/similar", String.class);
        assertThat(firstResponse.getStatusCode()).isEqualTo(HttpStatus.OK);

        // Second call should hit in-memory cache in sub-50ms without invoking mockServer
        long start = System.currentTimeMillis();
        ResponseEntity<String> cachedResponse = restTemplate.getForEntity("/product/1/similar", String.class);
        long elapsed = System.currentTimeMillis() - start;

        assertThat(cachedResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(cachedResponse.getBody()).isEqualTo(firstResponse.getBody());
        assertThat(elapsed).isLessThan(50);

        mockServer.verify();

        // Evict cache and assert fresh lookup is required
        cacheManager.getCache(CacheConfig.SIMILAR_PRODUCT_IDS_CACHE).clear();
        cacheManager.getCache(CacheConfig.PRODUCT_DETAILS_CACHE).clear();
        mockServer.reset();

        mockServer.expect(ExpectedCount.once(), requestTo("http://localhost:3001/product/1/similarids"))
            .andExpect(method(HttpMethod.GET))
            .andRespond(withSuccess("[\"2\"]", MediaType.APPLICATION_JSON));

        mockServer.expect(ExpectedCount.once(), requestTo("http://localhost:3001/product/2"))
            .andExpect(method(HttpMethod.GET))
            .andRespond(withSuccess("{\"id\":\"2\",\"name\":\"Dress\",\"price\":19.99,\"availability\":true}", MediaType.APPLICATION_JSON));

        ResponseEntity<String> freshResponse = restTemplate.getForEntity("/product/1/similar", String.class);
        assertThat(freshResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(freshResponse.getBody()).isEqualTo(firstResponse.getBody());

        mockServer.verify();
    }
}
