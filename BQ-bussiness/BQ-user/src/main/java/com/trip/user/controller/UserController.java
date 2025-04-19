package com.trip.user.controller;


import DTO.UserUpdateDTO;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import untils.JwtUtils;
import untils.Result;
import entity.User;
import com.trip.user.service.UserService;
import io.jsonwebtoken.MalformedJwtException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/user")
public class UserController {
    @Autowired
    private JwtUtils jwtUtils;


    @Autowired
    private UserService userService;

    @PostMapping("/add")
    public boolean addUser(@RequestBody User user) {
        return userService.save(user);
    }

    @GetMapping("/list")
    public List<User> listUsers() {
        return userService.list();
    }

    @GetMapping("/{id}")
    public User getUserById(@PathVariable Integer id) {
        return userService.getById(id);
    }

    @GetMapping("/byOpenid/{openid}")
    public User getUserByOpenid(@PathVariable String openid) {
        return userService.getOpenid(openid);
    }

    @PutMapping("/update")
    public Result updateUser(@RequestBody UserUpdateDTO dto,
                             @RequestHeader("Authorization") String authHeader) {
        try {
            String openid = jwtUtils.parseToken(authHeader);
            User existingUser = userService.getOpenid(openid);
            if (existingUser == null) {
                log.warn("用户不存在，openid: {}", openid);
                return Result.error(404, "用户不存在");
            }

            if (dto.getUserGender() != null) {
                dto.setUserGender("1".equals(dto.getUserGender()) ? "男" : "女");
            }
            log.info("更新用户数据: openid={} | DTO={}", openid, dto);

            UpdateWrapper<User> updateWrapper = new UpdateWrapper<>();
            updateWrapper.eq("openid", openid)
                    .set(dto.getAvatar() != null, "user_avatar", dto.getAvatar())
                    .set(dto.getUserNickname() != null, "user_nickname", dto.getUserNickname())
                    .set(dto.getPhone() != null, "user_number", dto.getPhone())
                    .set(dto.getUserGender() != null, "user_gender", dto.getUserGender());

            boolean success = userService.updateUser(updateWrapper);
            if (!success) {
                log.warn("更新失败，openid: {}", openid);
                return Result.error(500, "更新用户信息失败");
            }
            return Result.ok().msg("用户信息更新成功");
        } catch (MalformedJwtException e) {
            log.error("Token 格式错误: {}", authHeader, e);
            return Result.error(401, "无效的登录凭证");
        } catch (Exception e) {
            log.error("用户更新失败", e);
            return Result.error(500, "数据更新失败: " + e.getMessage());
        }
    }

    @DeleteMapping("/delete/{id}")
    public boolean deleteUser(@PathVariable Integer id) {
        return userService.removeById(id);
    }


    @PostMapping("/upload")
    public Result uploadAvatar(
            @RequestParam MultipartFile file,
            @RequestHeader("Authorization") String authHeader) {
        try {
            log.info("==== 收到上传请求 ====");
            if (file.isEmpty()) throw new IllegalArgumentException("文件不能为空");

            log.info("文件信息: 名称={} 大小={}KB",
                    file.getOriginalFilename(),
                    file.getSize()/1024);

            String openid = jwtUtils.parseToken(authHeader);
            String avatarUrl = userService.uploadAvatar(openid, file);
            return Result.ok().data("avatar", avatarUrl);

        } catch (MalformedJwtException e) {
            log.error("Token格式错误", e);
            return Result.error(401, "登录凭证无效");
        } catch (Exception e) {
            log.error("上传异常", e); // 完整堆栈日志
            return Result.error(500, "上传失败: " + e.getMessage());
        }
    }


    @GetMapping("/profile")
    public Result getProfile(@RequestHeader("Authorization") String authHeader) {


        try {
            String openid = jwtUtils.parseToken(authHeader);
            User user = userService.getOpenid(openid);
            return Result.ok().data("user", user);
        } catch (MalformedJwtException e) {
            return Result.error(401, "登录凭证无效");
        }

    }

    @GetMapping("/Mysesion")
    public int getMySesion(@RequestHeader("Authorization" ) String authHeader){
        try {
            String openid = jwtUtils.parseToken(authHeader);
            User user = userService.getOpenid(openid);
            return 1;
        }catch (Exception exception){

        }
        return 0;
    }
}
