package entity;

import com.baomidou.mybatisplus.annotation.TableField;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class Questions {
    @TableField("question_id")
    private Integer questionId;
    @TableField("question_type")
    private String questionType;
    @TableField("question_text")
    private String questionText;
    @TableField("options")
    private String options;
    @TableField("answer")
    private String answer;
    @TableField("difficulty")
    private Double difficulty;
    @TableField("knowledge_point")
    private String knowledgePoint;
    @TableField("file_id")
    private Integer fileId;
    @TableField("user_id")
    private Integer userId;
    @TableField("create_time")
    private LocalDateTime createTime;
    @TableField("update_time")
    private LocalDateTime updateTime;
}
