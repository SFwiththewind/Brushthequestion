package service.Impl;

import DTO.QuestiomsDTO;
import mapper.QuestionMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import entity.Questions;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import service.QuestionService;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
public class QuestionServiceImpl implements QuestionService {

    @Autowired
    private QuestionMapper questionMapper;

    @Autowired
    private ObjectMapper objectMapper;

    @Override
    public List<QuestiomsDTO> getQuestionsByUserId(Integer userId) {
        try {
            List<Questions> questions = questionMapper.selectByUserId(userId);
            log.info("查询到用户 {} 的题目数量: {}", userId, questions.size());
            // 转换为 DTO
            List<QuestiomsDTO> dtos = questions != null
                    ? questions.stream().map(QuestiomsDTO::new).collect(Collectors.toList())
                    : new ArrayList<>();
            return dtos;
        } catch (Exception e) {
            log.error("查询用户 {} 的题目失败: {}", userId, e.getMessage());
            throw e;
        }
    }


    @Override
    public boolean saveQuestions(List<Questions> questions) {
        if (questions == null || questions.isEmpty()) {
            log.warn("题目列表为空，无法保存");
            return false;
        }

        try {
            for (Questions question : questions) {
                validateAndFixOptions(question); // 验证并修复 options
                questionMapper.insert(question);
            }
            log.info("成功保存 {} 条题目", questions.size());
            return true;
        } catch (Exception e) {
            log.error("保存题目失败: {}", e.getMessage(), e);
            return false;
        }
    }

    @Override
    public List<QuestiomsDTO> getQuestionsByFileId(Integer fileId, Integer userId) {
        try {
            List<Questions> questions = questionMapper.selectByFileIdAndUserId(fileId, userId);
            log.info("查询到用户ID: {} 文件ID: {} 的题目数量: {}", userId, fileId, questions.size());
            List<QuestiomsDTO> dtos = questions != null
                    ? questions.stream().map(QuestiomsDTO::new).collect(Collectors.toList())
                    : new ArrayList<>();
            return dtos;
        } catch (Exception e) {
            log.error("查询用户ID: {} 文件ID: {} 的题目失败: {}", userId, fileId, e.getMessage());
            return new ArrayList<>();
        }
    }

    private void validateAndFixOptions(Questions question) throws JsonProcessingException {
        String options = question.getOptions();
        if (options == null || options.trim().isEmpty()) {
            log.warn("题目选项为空: {}", question.getQuestionText());
            return;
        }

        try {
            JsonNode jsonNode = objectMapper.readTree(options);
            checkDuplicateKeys(jsonNode, question);
        } catch (JsonProcessingException e) {
            log.warn("检测到无效 JSON，尝试修复: {}", options);
            String fixedOptions = fixControlCharacters(options);
            try {
                JsonNode jsonNode = objectMapper.readTree(fixedOptions);
                checkDuplicateKeys(jsonNode, question);
                question.setOptions(fixedOptions); // 更新为修复后的选项
                log.info("成功修复 JSON: {}", fixedOptions);
            } catch (JsonProcessingException ex) {
                log.error("无法修复 JSON 数据: {}, 题目: {}", options, question.getQuestionText(), ex);
                throw new JsonProcessingException("保存题目失败: 无效的 JSON 格式") {};
            }
        }
    }

    private String fixControlCharacters(String json) {
        return json.replace("\t", "\\t")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replaceAll("[\\p{Cntrl}&&[^\n\r\t]]", "");
    }

    private void checkDuplicateKeys(JsonNode jsonNode, Questions question) {
        if (jsonNode.isObject()) {
            Iterator<Map.Entry<String, JsonNode>> fields = jsonNode.fields();
            Set<String> keys = new HashSet<>();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> entry = fields.next();
                String key = entry.getKey();
                if (!keys.add(key)) {
                    log.warn("检测到重复键名 '{}' 在题目: {}", key, question.getQuestionText());
                }
            }
        }
    }
}