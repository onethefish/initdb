package cn.fish.initDB.service.impl;

import cn.fish.initDB.repository.VectorStoreRepository;
import cn.fish.initDB.service.DocumentService;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.TextReader;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;


@Service
public class DocumentServiceImpl implements DocumentService {

    @Autowired
    private VectorStoreRepository vectorStoreRepository;

    @Override
    public void importTxtDocument(MultipartFile file, String sessionId) {
        TextReader textReader = new TextReader(file.getResource());
        List<Document> read = textReader.read();
        TokenTextSplitter splitter = new TokenTextSplitter();
        List<Document> splitDocuments = splitter.apply(read);
        for (Document document : splitDocuments) {
            document.getMetadata().put("sessionId", sessionId);
        }
        vectorStoreRepository.add(splitDocuments);
    }


}
