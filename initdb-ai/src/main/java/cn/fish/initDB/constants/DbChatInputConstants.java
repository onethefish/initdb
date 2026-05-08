package cn.fish.initDB.constants;

/**
 * DB 聊天图状态里与「用户侧输入」相关的键；问句补全在图外写入 {@link #STANDALONE}，{@link cn.fish.initDB.workflow.node.DbAgentInputBridgeNode} 再转为 ReAct 所需 messages。
 */
public final class DbChatInputConstants {

    private DbChatInputConstants() {
    }

    public static final String STANDALONE = "standalone";
}
