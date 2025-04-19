package controller;

import DTO.ProgressRequest;
import DTO.QuestiomsDTO;
import entity.Questions;
import entity.User;
import io.jsonwebtoken.MalformedJwtException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestTemplate;
import service.QuestionService;
import untils.JwtUtils;
import untils.Result;
import DTO.FileIdRequest;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/question")
public class QuestionController {

    @Autowired
    private QuestionService questionService;

    @Autowired
    private JwtUtils jwtUtils;

    @Autowired
    private RestTemplate restTemplate;

    @Autowired
    private DiscoveryClient discoveryClient;

    private User getUserByOpenid(String openid) {
        String url = "http://47.115.35.162:18103/BQ-user/user/byOpenid/" + openid;
        try {
            ResponseEntity<User> response = restTemplate.getForEntity(url, User.class);
            log.info("BQ-user 响应，url: {}, status: {}, body: {}", url, response.getStatusCode(), response.getBody());
            if (response.getStatusCode().is2xxSuccessful()) {
                User user = response.getBody();
                if (user == null) {
                    log.warn("BQ-user 返回空用户，openid: {}", openid);
                }
                return user;
            } else {
                log.error("BQ-user 返回非成功状态码，url: {}, status: {}", url, response.getStatusCode());
                return null;
            }
        } catch (HttpServerErrorException e) {
            log.error("BQ-user 返回服务器错误，url: {}, status: {}, response: {}", url, e.getStatusCode(), e.getResponseBodyAsString());
            return null;
        } catch (Exception e) {
            log.error("调用 BQ-user 失败，url: {}, openid: {}, 错误: {}", url, openid, e.getMessage(), e);
            return null;
        }
    }

    @GetMapping("/list")
    public List<QuestiomsDTO> getUserQuestions(@RequestHeader("Authorization") String authHeader) {
        try {
            String openid = jwtUtils.parseToken(authHeader);
            User user = getUserByOpenid(openid);
            if (user == null) {
                log.warn("用户不存在，openid: {}", openid);
                return null;
            }
            Integer userId = user.getId();

            log.info("查询用户题目，userId: {}", userId);
            return questionService.getQuestionsByUserId(userId);
        } catch (MalformedJwtException e) {
            log.error("Token 格式错误: {}", authHeader, e);
            return null;
        } catch (Exception e) {
            log.error("查询题目失败: {}", e.getMessage());
            return null;
        }
    }

    @PostMapping("/save")
    public boolean saveQuestions(@RequestBody List<Questions> questions) {
        try {
            log.info("保存题目，题目数量: {}", questions.size());
            return questionService.saveQuestions(questions);
        } catch (Exception e) {
            log.error("保存题目失败: {}", e.getMessage());
            return false;
        }
    }

    @PostMapping("/getByFileId")
    public Result getQuestionsByFileId(@RequestHeader("Authorization") String authHeader,
                                       @RequestBody FileIdRequest fileIdRequest) {
        try {
            String openid = jwtUtils.parseToken(authHeader);
            User user = getUserByOpenid(openid);
            if (user == null) {
                log.warn("用户不存在，openid: {}", openid);
                return Result.error(404, "用户不存在");
            }
            Integer userId = user.getId();
            Integer fileId = fileIdRequest.getFileId();

            log.info("查询用户ID: {} 的文件ID: {} 的题目", userId, fileId);
            List<QuestiomsDTO> questions = questionService.getQuestionsByFileId(fileId, userId);
            if (questions.isEmpty()) {
                return Result.ok().data("questions", questions).data("message", "该试卷暂无题目");
            }
            return Result.ok().data("questions", questions);
        } catch (MalformedJwtException e) {
            log.error("Token 格式错误: {}", authHeader, e);
            return Result.error(401, "无效的登录凭证");
        } catch (Exception e) {
            log.error("查询题目失败: {}", e.getMessage());
            return Result.error(500, "查询题目失败: " + e.getMessage());
        }
    }

    @PostMapping("/saveProgress")
    public Result saveProgress(@RequestHeader("Authorization") String authHeader,
                               @RequestBody ProgressRequest progressRequest) {
        try {
            String openid = jwtUtils.parseToken(authHeader);
            User user = getUserByOpenid(openid);
            if (user == null) {
                log.warn("用户不存在，openid: {}", openid);
                return Result.error(404, "用户不存在");
            }
            Integer userId = user.getId();

            log.info("保存用户ID: {} 的进度", userId);
            return Result.ok().msg("进度保存成功");
        } catch (MalformedJwtException e) {
            log.error("Token 格式错误: {}", authHeader, e);
            return Result.error(401, "无效的登录凭证");
        } catch (Exception e) {
            log.error("保存进度失败: {}", e.getMessage());
            return Result.error(500, "保存进度失败: " + e.getMessage());
        }
    }
}