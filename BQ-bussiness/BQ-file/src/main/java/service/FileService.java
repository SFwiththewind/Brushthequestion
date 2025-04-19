package service;

import entity.Filerecords;
import entity.Questions;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface FileService {
    List<Questions> processAndSaveFile(MultipartFile file, String originalFilename, String type, Integer userId, String authHeader);
    List<Filerecords> getUserFileRecords(Integer userId);
    List<Questions> analyzeFile(MultipartFile file, Integer userId, int batchSize, String authHeader);
    String getQuestionServiceUrl();
    void saveQuestionsToQuestionModule(List<Questions> questions, String authHeader);
}