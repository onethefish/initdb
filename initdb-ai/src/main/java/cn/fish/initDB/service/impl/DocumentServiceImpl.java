package cn.fish.initDB.service.impl;

import cn.fish.initDB.entity.ChatSession;
import cn.fish.initDB.repository.ChatSessionRepository;
import cn.fish.initDB.repository.VectorStoreRepository;
import cn.fish.initDB.service.DocumentService;
import cn.fish.web.exception.CommonException;
import cn.hutool.core.util.ObjUtil;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.TextReader;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;


@Service
public class DocumentServiceImpl implements DocumentService {

    @Autowired
    private VectorStoreRepository vectorStoreRepository;
    @Autowired
    private ChatSessionRepository chatSessionRepository;

    @Override
    public void importTxtDocument(MultipartFile file, String sessionId) {
        ChatSession chatSession = chatSessionRepository.queryUnique(sessionId);
        if (ObjUtil.isEmpty(chatSession)) {
            throw new CommonException("Sorry, an error occurred: chatSession is null");
        }
        TextReader textReader = new TextReader(file.getResource());
        List<Document> read = textReader.read();
        for (Document document : read) {
            document.getMetadata().put("sessionId", sessionId);
        }
        vectorStoreRepository.add(read);
    }


}
