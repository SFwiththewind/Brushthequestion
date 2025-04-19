package com.trip.user.controller;

import DTO.WechatLoginDTO;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.trip.user.service.WeChatAuthService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/wechat")
public class WeChatAuthController {

    @Resource
    private WeChatAuthService weChatAuthService;

    @PostMapping("/login")
    public Map<String, Object> login(@RequestBody WechatLoginDTO loginDTO) throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        WechatLoginDTO.UserInfo userInfo = objectMapper.convertValue(loginDTO.getUserInfo(), WechatLoginDTO.UserInfo.class);

        return weChatAuthService.weChatLogin(
                loginDTO.getCode(),
                loginDTO.getEncryptedData(),
                loginDTO.getIv(),
                userInfo
        );

    }

}
