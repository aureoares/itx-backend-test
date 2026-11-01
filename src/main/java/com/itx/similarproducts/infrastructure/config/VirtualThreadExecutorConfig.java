package com.itx.similarproducts.infrastructure.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Configuration providing a virtual-thread-per-task executor for concurrent fan-out operations.
 */
@Configuration
public class VirtualThreadExecutorConfig {

    @Bean(name = "fanOutExecutor", destroyMethod = "close")
    public ExecutorService fanOutExecutor() {
        return Executors.newVirtualThreadPerTaskExecutor();
    }
}
