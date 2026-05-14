package cn.fish.common.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * {@code sql_check}（{@link cn.fish.initDB.workflow.agent.tool.QuerySqlCheckTool}）相关配置，前缀 {@code initdb.db.query-sql-check}。
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "initdb.db.query-sql-check")
public class QuerySqlCheckConfig {

    /**
     * 是否启用 JSqlParser 语法预检：解析失败则直接返回校验失败，不调用模型。
     */
    private boolean syntaxPreCheckEnabled = true;

    /**
     * 语法预检通过时是否跳过 LLM（显著省 token；不再做提示词中的语义/注入启发式检查）。
     */
    private boolean skipLlmWhenSyntaxOk = true;

    /**
     * 参与解析的 SQL 最大字符数（防止异常大文本；超出则视为校验失败且不解析）。
     */
    private int maxSqlChars = 256_000;
}
