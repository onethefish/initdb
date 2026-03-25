/*
 * Copyright 2026-2027 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package cn.fish.initDB.controller;

import cn.fish.initDB.bo.AiChatAskBo;
import cn.fish.initDB.controller.form.AiChatForm;
import cn.fish.initDB.entity.ChatRequest;
import cn.fish.initDB.entity.ChatResponse;
import cn.fish.initDB.service.DBAgentService;
import jakarta.servlet.http.HttpServletResponse;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.document.DocumentReader;
import org.springframework.ai.reader.tika.TikaDocumentReader;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.publisher.Flux;

import java.io.IOException;
import java.io.OutputStream;
import java.util.List;

@Slf4j
@Controller
@RequestMapping("/db")
public class DBAgentController {

    private final DBAgentService dbAgentService;

    private final HttpServletResponse httpServletResponse;
    private final VectorStore vectorStore;

    public DBAgentController(DBAgentService dbAgentService,
                             HttpServletResponse httpServletResponse,
                             VectorStore vectorStore) {
        this.dbAgentService = dbAgentService;
        this.httpServletResponse = httpServletResponse;
        this.vectorStore = vectorStore;
    }

    @ResponseBody
    @PostMapping("/chat")
    public ChatResponse chat(@RequestBody ChatRequest chatRequest) {
        return dbAgentService.chat(chatRequest);
    }

    @PostMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter chatStream(@RequestBody ChatRequest chatRequest) {
        // 1. 创建 Emitter，超时时间设为 0 表示永不超时（或者设置具体的毫秒数）
        SseEmitter emitter = new SseEmitter(0L);

        // 2. 获取 Flux 数据流
        Flux<String> flux = dbAgentService.chatStream(chatRequest);

        // 3. 订阅 Flux，将数据通过 emitter 发送出去
        flux.subscribe(
            data -> {
                try {
                    // 发送数据
                    emitter.send(data, MediaType.TEXT_HTML);
                } catch (IOException e) {
                    emitter.completeWithError(e);
                }
            },
            // 错误处理
            emitter::completeWithError,
            // 完成处理
            emitter::complete
        );

        return emitter;
    }


    @ResponseBody
    @GetMapping("/chat")
    public ChatResponse chatGet(@RequestParam("message") String message,
                                @RequestParam(value = "sessionId", required = false) String sessionId) {
        return dbAgentService.chat(new ChatRequest(message, sessionId));
    }


    /**
     * 提问
     *
     * @param chatCode 聊天编号
     * @param form     form
     */
    @SneakyThrows
    @PostMapping("/{chatCode}/ask")
    public void ask(@PathVariable String chatCode,
                    @RequestBody @Validated AiChatForm form) {

        AiChatAskBo bo = new AiChatAskBo()
            .setChatCode(chatCode)
            .setLastSessionId(form.getLastSessionId())
            .setPrompt(form.getPrompt())
            .setTags(form.getTags());

        try (OutputStream os = httpServletResponse.getOutputStream()) {
            httpServletResponse.setContentType(MediaType.TEXT_EVENT_STREAM_VALUE);

            dbAgentService.ask(os, bo);
        } catch (Exception e) {
            log.warn("智能客服提问异常", e);
        }
    }

    @SneakyThrows
    @PostMapping("/rag/importDocument")
    public void importDocument(MultipartFile file) {
        InputStreamResource resource = new InputStreamResource(file.getInputStream(), file.getOriginalFilename());
        DocumentReader reader = new TikaDocumentReader(resource);
        List<Document> documents = reader.get();
        List<Document> splitDocuments = new TokenTextSplitter().apply(documents);
        vectorStore.add(splitDocuments);
    }


}
