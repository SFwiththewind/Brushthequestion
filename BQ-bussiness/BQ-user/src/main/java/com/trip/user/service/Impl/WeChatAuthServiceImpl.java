package com.trip.user.service.Impl;

import DTO.WechatLoginDTO;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import entity.User;
import com.trip.user.mapper.UserMapper;
import com.trip.user.service.WeChatAuthService;
import untils.JwtUtils;
import org.apache.commons.codec.binary.Base64;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import javax.annotation.Resource;
import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Service
public class WeChatAuthServiceImpl implements WeChatAuthService {

    @Value("${wechat.appid}")
    private String appId;
    @Autowired
    private JwtUtils jwtUtils;
    @Value("${wechat.secret}")
    private String appSecret;

    @Resource
    private UserMapper userMapper;

    private final RestTemplate restTemplate = new RestTemplate();

    @Override
    public Map<String, Object> weChatLogin(String code, String encryptedData, String iv,
                                           WechatLoginDTO.UserInfo frontendUserInfo) throws Exception {

        Map<String, String> sessionData = getSessionKey(code);
        String openid = sessionData.get("openid");
        String sessionKey = sessionData.get("session_key");


        String decryptedData = decryptUserInfo(encryptedData, sessionKey, iv);
        ObjectMapper mapper = new ObjectMapper();
        JsonNode wechatData = mapper.readTree(decryptedData);


        if (!wechatData.get("nickName").asText().equals(frontendUserInfo.getNickname()) ||
                !wechatData.get("avatarUrl").asText().equals(frontendUserInfo.getAvatarUrl()) ||
                wechatData.get("gender").asInt() != frontendUserInfo.getGender()) {
            throw new SecurityException("微信数据校验失败");
        }


        User user = processUser(openid, sessionKey, frontendUserInfo);


        return buildResult(user);
    }

    private User processUser(String openid, String sessionKey,
                             WechatLoginDTO.UserInfo userInfo) {
        User user = userMapper.selectByOpenId(openid);

        LocalDateTime now = LocalDateTime.now();
        if (user == null) {
            user = new User();
            user.setOpenid(openid);
            user.setUserName(userInfo.getNickname());
            user.setUserAvatar(userInfo.getAvatarUrl());
            user.setUserGender(userInfo.getGender().toString());
            user.setUserCreate(now);
            user.setUserUpdate(now);
            user.setSessionKey(sessionKey);
            user.setSessionkeyexpiretime(now.plusDays(2));
            userMapper.insert(user);
        } else {
            user.setUserGender(userInfo.getGender().toString());
            user.setUserUpdate(now);
            user.setSessionKey(sessionKey);
            user.setSessionkeyexpiretime(now.plusDays(2));
            userMapper.updateById(user);
        }
        return user;
    }

    private Map<String, Object> buildResult(User user) {
        Map<String, Object> result = new HashMap<>();
        result.put("token", jwtUtils.generateToken(user.getOpenid()));
        Map<String, String> userInfo = new HashMap<>();
        userInfo.put("nickname", getDisplayName(user));
        userInfo.put("avatar", getUserAvatar(user));
        userInfo.put("user_number", user.getUserNumber());
        userInfo.put("user_gender", user.getUserGender());
        result.put("user", userInfo);
        return result;
    }

    private String getDisplayName(User user) {
        return (user.getUserNickname() != null ) ? user.getUserNickname() : user.getUserName();
    }
    private String getUserAvatar(User user){
        return (user.getUserAver() !=null) ? user.getUserAver() : user.getUserAvatar();
    }
    @NotNull
    private Map<String, Object> getStringObjectMap(String openid, String nickname, String avatarUrl, String userNumber, String userGender, Map<String, Object> result) {
        Map<String, String> userInfo = new HashMap<>();
        userInfo.put("openid", openid);
        userInfo.put("nickname", nickname);
        userInfo.put("avatar", avatarUrl);
        userInfo.put("user_number", userNumber);
        userInfo.put("user_gender", userGender);

        result.put("user",userInfo);
        return result;
    }

    /**
     * 🔓 解密微信加密数据
     */
    private String decryptUserInfo(String encryptedData, String sessionKey, String iv) throws Exception {
        byte[] dataBytes = Base64.decodeBase64(encryptedData);
        byte[] keyBytes = Base64.decodeBase64(sessionKey);
        byte[] ivBytes = Base64.decodeBase64(iv);

        SecretKeySpec secretKeySpec = new SecretKeySpec(keyBytes, "AES");
        IvParameterSpec ivParameterSpec = new IvParameterSpec(ivBytes);

        Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
        cipher.init(Cipher.DECRYPT_MODE, secretKeySpec, ivParameterSpec);

        byte[] decryptedBytes = cipher.doFinal(dataBytes);
        return new String(decryptedBytes, StandardCharsets.UTF_8);
    }

    /**
     * 获取 openid 和 session_key
     */
    private Map<String, String> getSessionKey(String code) throws Exception {
        String url = "https://api.weixin.qq.com/sns/jscode2session?appid=" + appId +
                "&secret=" + appSecret +
                "&js_code=" + code +
                "&grant_type=authorization_code";

        String response = restTemplate.getForObject(url, String.class);
        ObjectMapper objectMapper = new ObjectMapper();
        JsonNode jsonNode = objectMapper.readTree(response);

        if (jsonNode.has("errcode")) {
            throw new RuntimeException("微信登录失败: " + jsonNode.get("errmsg").asText());
        }

        String openid = jsonNode.get("openid").asText();
        String sessionKey = jsonNode.get("session_key").asText();



        Map<String, String> sessionData = new HashMap<>();
        sessionData.put("openid", openid);
        sessionData.put("session_key", sessionKey);
        return sessionData;
    }
}
