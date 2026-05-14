package cn.fish.common.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Chart 会话 checkpoint 自动压缩摘要（{@code chart_conversation_summary}）相关配置，前缀 {@code initdb.chart.summary}。
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "initdb.chart.summary")
public class ChartSummaryConfig {

    /**
     * 消息条数达到该值才尝试压缩摘要（越大触发越少、单轮成本触发越少）。
     */
    private int minMessages = 16;

    /**
     * 送入摘要模型的「待压缩历史」最大字符数（越小 prompt token 越低；过小可能丢早期要点）。
     */
    private int historyMaxChars = 8_000;

    /**
     * 切分时保留在 checkpoint 尾部、不参与本轮摘要的消息条数（越大则本轮 head 越短、摘要 prompt 越小）。
     */
    private int keepRecentMessages = 8;

    /**
     * 超长时是否保留历史文本的<strong>末尾</strong>（靠近未压缩的近期对话）；false 则保留开头（旧行为）。
     */
    private boolean truncateTailAligned = true;
}
