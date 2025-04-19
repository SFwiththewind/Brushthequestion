package controller;

import entity.Filerecords;
import entity.Questions;
import entity.User;
import io.jsonwebtoken.MalformedJwtException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;
import service.FileService;
import untils.JwtUtils;
import untils.Result;
import DTO.FileRecordDto;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/file")
public class FileController {

    @Autowired
    private FileService fileService;

    @Autowired
    private JwtUtils jwtUtils;

    @Autowired
    private RestTemplate restTemplate;

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

    @PostMapping("/aiAnalyzeFile")
    public Result aiAnalyzeFile(@RequestParam("file") MultipartFile file,
                                @RequestHeader("Authorization") String authHeader) {
        if (file == null || file.isEmpty()) {
            log.warn("上传的文件为空");
            return Result.error(400, "文件为空");
        }

        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null) {
            log.warn("文件名无效");
            return Result.error(400, "文件名无效");
        }

        try {
            String openid = jwtUtils.parseToken(authHeader);
            User user = getUserByOpenid(openid);
            if (user == null) {
                log.warn("用户不存在，openid: {}", openid);
                return Result.error(404, "用户不存在");
            }
            Integer userId = user.getId();

            log.info("Token 验证通过，接收到文件: {}，用户ID: {}", originalFilename, userId);

            int batchSize = 50;
            List<Questions> questions = fileService.analyzeFile(file, userId, batchSize, authHeader);

            if (questions.isEmpty()) {
                return Result.ok().data("message", "文件分析成功，但未解析到题目");
            }

            fileService.saveQuestionsToQuestionModule(questions, authHeader); // 传递 authHeader

            return Result.ok().data("questions", questions);

        } catch (MalformedJwtException e) {
            log.error("Token 格式错误: {}", authHeader, e);
            return Result.error(401, "无效的登录凭证");
        } catch (Exception e) {
            log.error("文件分析失败: {}，错误: {}", originalFilename, e.getMessage());
            return Result.error(500, "文件分析失败: " + e.getMessage());
        }
    }

    @PostMapping("/upload")
    public Result uploadFile(@RequestParam("file") MultipartFile file,
                             @RequestParam(value = "filename", required = false) String filename,
                             @RequestParam(value = "type", required = false) String type,
                             @RequestHeader("Authorization") String authHeader) {
        if (file == null || file.isEmpty()) {
            log.warn("上传的文件为空");
            return Result.error(400, "文件为空");
        }

        String originalFilename = filename != null ? filename : file.getOriginalFilename();
        if (originalFilename == null) {
            log.warn("文件名无效");
            return Result.error(400, "文件名无效");
        }

        try {
            String openid = jwtUtils.parseToken(authHeader);
            User user = getUserByOpenid(openid);
            if (user == null) {
                log.warn("用户不存在，openid: {}", openid);
                return Result.error(404, "用户不存在");
            }
            Integer userId = user.getId();

            log.info("接收到文件: {}, 类型: {}, 用户ID: {}", originalFilename, type, userId);

            List<Questions> questions = fileService.processAndSaveFile(file, originalFilename, type, userId, authHeader);
            if (questions.isEmpty()) {
                return Result.ok().data("message", "文件上传成功，但未解析到题目");
            }
            fileService.saveQuestionsToQuestionModule(questions, authHeader); // 传递 authHeader
            return Result.ok().data("questions", questions);
        } catch (MalformedJwtException e) {
            log.error("Token 格式错误: {}", authHeader, e);
            return Result.error(401, "无效的登录凭证");
        } catch (Exception e) {
            log.error("文件处理失败: {}，错误: {}", originalFilename, e.getMessage());
            return Result.error(500, "文件处理失败: " + e.getMessage());
        }
    }

    @PostMapping("/GetExam")
    public Result getUserExams(@RequestHeader("Authorization") String authHeader) {
        try {
            String openid = jwtUtils.parseToken(authHeader);
            User user = getUserByOpenid(openid);
            if (user == null) {
                log.warn("用户不存在，openid: {}", openid);
                return Result.error(404, "用户不存在");
            }
            Integer userId = user.getId();

            List<Filerecords> fileRecords = fileService.getUserFileRecords(userId);
            if (fileRecords.isEmpty()) {
                log.info("用户ID: {} 无试卷记录", userId);
                return Result.ok().data("exams", fileRecords).data("message", "暂无试卷记录");
            }

            List<FileRecordDto> fileRecordDTOs = fileRecords.stream()
                    .map(FileRecordDto::new)
                    .collect(Collectors.toList());

            log.info("成功获取用户ID: {} 的试卷记录，数量: {}", userId, fileRecordDTOs.size());
            return Result.ok().data("exams", fileRecordDTOs);
        } catch (MalformedJwtException e) {
            log.error("Token 格式错误: {}", authHeader, e);
            return Result.error(401, "无效的登录凭证");
        } catch (Exception e) {
            log.error("获取试卷记录失败: {}", e.getMessage());
            return Result.error(500, "获取试卷记录失败: " + e.getMessage());
        }
    }
}