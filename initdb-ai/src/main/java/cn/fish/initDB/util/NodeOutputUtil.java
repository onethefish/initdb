package cn.fish.initDB.util;

import com.alibaba.cloud.ai.graph.NodeOutput;
import com.alibaba.cloud.ai.graph.OverAllState;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.commonmark.ext.gfm.tables.TablesExtension;
import org.commonmark.node.Node;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.html.HtmlRenderer;
import org.springframework.ai.chat.messages.AbstractMessage;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

@Slf4j
public class NodeOutputUtil {
    private static final Parser parser = Parser.builder()
                                               .extensions(Arrays.asList(TablesExtension.create()))
                                               .build();
    private static final HtmlRenderer renderer = HtmlRenderer.builder()
                                                             .extensions(Arrays.asList(TablesExtension.create()))
                                                             .build();

    public static String extractResponse(NodeOutput nodeOutput) {
        if (nodeOutput == null) {
            return "No response generated.";
        }
        if (log.isDebugEnabled()) {
            log.debug("Received response: {}", nodeOutput);
        }
        OverAllState state = nodeOutput.state();
        //         Try "output" key first (common for ReactAgent)
        Optional<Object> output = state.value("output");
        if (output.isPresent()) {
            return String.valueOf(output.get());
        }

        // Fallback to "messages" key
        Optional<List<AbstractMessage>> messages = state.value("messages");
        if (messages.isPresent()) {
            //            StringJoiner result = new StringJoiner("\n");
            List<AbstractMessage> msgList = messages.get();
            // 拿最后一条
            AbstractMessage abstractMessage = msgList.get(msgList.size() - 1);
            String text = abstractMessage.getText();
            Node node = parser.parse(text);
            String render = renderer.render(node);
            //            result.add(render);
            return render;
        }
        String result = state.toString();
        if (StringUtils.isBlank(result)) {
            result = "No response generated.";
        }
        return result;
    }
}
