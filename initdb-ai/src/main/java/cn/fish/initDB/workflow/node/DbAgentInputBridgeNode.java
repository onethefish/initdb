package cn.fish.initDB.workflow.node;

import cn.fish.initDB.constants.InitDBConstants;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 将图初始状态中的 {@link InitDBConstants#STANDALONE}（由服务在图外补全后写入）转为 ReAct 子图所需的 {@code messages} / {@code input}，
 * 与 {@link com.alibaba.cloud.ai.graph.agent.Agent} 内部 {@code buildMessageInput} 的形态一致。
 */
public class DbAgentInputBridgeNode implements NodeAction {

    @Override
    public Map<String, Object> apply(OverAllState state) throws Exception {
        String standalone = state.value(InitDBConstants.STANDALONE).map(Object::toString).orElse("");
        if (standalone != null) {
            standalone = standalone.strip();
        }
        UserMessage userMessage = new UserMessage(standalone);
        List<Message> messages = List.of(userMessage);
        Map<String, Object> out = new HashMap<>();
        out.put(InitDBConstants.STATE_KEY_MESSAGES, messages);
        out.put(OverAllState.DEFAULT_INPUT_KEY, standalone);
        return out;
    }
}
