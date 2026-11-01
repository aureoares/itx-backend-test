package com.itx.similarproducts.infrastructure.config;

import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
import org.apache.hc.core5.util.Timeout;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * Configuration for Spring 6 RestClient with pooled HTTP connections and timeout settings.
 */
@Configuration
public class RestClientConfig {

    @Value("${upstream.catalog.base-url:http://localhost:3001}")
    private String upstreamBaseUrl;

    @Value("${upstream.catalog.connect-timeout-ms:1000}")
    private int connectTimeoutMs;

    @Value("${upstream.catalog.read-timeout-ms:5000}")
    private int readTimeoutMs;

    @Value("${upstream.catalog.max-total-connections:500}")
    private int maxTotalConnections;

    @Value("${upstream.catalog.max-connections-per-route:200}")
    private int maxConnectionsPerRoute;

    @Bean
    public RestClient catalogRestClient(RestClient.Builder builder) {
        PoolingHttpClientConnectionManager connectionManager = new PoolingHttpClientConnectionManager();
        connectionManager.setMaxTotal(maxTotalConnections);
        connectionManager.setDefaultMaxPerRoute(maxConnectionsPerRoute);

        RequestConfig requestConfig = RequestConfig.custom()
            .setConnectTimeout(Timeout.ofMilliseconds(connectTimeoutMs))
            .setResponseTimeout(Timeout.ofMilliseconds(readTimeoutMs))
            .build();

        CloseableHttpClient httpClient = HttpClients.custom()
            .setConnectionManager(connectionManager)
            .setDefaultRequestConfig(requestConfig)
            .build();

        HttpComponentsClientHttpRequestFactory requestFactory =
            new HttpComponentsClientHttpRequestFactory(httpClient);

        return builder
            .baseUrl(upstreamBaseUrl)
            .requestFactory(requestFactory)
            .build();
    }
}
