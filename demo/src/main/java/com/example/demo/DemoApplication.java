package com.example.demo;

import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

@SpringBootApplication
public class DemoApplication {
    private static volatile ThreadPoolTaskExecutor executor;

    public static void main(String[] args) {
        SpringApplication.run(DemoApplication.class, args);
    }

    @Bean
    public ThreadPoolTaskExecutor taskExecutor(
            @Value("${thread.pool.core-size:5}") int corePoolSize,
            @Value("${thread.pool.max-size:15}") int maxPoolSize,
            @Value("${thread.pool.queue-capacity:150}") int queueCapacity,
            @Value("${thread.pool.keep-alive:120}") int keepAliveSeconds) {
        executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(corePoolSize);     //核心线程数
        executor.setMaxPoolSize(maxPoolSize);       //最大线程数
        executor.setQueueCapacity(queueCapacity);   //任务队列容量
        executor.setKeepAliveSeconds(keepAliveSeconds);
        executor.setThreadNamePrefix("videoExecutor-");
        // 自定义拒绝策略（当任务无法执行时）
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        // 允许核心线程超时销毁（适用于低负载场景）
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(300); // 5分钟
        executor.initialize();
        return executor;
    }

    @PreDestroy
    public void onShutdown() {
        if (executor == null) {
            return;
        }
        System.out.println("应用关闭中，开始关闭线程池...");
        // 第一阶段：停止接受新任务并启动有序关闭
        executor.shutdown();
        try {
            // 第二阶段：等待正在执行的任务完成
            if (!executor.getThreadPoolExecutor().awaitTermination(5, TimeUnit.MINUTES)) {
                System.out.println("超时后仍有任务未完成，尝试强制关闭");
                // 第三阶段：尝试取消剩余任务
                executor.getThreadPoolExecutor().shutdownNow();
                // 第四阶段：再次等待任务响应中断
                if (!executor.getThreadPoolExecutor().awaitTermination(1, TimeUnit.MINUTES)) {
                    System.err.println("线程池未能完全终止");
                }
            }
        } catch (InterruptedException e) {
            System.err.println("关闭过程被中断: " + e.getMessage());
            executor.getThreadPoolExecutor().shutdownNow();
            Thread.currentThread().interrupt();
        } finally {
            System.out.println("线程池关闭完成---" +
                    "活跃线程: " + executor.getActiveCount() +
                    ", 队列大小: " + executor.getThreadPoolExecutor().getQueue().size());
        }
    }
}
