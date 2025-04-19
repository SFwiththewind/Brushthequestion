package DTO;

import entity.Questions;
import lombok.Data;
import org.springframework.beans.BeanUtils;

@Data
public class QuestiomsDTO {
    private String questionId;
    private String questionType;
    private String questionText;
    private String options;
    private String knowledgePoint;
    private String answer;
    private String difficulty;

    public QuestiomsDTO(Questions record) {
        BeanUtils.copyProperties(record, this);
        this.questionId = String.valueOf(record.getQuestionId());
        this.options = record.getOptions();
    }

    public QuestiomsDTO() {}

}
