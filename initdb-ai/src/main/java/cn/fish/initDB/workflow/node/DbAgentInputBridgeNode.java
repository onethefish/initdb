package cn.fish.initDB.workflow.node;

import cn.fish.initDB.constants.WorkflowConstants;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 将图初始状态中的 {@link WorkflowConstants#STANDALONE}（由服务在图外补全后写入）转为 ReAct 子图所需的 {@code messages} / {@code input}，
 * 与 {@link com.alibaba.cloud.ai.graph.agent.Agent} 内部 {@code buildMessageInput} 的形态一致。
 */
@Component
public class DbAgentInputBridgeNode implements NodeAction {

    // StateGraph 节点 id，勿改字符串以免破坏 checkpoint / 流式帧匹配
    public static final String GRAPH_NODE_ID = "db_agent_input_bridge";

    @Override
    public Map<String, Object> apply(OverAllState state) {
        String standalone = state.value(WorkflowConstants.STANDALONE, "");
        UserMessage userMessage = new UserMessage(standalone);
        List<Message> messages = List.of(userMessage);
        Map<String, Object> out = new HashMap<>();
        out.put(WorkflowConstants.STATE_KEY_MESSAGES, messages);
        out.put(OverAllState.DEFAULT_INPUT_KEY, standalone);
        return out;
    }
}
