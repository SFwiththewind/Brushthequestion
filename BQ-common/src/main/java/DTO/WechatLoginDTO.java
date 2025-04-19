package DTO;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.Map;

@Data
public class WechatLoginDTO {
    private String code;
    private String iv;
    private String encryptedData;
    private Map<String, Object> userInfo;

    @Data
    public static class UserInfo {
        @JsonProperty("nickName")
        private String nickname;
        @JsonProperty("avatarUrl")
        private String avatarUrl;
        private Integer gender;
    }
}
