package com.compass.bank.test.digitalbankapi.config;

import java.util.Arrays;
import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.lang.NonNull;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

@Configuration
@EnableAsync
public class AsyncConfiguration {

	public static final String NOTIFY_TASK_EXECUTOR = "notifyTaskExecutor";
	public static final String TRANSFER_API_EXECUTOR = "transferApiExecutor";
	public static final String IDEMPOTENCY_WAIT_SCHEDULER = "idempotencyWaitScheduler";

	private static final Logger log = LoggerFactory.getLogger(AsyncConfiguration.class);

	@Bean(name = IDEMPOTENCY_WAIT_SCHEDULER)
	public ThreadPoolTaskScheduler idempotencyWaitScheduler() {
		ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
		scheduler.setPoolSize(2);
		scheduler.setThreadNamePrefix("idem-wait-");
		scheduler.setWaitForTasksToCompleteOnShutdown(true);
		scheduler.setAwaitTerminationSeconds(10);
		scheduler.initialize();
		return scheduler;
	}

	@Bean(name = TRANSFER_API_EXECUTOR)
	public ThreadPoolTaskExecutor transferApiExecutor() {
		ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
		executor.setBeanName(TRANSFER_API_EXECUTOR);
		executor.setCorePoolSize(8);
		executor.setMaxPoolSize(32);
		executor.setQueueCapacity(500);
		executor.setThreadNamePrefix("transfer-api-");
		executor.setAllowCoreThreadTimeOut(false);
		executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
		executor.setWaitForTasksToCompleteOnShutdown(true);
		executor.setAwaitTerminationSeconds(60);
		executor.initialize();
		return executor;
	}

	@Bean(name = NOTIFY_TASK_EXECUTOR)
	public ThreadPoolTaskExecutor notifyTaskExecutor() {
		ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
		executor.setBeanName(NOTIFY_TASK_EXECUTOR);
		executor.setCorePoolSize(4);
		executor.setMaxPoolSize(16);
		executor.setQueueCapacity(200);
		executor.setThreadNamePrefix("notify-");
		executor.setAllowCoreThreadTimeOut(false);
		executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
		executor.setWaitForTasksToCompleteOnShutdown(true);
		executor.setAwaitTerminationSeconds(30);
		executor.initialize();
		return executor;
	}

	@Bean
	public AsyncConfigurer asyncConfigurer(
			@Qualifier(NOTIFY_TASK_EXECUTOR) ThreadPoolTaskExecutor notifyTaskExecutor) {
		return new AsyncConfigurer() {
			@Override
			@NonNull
			public Executor getAsyncExecutor() {
				return notifyTaskExecutor;
			}

			@Override
			@NonNull
			public AsyncUncaughtExceptionHandler getAsyncUncaughtExceptionHandler() {
				return (ex, method, params) -> log.error("Async method failed {} params={}", method.getName(),
						Arrays.toString(params), ex);
			}
		};
	}
}
