package DTO;

import lombok.Data;

@Data
public class FileIdRequest {
    private Integer fileId;

    public FileIdRequest(Integer fileId) {
        this.fileId = fileId;
    }

    public FileIdRequest() {
    }

}
