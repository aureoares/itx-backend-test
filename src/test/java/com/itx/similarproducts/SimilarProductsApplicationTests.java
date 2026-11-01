package com.itx.similarproducts;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
class SimilarProductsApplicationTests {

    @Value("${server.port}")
    private int serverPort;

    @Value("${spring.threads.virtual.enabled}")
    private boolean virtualThreadsEnabled;

    @Value("${upstream.catalog.base-url}")
    private String upstreamBaseUrl;

    @Value("${upstream.catalog.connect-timeout-ms}")
    private int connectTimeoutMs;

    @Value("${upstream.catalog.read-timeout-ms}")
    private int readTimeoutMs;

    @Value("${upstream.catalog.max-connections-per-route}")
    private int maxConnectionsPerRoute;

    @Value("${upstream.catalog.max-total-connections}")
    private int maxTotalConnections;

    @org.springframework.beans.factory.annotation.Autowired
    private org.springframework.web.client.RestClient catalogRestClient;

    @org.springframework.beans.factory.annotation.Autowired
    private org.springframework.cache.CacheManager cacheManager;

    @Test
    void contextLoadsAndConfigurationMatchesRequirements() {
        assertThat(serverPort).isEqualTo(5000);
        assertThat(virtualThreadsEnabled).isTrue();
        assertThat(upstreamBaseUrl).isEqualTo("http://localhost:3001");
        assertThat(connectTimeoutMs).isEqualTo(500);
        assertThat(readTimeoutMs).isEqualTo(2500);
        assertThat(maxConnectionsPerRoute).isEqualTo(200);
        assertThat(maxTotalConnections).isEqualTo(500);
        assertThat(catalogRestClient).isNotNull();
        assertThat(cacheManager).isNotNull();
        assertThat(cacheManager.getCacheNames()).containsExactlyInAnyOrder("similarProductIds", "productDetails");
    }
}
