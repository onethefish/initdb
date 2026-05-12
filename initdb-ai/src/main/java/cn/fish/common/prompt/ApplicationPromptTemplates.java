/*
 * Copyright 2024-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package cn.fish.common.prompt;

import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * Loads prompt bodies from {@code classpath:prompts/*.txt} via {@link PromptTemplate}.
 */
@Component
public class ApplicationPromptTemplates {

	private static final String PROMPTS = "prompts/";

	private final PromptTemplate chartConversationSummary;
	private final PromptTemplate dbReactSystem;
	private final PromptTemplate dbReactDescription;
	private final PromptTemplate querySqlCheck;
	private final PromptTemplate agentVectorRagAnswer;
	private final PromptTemplate contextualizeRewriteSystem;
	private final PromptTemplate dbDirectNl2sql;
	private final PromptTemplate dbIntentRoute;
	private final PromptTemplate chartSessionTitle;

	public ApplicationPromptTemplates() {
		this.chartConversationSummary = tpl("chart_conversation_summary.txt");
		this.dbReactSystem = tpl("db_react_system.txt");
		this.dbReactDescription = tpl("db_react_description.txt");
		this.querySqlCheck = tpl("query_sql_check.txt");
		this.agentVectorRagAnswer = tpl("agent_vector_rag_answer.txt");
		this.contextualizeRewriteSystem = tpl("contextualize_rewrite_system.txt");
		this.dbDirectNl2sql = tpl("db_direct_nl2sql.txt");
		this.dbIntentRoute = tpl("db_intent_route.txt");
		this.chartSessionTitle = tpl("chart_session_title.txt");
	}

	private static PromptTemplate tpl(String fileName) {
		return new PromptTemplate(new ClassPathResource(PROMPTS + fileName));
	}

	public String renderChartConversationSummary(String history) {
		Map<String, Object> model = new HashMap<>(2);
		model.put("history", StrUtil.nullToEmpty(history));
		return chartConversationSummary.render(model);
	}

	public String dbReactSystemText() {
		return dbReactSystem.render();
	}

	public String dbReactDescriptionText() {
		return dbReactDescription.render().strip();
	}

	public String renderQuerySqlCheck(String query) {
		Map<String, Object> model = new HashMap<>(2);
		model.put("query", StrUtil.nullToEmpty(query));
		return querySqlCheck.render(model);
	}

	public String renderAgentVectorRagAnswer(String context, String query) {
		Map<String, Object> model = new HashMap<>(4);
		model.put("context", StrUtil.nullToEmpty(context));
		model.put("query", StrUtil.nullToEmpty(query));
		return agentVectorRagAnswer.render(model);
	}

	public String contextualizeRewriteSystemText() {
		return contextualizeRewriteSystem.render();
	}

	public String renderDbDirectNl2sql(String question, String tableCatalogJson) {
		Map<String, Object> model = new HashMap<>(4);
		model.put("question", StrUtil.nullToEmpty(question));
		model.put("table_catalog", ObjectUtil.defaultIfNull(tableCatalogJson, "[]"));
		return dbDirectNl2sql.render(model);
	}

	public String renderDbIntentRoute(String standalone) {
		Map<String, Object> model = new HashMap<>(2);
		model.put("standalone", StrUtil.nullToEmpty(standalone));
		return dbIntentRoute.render(model);
	}

	public String renderChartSessionTitle(String snippet) {
		Map<String, Object> model = new HashMap<>(2);
		model.put("snippet", StrUtil.nullToEmpty(snippet));
		return chartSessionTitle.render(model);
	}
}
