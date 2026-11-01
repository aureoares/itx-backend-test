package com.itx.similarproducts.infrastructure.adapter.out.http;

import com.itx.similarproducts.application.port.out.ProductCatalogPort;
import com.itx.similarproducts.domain.model.ProductDetail;
import com.itx.similarproducts.infrastructure.config.CacheConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.ExpectedCount;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

@SpringBootTest
class SimuladoProductCatalogAdapterCacheTest {

    private static MockRestServiceServer mockServer;

    @TestConfiguration
    static class TestConfig {
        @Bean
        @Primary
        public RestClient testCatalogRestClient() {
            RestClient.Builder builder = RestClient.builder().baseUrl("http://localhost:3001");
            mockServer = MockRestServiceServer.bindTo(builder).build();
            return builder.build();
        }
    }

    @Autowired
    private ProductCatalogPort productCatalogPort;

    @Autowired
    private CacheManager cacheManager;

    @BeforeEach
    void setUp() {
        mockServer.reset();
        cacheManager.getCache(CacheConfig.SIMILAR_PRODUCT_IDS_CACHE).clear();
        cacheManager.getCache(CacheConfig.PRODUCT_DETAILS_CACHE).clear();
    }

    @Test
    void getSimilarProductIds_isCached_whenCalledMultipleTimes() {
        mockServer.expect(ExpectedCount.once(), requestTo("http://localhost:3001/product/1/similarids"))
            .andExpect(method(HttpMethod.GET))
            .andRespond(withSuccess("[2, 3, 4]", MediaType.APPLICATION_JSON));

        List<String> firstCall = productCatalogPort.getSimilarProductIds("1");
        List<String> secondCall = productCatalogPort.getSimilarProductIds("1");

        assertThat(firstCall).containsExactly("2", "3", "4");
        assertThat(secondCall).containsExactly("2", "3", "4");

        mockServer.verify();
    }

    @Test
    void getProductDetail_isCached_whenCalledMultipleTimes() {
        String json = "{\"id\":\"2\",\"name\":\"Dress\",\"price\":19.99,\"availability\":true}";
        mockServer.expect(ExpectedCount.once(), requestTo("http://localhost:3001/product/2"))
            .andExpect(method(HttpMethod.GET))
            .andRespond(withSuccess(json, MediaType.APPLICATION_JSON));

        Optional<ProductDetail> firstCall = productCatalogPort.getProductDetail("2");
        Optional<ProductDetail> secondCall = productCatalogPort.getProductDetail("2");

        assertThat(firstCall).isPresent();
        assertThat(secondCall).isPresent();
        assertThat(firstCall.get().name()).isEqualTo("Dress");
        assertThat(secondCall.get().name()).isEqualTo("Dress");

        mockServer.verify();
    }
}
