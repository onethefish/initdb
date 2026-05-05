package cn.fish.database.repository.impl;

import cn.fish.cloud.serva.web.exception.CommonException;
import cn.fish.database.repository.DataBaseRepository;
import cn.hutool.core.util.StrUtil;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.springframework.stereotype.Repository;

import javax.sql.DataSource;
import java.util.HashMap;

@Repository
public class DataBaseRepositoryImpl implements DataBaseRepository {

    private static final Cache<String, HikariDataSource> DATA_SOURCE_CACHE = Caffeine.newBuilder()
                                                                                     .maximumSize(128) // 缓存128个连接池
                                                                                     .build();

    @Override
    public void test(String url, String username, String password) {
        HikariConfig hikariConfig = createHikariConfig(url, username, password);
        try (HikariDataSource hikariDataSource = new HikariDataSource(hikariConfig)) {
            boolean running = hikariDataSource.isRunning();
        } catch (Exception e) {
            throw new CommonException("数据库连接失败" + e.getMessage(), e);
        } finally {
        }
    }

    @Override
    public HikariDataSource get(String id) {
        return DATA_SOURCE_CACHE.getIfPresent(id);
    }

    @Override
    public HikariDataSource add(String id, String url, String username, String password) {
        HikariDataSource hikariDataSource = createDataSource(url, username, password);
        DATA_SOURCE_CACHE.put(id, hikariDataSource);
        return hikariDataSource;
    }

    @Override
    public void remove(String id) {
        try {
            HikariDataSource ifPresent = DATA_SOURCE_CACHE.getIfPresent(id);
            dataSourceClose(ifPresent);
            DATA_SOURCE_CACHE.invalidate(id);
        } catch (Exception ignored) {

        }
    }

    @Override
    public void removeAll() {
        DATA_SOURCE_CACHE.asMap().forEach((key, value) -> {
            dataSourceClose(value);
        });
        DATA_SOURCE_CACHE.invalidateAll();
    }

    private static HikariConfig createHikariConfig(String url, String username, String password) {
        HikariConfig hikariConfig = new HikariConfig();
        hikariConfig.setJdbcUrl(url);
        if (StrUtil.isNotBlank(username)) {
            hikariConfig.setUsername(username);
        }
        if (StrUtil.isNotBlank(password)) {
            hikariConfig.setPassword(password);
        }
        hikariConfig.setMaximumPoolSize(5);
        hikariConfig.setMinimumIdle(1);
        return hikariConfig;
    }

    private static HikariDataSource createDataSource(String url, String username, String password) {
        HikariConfig hikariConfig = createHikariConfig(url, username, password);
        return new HikariDataSource(hikariConfig);
    }

    private static void dataSourceClose(DataSource dataSource) {
        if (dataSource == null) {
            return;
        }
        if (dataSource instanceof HikariDataSource hikariDataSource) {
            hikariDataSource.close();
        }
    }
}
