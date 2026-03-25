package cn.fish.initDB;

import org.springframework.ai.document.Document;
import org.springframework.ai.reader.TextReader;
import org.springframework.core.io.FileSystemResource;

import java.io.File;
import java.util.List;

public class Test {


    public static void main(String[] args) {

        File file = new File("C:\\Users\\57172\\Documents\\临时.txt");
        FileSystemResource fileSystemResource = new FileSystemResource(file);
        TextReader textReader = new TextReader(fileSystemResource);
        List<Document> documents = textReader.get();
        documents.forEach(System.out::println);
    }

}
