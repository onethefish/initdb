package cn.fish.common.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Chart 会话自动标题（{@code chart_session_title}）相关配置，前缀 {@code initdb.chart.session-title}。
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "initdb.chart.session-title")
public class ChartSessionTitleConfig {

    /**
     * 非占位会话名时，每隔多少次「流结束」才再次调用标题模型（越大越少调用）。
     */
    private int renamingIntervalStreams = 5;

    /**
     * 拼标题 snippet 时最多取多少条消息（从头部或尾部截取，见 {@link #snippetMessagesFromTail}）。
     */
    private int snippetMaxMessages = 8;

    /**
     * snippet 文本最大字符数（越小 prompt token 越低）。
     */
    private int snippetMaxChars = 1_200;

    /**
     * true：取 checkpoint 中<strong>最后</strong>若干条消息拼 snippet（更贴近当前话题）；false：取开头（旧行为）。
     */
    private boolean snippetMessagesFromTail = true;

    /**
     * snippet 超长时 true 保留<strong>末尾</strong>字符；false 保留开头并加 {@code ...[truncated]} 后缀（旧行为）。
     */
    private boolean snippetCharsTruncateTail = true;
}
