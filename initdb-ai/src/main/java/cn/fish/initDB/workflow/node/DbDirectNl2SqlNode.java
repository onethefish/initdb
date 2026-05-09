package cn.fish.initDB.workflow.node;

import cn.fish.common.prompt.ApplicationPromptTemplates;
import cn.fish.initDB.constants.InitDBConstants;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 直连查数链路：从用户表述生成单条 SELECT，或识别用户粘贴的 SQL。
 */
@Slf4j
@Component
public class DbDirectNl2SqlNode implements NodeAction {

    private static final Pattern CODE_FENCE_SQL = Pattern.compile("(?is)```(?:sql)?\\s*([\\s\\S]*?)```");
    private static final Pattern LEADING_SELECT = Pattern.compile("(?is)^\\s*(SELECT\\b[\\s\\S]+)$");

    private final ChatModel chatModel;
    private final ApplicationPromptTemplates applicationPromptTemplates;

    public DbDirectNl2SqlNode(ChatModel chatModel, ApplicationPromptTemplates applicationPromptTemplates) {
        this.chatModel = chatModel;
        this.applicationPromptTemplates = applicationPromptTemplates;
    }

    @Override
    public Map<String, Object> apply(OverAllState state) {
        String standalone = state.value(InitDBConstants.STANDALONE, "").trim();
        String sql = resolveSql(standalone);
        log.info("db direct nl2sql length={}", sql != null ? sql.length() : 0);
        Map<String, Object> out = new HashMap<>(2);
        out.put(InitDBConstants.STATE_KEY_GENERATED_SQL, sql);
        return out;
    }

    private String resolveSql(String text) {
        if (!StringUtils.hasText(text)) {
            return "SELECT 1 AS placeholder";
        }
        Matcher fence = CODE_FENCE_SQL.matcher(text);
        if (fence.find()) {
            return normalizeOneStatement(fence.group(1));
        }
        Matcher sel = LEADING_SELECT.matcher(text.trim());
        if (sel.matches()) {
            return normalizeOneStatement(sel.group(1));
        }
        try {
            String raw = chatModel.call(new Prompt(applicationPromptTemplates.renderDbDirectNl2sql(text)))
                                  .getResult()
                                  .getOutput()
                                  .getText();
            return normalizeOneStatement(stripNoise(raw));
        } catch (Exception e) {
            log.warn("nl2sql model failed", e);
            return "SELECT 1 AS placeholder";
        }
    }

    private static String stripNoise(String raw) {
        if (raw == null) {
            return "SELECT 1 AS placeholder";
        }
        Matcher fence = CODE_FENCE_SQL.matcher(raw);
        if (fence.find()) {
            return fence.group(1).trim();
        }
        return raw.trim();
    }

    private static String normalizeOneStatement(String sql) {
        if (sql == null) {
            return "SELECT 1 AS placeholder";
        }
        String s = sql.trim();
        if (s.endsWith(";")) {
            s = s.substring(0, s.length() - 1).trim();
        }
        int idx = s.toLowerCase().indexOf("\nselect ");
        if (idx > 0) {
            s = s.substring(idx).trim();
        }
        return s;
    }
}
