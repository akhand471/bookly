package com.bookly.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * Configures asynchronous task execution (used by {@link com.bookly.service.NotificationService})
 * and scheduled jobs (used by {@link com.bookly.service.ReminderSchedulerService}).
 *
 * <p>A dedicated thread pool ({@code notificationExecutor}) is used for notification sends
 * so that a slow mail server cannot block the HTTP request threads.</p>
 */
@Configuration
@EnableAsync
@EnableScheduling
public class AsyncConfig {

    /**
     * Thread pool used for all {@code @Async} notification sends.
     * Sized conservatively — notifications are I/O-bound and low-volume.
     */
    @Bean(name = "notificationExecutor")
    public Executor notificationExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(5);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("notification-");
        executor.initialize();
        return executor;
    }
}
