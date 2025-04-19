package DTO;

import entity.Filerecords;
import lombok.Data;
import org.springframework.beans.BeanUtils;

import java.time.LocalDateTime;
@Data

public class FileRecordDto {
    private Integer fileId;
    private String fileName;
    private String fileType;
    private LocalDateTime uploadTime;
    private Integer userId;

    public FileRecordDto(Filerecords record) {
        BeanUtils.copyProperties(record, this);
    }
}
