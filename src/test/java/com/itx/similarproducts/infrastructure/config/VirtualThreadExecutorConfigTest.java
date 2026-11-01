package com.itx.similarproducts.infrastructure.config;

import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;

class VirtualThreadExecutorConfigTest {

    private final VirtualThreadExecutorConfig config = new VirtualThreadExecutorConfig();

    @Test
    void fanOutExecutor_runsTasksOnVirtualThreads() throws ExecutionException, InterruptedException, TimeoutException {
        try (ExecutorService executor = config.fanOutExecutor()) {
            CompletableFuture<Boolean> future = CompletableFuture.supplyAsync(
                () -> Thread.currentThread().isVirtual(),
                executor
            );

            boolean isVirtual = future.get(5, TimeUnit.SECONDS);

            assertThat(isVirtual).isTrue();
        }
    }
}
