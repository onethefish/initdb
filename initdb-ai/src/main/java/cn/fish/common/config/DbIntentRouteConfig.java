package cn.fish.common.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * DB 意图路由（{@code db_intent_route}）相关配置，前缀 {@code initdb.db.intent-route}。
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "initdb.db.intent-route")
public class DbIntentRouteConfig {

    /**
     * 是否启用启发式短路：高置信度输入直接判 DIRECT/REACT，跳过路由 LLM。
     */
    private boolean heuristicEnabled = true;

    /**
     * 启发式「纯闲聊」判定时，输入最大字符数（超过则不走闲聊规则，交给 LLM）。
     */
    private int chitchatMaxChars = 28;
}
