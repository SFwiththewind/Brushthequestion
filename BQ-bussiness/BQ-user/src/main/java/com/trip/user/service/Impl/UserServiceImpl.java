package com.trip.user.service.Impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.trip.user.mapper.UserMapper;
import entity.User;
import com.trip.user.service.UserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.AccessDeniedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements UserService {

    @Value("${server.host}")
    private String serverHost;

    @Value("${file.storage.local-path}")
    private String uploadPath;
    @Value("${file.storage.access-path}")
    private String accessPath;

    @Override
    public String uploadAvatar(String openid, MultipartFile file) {
        log.info(">>>> 开始处理上传请求 <<<<");
        log.info("配置参数 -> 存储路径: {} | 访问路径: {}", uploadPath, accessPath);
        final String METHOD_TAG = "[头像上传]";
        Path targetPath = null;

        try {
            log.info("{} 开始处理，用户OpenID：{}", METHOD_TAG, openid);

            // 1. 验证文件有效性
            if (file == null || file.isEmpty()) {
                throw new IllegalArgumentException("上传文件不能为空");
            }
            String originalFilename = file.getOriginalFilename();
            if (originalFilename == null || originalFilename.trim().isEmpty()) {
                throw new IllegalArgumentException("无效的文件名");
            }

            // 2. 路径预处理
            String sanitizedPath = uploadPath
                    .replace("\\", "/")          // 统一斜杠方向
                    .replace("//", "/");         // 处理多余斜杠
            Path storagePath = Paths.get(sanitizedPath)
                    .normalize()                 // 标准化路径
                    .toAbsolutePath();

            // 3. 目录创建（带异常重试）
            if (!Files.exists(storagePath)) {
                log.info("{} 创建存储目录：{}", METHOD_TAG, storagePath);
                try {
                    Files.createDirectories(storagePath);
                } catch (AccessDeniedException e) {
                    String errorMsg = String.format("目录创建权限不足：%s", storagePath);
                    log.error(errorMsg);
                    throw new RuntimeException(errorMsg, e);
                }
            }

            // 4. 验证写入权限
            if (!Files.isWritable(storagePath)) {
                String errorMsg = String.format("目录不可写：%s", storagePath);
                log.error(errorMsg);
                throw new RuntimeException(errorMsg);
            }

            // 5. 生成安全文件名
            String fileExt = originalFilename.substring(originalFilename.lastIndexOf("."));
            String newFilename = UUID.randomUUID() + fileExt;
            targetPath = storagePath.resolve(newFilename);

            // 6. 保存文件（带IO监控）
            log.info("{} 保存文件至：{}", METHOD_TAG, targetPath);
            long startTime = System.currentTimeMillis();
            file.transferTo(targetPath);
            log.info("{} 文件保存成功，耗时：{}ms", METHOD_TAG, System.currentTimeMillis() - startTime);

            // 7. 构建访问URL
            String accessUrl = serverHost+accessPath.replace("/**", "")
                    + "/" + newFilename;
            log.info("{} 生成访问地址：{}", METHOD_TAG, accessUrl);

            UpdateWrapper<User> updateWrapper = new UpdateWrapper<>();
            updateWrapper.eq("openid", openid)
                    .set("user_avatar", accessUrl);
            baseMapper.update(null, updateWrapper);

            log.info("用户头像已更新: openid={} avatar={}", openid, accessUrl);

            return accessUrl;

        } catch (IOException e) {
            String errorMsg = "文件传输失败";
            if (targetPath != null) {
                errorMsg += "，目标路径：" + targetPath;
            }
            log.error("{} {}", METHOD_TAG, errorMsg, e);
            throw new RuntimeException(errorMsg, e);

        } catch (Exception e) {
            log.error("{} 上传流程异常：{}", METHOD_TAG, e.getMessage(), e);
            throw new RuntimeException("文件上传流程异常", e);
        }


    }

    @Override
    public Boolean updateUser(UpdateWrapper<User> wrapper) {
        wrapper.set("user_update", LocalDateTime.now());
        int affectedRows = baseMapper.update(null, wrapper);
        log.info("[用户信息更新] 条件: {} | 影响行数: {}",
                wrapper.getTargetSql(),
                affectedRows);

        return affectedRows > 0;
    }


    @Override
    public User getOpenid(String openid) {
        return baseMapper.selectOne(new QueryWrapper<User>().eq("openid", openid));
    }
}
