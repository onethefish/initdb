package cn.fish.initDB.util;

import com.alibaba.cloud.ai.graph.NodeOutput;
import com.alibaba.cloud.ai.graph.OverAllState;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.ai.chat.messages.AbstractMessage;

import java.util.List;
import java.util.Optional;

@Slf4j
public class NodeOutputUtil {

    public static String extractResponse(NodeOutput nodeOutput) {
        if (nodeOutput == null) {
            return "No response generated.";
        }
        log.info("Received response: {}", nodeOutput);
        OverAllState state = nodeOutput.state();
        // Try "output" key first (common for ReactAgent)
        Optional<Object> output = state.value("output");
        if (output.isPresent()) {
            return String.valueOf(output.get());
        }

        // Fallback to "messages" key
        Optional<List<AbstractMessage>> messages = state.value("messages");
        if (messages.isPresent() && !messages.get().isEmpty()) {
            List<AbstractMessage> msgList = messages.get();
            return msgList.get(msgList.size() - 1).getText();
        }
        String result = state.toString();
        if (StringUtils.isBlank(result)) {
            result = "No response generated.";
        }
        return result;
    }
}
