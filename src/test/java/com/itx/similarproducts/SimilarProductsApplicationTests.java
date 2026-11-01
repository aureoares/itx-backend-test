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

    @org.springframework.beans.factory.annotation.Autowired
    private org.springframework.web.client.RestClient catalogRestClient;

    @Test
    void contextLoadsAndConfigurationMatchesRequirements() {
        assertThat(serverPort).isEqualTo(5000);
        assertThat(virtualThreadsEnabled).isTrue();
        assertThat(upstreamBaseUrl).isEqualTo("http://localhost:3001");
        assertThat(catalogRestClient).isNotNull();
    }
}
