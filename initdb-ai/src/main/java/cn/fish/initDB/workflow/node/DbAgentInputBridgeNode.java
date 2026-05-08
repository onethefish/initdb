package cn.fish.initDB.workflow.node;

import cn.fish.initDB.constants.DbChatInputConstants;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 将图初始状态中的 {@link DbChatInputConstants#STANDALONE}（由服务在图外补全后写入）转为 ReAct 子图所需的 {@code messages} / {@code input}，
 * 与 {@link com.alibaba.cloud.ai.graph.agent.Agent} 内部 {@code buildMessageInput} 的形态一致。
 */
public class DbAgentInputBridgeNode implements NodeAction {

    public static final String NODE_ID = "db_agent_input_bridge";

    @Override
    public Map<String, Object> apply(OverAllState state) throws Exception {
        String standalone = state.value(DbChatInputConstants.STANDALONE).map(Object::toString).orElse("");
        if (standalone != null) {
            standalone = standalone.strip();
        }
        UserMessage userMessage = new UserMessage(standalone);
        List<Message> messages = List.of(userMessage);
        Map<String, Object> out = new HashMap<>();
        out.put("messages", messages);
        out.put(OverAllState.DEFAULT_INPUT_KEY, standalone);
        return out;
    }
}
