package com.aichat.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

@Configuration
@EnableAsync
public class ChatExecutorConfigure implements AsyncConfigurer {

    private static final Logger log = LoggerFactory.getLogger(ChatExecutorConfigure.class);

    @Value("${chat.executor.config.core-size:10}")
    private int coreSize;

    @Value("${chat.executor.config.max-size:20}")
    private int maxSize;

    @Value("${chat.executor.config.queue-size:100}")
    private int queueSize;

    @Value("${chat.executor.config.keep-alive-seconds:30}")
    private int keepAliveSeconds;


    @Bean(name = "chatExecutor")
    public Executor chatExecutor(){
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(coreSize);
        executor.setMaxPoolSize(maxSize);
        executor.setQueueCapacity(queueSize);
        executor.setKeepAliveSeconds(keepAliveSeconds);

        executor.setAllowCoreThreadTimeOut(true);
        executor.setThreadNamePrefix("Chat-Worker-");

        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(60);

        executor.initialize();
        return executor;
    }

    @Override
    public AsyncUncaughtExceptionHandler getAsyncUncaughtExceptionHandler(){
        return ((ex, method, params) -> log.error("异步任务执行异常: 方法 {}", method.getName(), ex));
    }

    @Override
    public Executor getAsyncExecutor() {
        return chatExecutor();
    }
}
