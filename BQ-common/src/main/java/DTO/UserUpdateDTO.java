package DTO;

import com.baomidou.mybatisplus.annotation.TableField;
import lombok.Data;

@Data
public class UserUpdateDTO {
    private String avatar;
    private String userNickname;
    @TableField("user_number")
    private String phone;
    private String userGender;
}
