package com.trip.user.service;

import DTO.WechatLoginDTO;

import java.util.Map;

public interface WeChatAuthService {
    /**
     * 处理微信登录逻辑
     *
     * @param code 前端 wx.login() 返回的 code
     * @return 包含 openid 和 token 的 Map
     * @throws Exception 如果调用微信 API 失败
     */
    Map<String, Object> weChatLogin(
            String code,
            String encryptedData,
            String iv,
            WechatLoginDTO.UserInfo userInfo // 保持参数类型一致
    ) throws Exception;
}