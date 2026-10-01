package com.resumeanalyzer.resume_analyzer;

import java.util.concurrent.ThreadPoolExecutor;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

@Configuration
public class AnalysisProcessingConfiguration {

    @Bean(name = "resumeAnalysisExecutor")
    public ThreadPoolTaskExecutor resumeAnalysisExecutor(
            @Value("${refinecv.analysis.executor.core-pool-size}") int corePoolSize,
            @Value("${refinecv.analysis.executor.max-pool-size}") int maxPoolSize,
            @Value("${refinecv.analysis.executor.queue-capacity}") int queueCapacity,
            @Value("${refinecv.analysis.executor.shutdown-wait-seconds}") int shutdownWaitSeconds
    ) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(corePoolSize);
        executor.setMaxPoolSize(maxPoolSize);
        executor.setQueueCapacity(queueCapacity);
        executor.setThreadNamePrefix("resume-analysis-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(shutdownWaitSeconds);
        return executor;
    }

    @Bean(name = "analysisTimeoutScheduler")
    public ThreadPoolTaskScheduler analysisTimeoutScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(1);
        scheduler.setThreadNamePrefix("resume-analysis-timeout-");
        scheduler.setRemoveOnCancelPolicy(true);
        return scheduler;
    }
}
