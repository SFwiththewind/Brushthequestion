package entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("filerecords")
public class Filerecords {
    @TableField("file_id")
    private Integer fileId;
    @TableField("file_name")
    private String fileName;
    @TableField("file_path")
    private String filePath;
    @TableField("file_type")
    private String fileType;
    @TableField("upload_time")
    private LocalDateTime uploadTime;
    @TableField("question_id")
    private Integer questionId;
    @TableField("user_id")
    private Integer userId; // 新增字段
}
