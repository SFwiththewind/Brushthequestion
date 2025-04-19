package com.trip.user.service;

import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.extension.service.IService;
import entity.User;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

public interface UserService extends IService<User> {
    String uploadAvatar(String openId, MultipartFile file);
    Boolean updateUser(UpdateWrapper<User> wrapper);
    User getOpenid(String openid);
}
