package service;

import DTO.QuestiomsDTO;
import entity.Questions;

import java.util.List;

public interface QuestionService {
    List<QuestiomsDTO> getQuestionsByUserId(Integer userId);

    boolean saveQuestions(List<Questions> questions);
    List<QuestiomsDTO> getQuestionsByFileId(Integer fileId, Integer userId); // 修改为 DTO


}
