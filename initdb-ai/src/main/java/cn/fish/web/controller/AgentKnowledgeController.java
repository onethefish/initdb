package cn.fish.web.controller;

import cn.fish.cloud.serva.file.operate.ServaFile;
import cn.fish.cloud.serva.web.controller.BaseController;
import cn.fish.cloud.serva.web.response.ResponseResult;
import cn.fish.knowledge.business.KnowledgeBo;
import cn.fish.knowledge.service.AgentKnowledgeService;
import cn.fish.web.form.KnowledgeForm;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;

import javax.validation.Valid;
import java.io.IOException;


/**
 * 知识库管理控制器
 *
 * @author 余康云
 * @since 2025/2/1 11:33
 */

@Slf4j
@RequestMapping("/agent-knowledge")
@RequiredArgsConstructor
public class AgentKnowledgeController extends BaseController {
    private final AgentKnowledgeService agentKnowledgeService;
    private final ServaFile servaFile;


    /**
     * 新增知识库管理
     *
     * @param file file
     * @param form form
     * @return ResponseResult
     */
    @PostMapping
    public ResponseResult<String> add(@RequestParam("file") MultipartFile file, @RequestBody @Valid KnowledgeForm form) {
        KnowledgeBo bo = assemble(file, form);
        agentKnowledgeService.add(bo);
        return ResponseResult.success(bo.getKnowledgeId());
    }

    private KnowledgeBo assemble(MultipartFile file, KnowledgeForm form) {
        KnowledgeBo bo = new KnowledgeBo();
        try {
            String fileId = servaFile.upload(file.getInputStream());
            bo.setFileId(fileId);
            bo.setFileSize(file.getSize());
            bo.setFileType(file.getContentType());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        bo
            .setDatasourceId(form.getDatasourceId())
            .setTitle(form.getTitle())
            .setType(form.getType())
            .setQuestion(form.getQuestion())
            .setContent(form.getContent())
            .setFileType(file.getContentType())
            .setSplitterType(form.getSplitterType());
        return bo;
    }
}
