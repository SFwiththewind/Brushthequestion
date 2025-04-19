package DTO;

import lombok.Data;

import java.util.List;

@Data
public class ProgressRequest {
    private Integer fileId;
    private List<String> userAnswers; // 兼容多选（数组）和单选/判断/问答（字符串）
    private Integer currentIndex;

    public ProgressRequest() {}
}
