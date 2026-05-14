package cn.fish.initDB.util;

import cn.hutool.core.util.StrUtil;

import java.util.Optional;
import java.util.regex.Pattern;

/**
 * {@code db_intent_route} 的保守启发式：仅在规则高置信时给出结果，否则交给 LLM。
 * <ul>
 *   <li>{@link Optional#of(Boolean) Optional.of(false)} → 走 ReAct；</li>
 *   <li>{@link Optional#of(Boolean) Optional.of(true)} → 走直连；</li>
 *   <li>{@link Optional#empty()} → 需调用模型。</li>
 * </ul>
 */
public final class DbIntentRouteHeuristics {

    /** 明显需要多步/元信息/分析类能力 → ReAct */
    private static final Pattern REACT_ANALYSIS_OR_META = Pattern.compile(
            "分析|统计|汇总|对比|关联|趋势|占比|归因|同比|环比|\\bjoin\\b|多表|几张表一起|哪些表|什么表|所有表|表清单|列出.*表名|表结构|字段含义|DDL|建表语句|explain|如何写|怎么写|改写|优化.*sql",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

    /** 典型单表明细拉取 → 直连（在未被上文 ReAct 类规则命中时使用） */
    private static final Pattern DIRECT_VERB_NEAR_TABLE = Pattern.compile(
            "(查|查询|查看|看下|看看|显示|列出).{0,48}?表.{0,24}?(的.{0,8})?(数据|记录|明细|列表|内容|行|有多少|几条|前\\s*\\d+)",
            Pattern.DOTALL);

    /** 未带「查」类动词、但「表 + 明细/条数」仍常为直连 */
    private static final Pattern DIRECT_TABLE_TAIL = Pattern.compile(
            "表.{0,20}(的.{0,8})?(数据|记录|明细|列表|有多少|几条|前\\s*\\d+)",
            Pattern.DOTALL);

    /** 指代不清或「全库/全部」类 → ReAct，避免误启发式直连 */
    private static final Pattern REACT_COREF_OR_BROAD = Pattern.compile(
            "那张|那个表|哪个表|哪张表|之前|上次|全部数据|所有数据|整张表|整张|随便|任意表");

    /** 短句纯寒暄 → ReAct（避免为无查数意图付路由模型） */
    private static final Pattern CHITCHAT_ONLY = Pattern.compile(
            "(?is)^(你好|您好|hi|hello|ok|thanks|thank\\s*you|哈喽|在吗|在嘛|早上好|下午好|晚上好|谢谢|多谢|感谢|好的|好滴|嗯|嗯嗯|明白|收到|请问|想问|帮忙|帮帮我)[!！。.…·\\s]*$");

    private DbIntentRouteHeuristics() {
    }

    /**
     * @param text            已 trim；宜与路由 LLM 使用同一裁剪上限后的文本
     * @param chitchatMaxLen  仅当长度 ≤ 该值时才尝试闲聊匹配
     * @return 有值则跳过 LLM
     */
    public static Optional<Boolean> maybeRouteWithoutLlm(String text, int chitchatMaxLen) {
        if (StrUtil.isBlank(text)) {
            return Optional.of(false);
        }
        int maxLen = Math.max(8, chitchatMaxLen);
        if (text.length() <= maxLen && CHITCHAT_ONLY.matcher(text).matches()) {
            return Optional.of(false);
        }
        if (REACT_ANALYSIS_OR_META.matcher(text).find()) {
            return Optional.of(false);
        }
        if (REACT_COREF_OR_BROAD.matcher(text).find()) {
            return Optional.of(false);
        }
        if (DIRECT_VERB_NEAR_TABLE.matcher(text).find() || DIRECT_TABLE_TAIL.matcher(text).find()) {
            return Optional.of(true);
        }
        return Optional.empty();
    }
}
