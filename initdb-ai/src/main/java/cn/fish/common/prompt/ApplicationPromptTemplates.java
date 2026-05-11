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

import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Loads all prompt bodies from {@code classpath:prompts/*.txt} via {@link PromptTemplate}.
 */
@Component
public class ApplicationPromptTemplates {

	private static final String PROMPTS = "prompts/";

	private final PromptTemplate chartConversationSummary;
	private final PromptTemplate chartCompressedUserMessage;
	private final PromptTemplate dbReactSystem;
	private final PromptTemplate dbReactDescription;
	private final PromptTemplate querySqlCheck;
	private final PromptTemplate agentVectorRagAnswer;
	private final PromptTemplate contextualizeRewriteSystem;
	private final PromptTemplate contextualizeUserBlock;
	private final PromptTemplate dbDirectNl2sql;
	private final PromptTemplate dbIntentRoute;
	private final PromptTemplate chartSessionTitle;

	public ApplicationPromptTemplates() {
		this.chartConversationSummary = tpl("chart_conversation_summary.txt");
		this.chartCompressedUserMessage = tpl("chart_compressed_user_message.txt");
		this.dbReactSystem = tpl("db_react_system.txt");
		this.dbReactDescription = tpl("db_react_description.txt");
		this.querySqlCheck = tpl("query_sql_check.txt");
		this.agentVectorRagAnswer = tpl("agent_vector_rag_answer.txt");
		this.contextualizeRewriteSystem = tpl("contextualize_rewrite_system.txt");
		this.contextualizeUserBlock = tpl("contextualize_user_block.txt");
		this.dbDirectNl2sql = tpl("db_direct_nl2sql.txt");
		this.dbIntentRoute = tpl("db_intent_route.txt");
		this.chartSessionTitle = tpl("chart_session_title.txt");
	}

	private static PromptTemplate tpl(String fileName) {
		return new PromptTemplate(new ClassPathResource(PROMPTS + fileName));
	}

	public String renderChartConversationSummary(String history) {
		return chartConversationSummary.render(Map.of("history", history == null ? "" : history));
	}

	public String renderChartCompressedUserMessage(String summary) {
		return chartCompressedUserMessage.render(Map.of("summary", summary == null ? "" : summary));
	}

	public String dbReactSystemText() {
		return dbReactSystem.render();
	}

	public String dbReactDescriptionText() {
		return dbReactDescription.render().strip();
	}

	public String renderQuerySqlCheck(String query) {
		return querySqlCheck.render(Map.of("query", query == null ? "" : query));
	}

	public String renderAgentVectorRagAnswer(String context, String query) {
		return agentVectorRagAnswer.render(Map.of(
				"context", context == null ? "" : context,
				"query", query == null ? "" : query));
	}

	public String contextualizeRewriteSystemText() {
		return contextualizeRewriteSystem.render();
	}

	public String renderContextualizeUserBlock(String historyBlock, String latestInput) {
		return contextualizeUserBlock.render(Map.of(
				"historyBlock", historyBlock == null ? "" : historyBlock,
				"latestInput", latestInput == null ? "" : latestInput));
	}

	public String renderDbDirectNl2sql(String question, String tableCatalogJson) {
		return dbDirectNl2sql.render(Map.of(
				"question", question == null ? "" : question,
				"table_catalog", tableCatalogJson == null ? "[]" : tableCatalogJson));
	}

	public String renderDbIntentRoute(String standalone) {
		return dbIntentRoute.render(Map.of("standalone", standalone == null ? "" : standalone));
	}

	public String renderChartSessionTitle(String snippet) {
		return chartSessionTitle.render(Map.of("snippet", snippet == null ? "" : snippet));
	}
}
