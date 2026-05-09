package cn.fish.web.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.web.servlet.config.annotation.AsyncSupportConfigurer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 为 Spring MVC 异步请求（如返回 {@link reactor.core.publisher.Flux} 的流式接口）配置线程池，
 * 避免使用默认 {@code SimpleAsyncTaskExecutor} 产生告警。
 */
@Configuration
public class MvcAsyncConfiguration {

    @Bean(destroyMethod = "shutdown")
    public ThreadPoolTaskExecutor mvcAsyncTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(64);
        executor.setQueueCapacity(500);
        executor.setThreadNamePrefix("mvc-async-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(60);
        executor.initialize();
        return executor;
    }

    @Bean
    public WebMvcConfigurer mvcAsyncWebMvcConfigurer(ThreadPoolTaskExecutor mvcAsyncTaskExecutor) {
        return new WebMvcConfigurer() {
            @Override
            public void configureAsyncSupport(AsyncSupportConfigurer configurer) {
                configurer.setTaskExecutor(mvcAsyncTaskExecutor);
                // 流式对话可能较长（毫秒）
                configurer.setDefaultTimeout(600_000L);
            }
        };
    }
}
