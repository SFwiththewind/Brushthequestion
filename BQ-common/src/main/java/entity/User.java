package entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data  // Lombok 自动生成 getter/setter
@TableName("user")  // 绑定数据库表
public class User {

  @TableId(value = "id", type = IdType.AUTO)  // 主键自增
  private Integer id;

  @TableField("openid")  // 关联数据库字段
  private String openid;

  @TableField("unionid")
  private String unionid;

  @TableField("user_number")
  private String userNumber;

  @TableField("user_name")
  private String userName;

  @TableField("user_nickname")
  private String userNickname;

  @TableField("user_gender")
  private String userGender;

  @TableField("user_avatar")
  private String userAvatar;

  @TableField("user_session")
  private int userSession;



  @TableField(value = "user_update", fill = FieldFill.INSERT_UPDATE)
  private LocalDateTime userUpdate;

  @TableField(value = "user_create", fill = FieldFill.INSERT)
  private LocalDateTime userCreate;

  @TableField("user_aver")
  private String userAver;

  @TableField("user_start")
  private Integer userStart;

  @TableField("user_token")
  private String userToken;

  @TableField("session_key")
  private String sessionKey;

  @TableField("sessionKey_ExpireTime")
  private LocalDateTime sessionkeyexpiretime;
}
