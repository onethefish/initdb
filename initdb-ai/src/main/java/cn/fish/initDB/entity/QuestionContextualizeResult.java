package cn.fish.initDB.entity;

import cn.fish.initDB.chat.DbChatInputKeys;

/**
 * 问句补全结果：{@link #getStandalone()} 写入图初始状态 {@link DbChatInputKeys#STANDALONE}；
 * {@link #getDisplay()} 供流式 NDJSON 单独下发前端。
 */
public final class QuestionContextualizeResult {

    private final String standalone;
    private final String display;

    public QuestionContextualizeResult(String standalone, String display) {
        this.standalone = standalone != null ? standalone : "";
        this.display = display != null ? display : "";
    }

    public String getStandalone() {
        return standalone;
    }

    public String getDisplay() {
        return display;
    }
}
