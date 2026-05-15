package cn.fish.common.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 库表元数据（全表清单、表结构）本地缓存，前缀 {@code initdb.db.metadata-cache}。
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "initdb.db.metadata-cache")
public class DbMetadataCacheConfig {

    /**
     * 写入后过期时间（秒）。同一会话内 ReAct {@code get_all_tables}、直连表清单、表结构等元数据复用该 TTL，避免重复扫库。
     */
    private int ttlSeconds = 1800;
}
