package cn.fish.knowledge.service.impl;

import cn.fish.chart.entity.ChatSession;
import cn.fish.chart.repository.ChatSessionRepository;
import cn.fish.cloud.serva.file.operate.ServaFile;
import cn.fish.cloud.serva.web.exception.CommonException;
import cn.fish.knowledge.repository.VectorStoreRepository;
import cn.fish.knowledge.service.DocumentService;
import cn.fish.knowledge.splitter.ParagraphTextSplitter;
import cn.hutool.core.util.ObjUtil;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.tika.TikaDocumentReader;
import org.springframework.ai.transformer.splitter.TextSplitter;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;


@Service
public class DocumentServiceImpl implements DocumentService {

    private final VectorStoreRepository vectorStoreRepository;
    private final ChatSessionRepository chatSessionRepository;
    private final ServaFile servaFile;

    public DocumentServiceImpl(VectorStoreRepository vectorStoreRepository, ChatSessionRepository chatSessionRepository, ServaFile servaFile) {
        this.vectorStoreRepository = vectorStoreRepository;
        this.chatSessionRepository = chatSessionRepository;
        this.servaFile = servaFile;
    }

    @Override
    public void importTxtDocument(String sessionId, MultipartFile file) {
        ChatSession chatSession = chatSessionRepository.queryUnique(sessionId);
        if (ObjUtil.isEmpty(chatSession)) {
            throw new CommonException("Sorry, an error occurred: chatSession is null");
        }
        // todo
        try {
            String fileId = servaFile.upload(file.getInputStream());

        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        List<Document> splitDocuments = getAndSplitDocument(file);
        addMetadata(sessionId, splitDocuments);
        vectorStoreRepository.add(splitDocuments);
    }

    private static void addMetadata(String sessionId, List<Document> splitDocuments) {
        for (Document document : splitDocuments) {
            document.getMetadata().put("sessionId", sessionId);
        }
    }

    private static List<Document> getAndSplitDocument(MultipartFile file) {
        TikaDocumentReader textReader = new TikaDocumentReader(file.getResource());
        List<Document> read = textReader.read();

        TextSplitter splitter = new ParagraphTextSplitter();
        return splitter.apply(read);
    }

    @Override
    public List<Document> queryList(String sessionId, String query) {
        FilterExpressionBuilder filterExpressionBuilder = new FilterExpressionBuilder();
        Filter.Expression expression = filterExpressionBuilder.eq("sessionId", sessionId)
                                                              .build();
        SearchRequest searchRequest = SearchRequest.builder()
                                                   .query(query)
                                                   .topK(1)
                                                   .filterExpression(expression)
                                                   .build();
        return vectorStoreRepository.queryList(searchRequest);
    }


}
