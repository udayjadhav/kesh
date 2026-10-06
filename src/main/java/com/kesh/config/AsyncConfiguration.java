package com.kesh.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.SimpleAsyncTaskExecutor;
import org.springframework.core.task.TaskExecutor;

@Configuration
public class AsyncConfiguration {

    @Bean
    TaskExecutor keshTaskExecutor() {
        SimpleAsyncTaskExecutor executor = new SimpleAsyncTaskExecutor("kesh-");
        executor.setVirtualThreads(true);
        return executor;
    }
}
