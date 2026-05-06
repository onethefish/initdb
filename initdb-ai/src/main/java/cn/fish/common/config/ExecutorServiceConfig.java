package cn.fish.common.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@Configuration
public class ExecutorServiceConfig implements DisposableBean {

    /**
     * 专用线程池，用于数据库操作的并行处理
     */
    private ExecutorService initDbExecutor;

    @Bean(name = "initDbExecutor")
    public ExecutorService initDbExecutor() {
        // 初始化专用线程池，用于initDb 异步任务
        int corePoolSize = Math.max(4, Math.min(Runtime.getRuntime().availableProcessors() * 2, 16));
        log.info("initDb executor initialized with {} threads", corePoolSize);

        // 自定义线程工厂
        ThreadFactory threadFactory = new ThreadFactory() {
            private final AtomicInteger threadNumber = new AtomicInteger(1);

            @Override
            public Thread newThread(Runnable r) {
                Thread t = new Thread(r, "db-operation-" + threadNumber.getAndIncrement());
                t.setDaemon(false);
                if (t.getPriority() != Thread.NORM_PRIORITY) {
                    t.setPriority(Thread.NORM_PRIORITY);
                }
                return t;
            }
        };

        // 创建原生线程池
        initDbExecutor = new ThreadPoolExecutor(
                corePoolSize, corePoolSize, 60L, TimeUnit.SECONDS, new LinkedBlockingQueue<>(500), threadFactory,
                new ThreadPoolExecutor.CallerRunsPolicy());

        return initDbExecutor;
    }

    @Override
    public void destroy() {
        if (initDbExecutor != null && !initDbExecutor.isShutdown()) {
            log.info("Shutting down initDb executor...");

            // 记录关闭前的状态，便于排查问题
            if (initDbExecutor instanceof ThreadPoolExecutor tpe) {
                log.info("Executor Status before shutdown: [Queue Size: {}], [Active Count: {}], [Completed Tasks: {}]",
                        tpe.getQueue().size(), tpe.getActiveCount(), tpe.getCompletedTaskCount());
            }

            // 1. 停止接收新任务
            initDbExecutor.shutdown();

            try {
                // 2. 等待现有任务完成（包括队列中的）
                if (!initDbExecutor.awaitTermination(60, TimeUnit.SECONDS)) {
                    log.warn("Executor did not terminate in 60s. Forcing shutdown...");

                    // 3. 超时强行关闭
                    initDbExecutor.shutdownNow();

                    // 4. 再次确认是否关闭
                    if (!initDbExecutor.awaitTermination(10, TimeUnit.SECONDS)) {
                        log.error("Executor failed to terminate completely.");
                    }
                }
                else {
                    log.info("initDb executor terminated gracefully.");
                }
            } catch (InterruptedException e) {
                log.warn("Interrupted during executor shutdown. Forcing immediate shutdown.");
                initDbExecutor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }

}
