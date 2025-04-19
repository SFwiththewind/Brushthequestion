package entity;

import lombok.Data;

import java.util.List;

@Data
public class Progress {
    private Integer userId;
    private Integer fileId;
    private List<String> userAnswers;
    private Integer currentIndex;

}
