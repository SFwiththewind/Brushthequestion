package service.Impl;

import DTO.FileIdRequest;
import mapper.FileMapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import entity.Filerecords;
import entity.Questions;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.hwpf.HWPFDocument;
import org.apache.poi.hwpf.extractor.WordExtractor;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;
import service.FileService;

import javax.annotation.PostConstruct;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import com.fasterxml.jackson.databind.ObjectMapper;
import untils.Result;

@Slf4j
@Service
public class FileServiceImpl implements FileService {

    @Autowired
    private FileMapper fileMapper;

    @Autowired
    private DiscoveryClient discoveryClient;

    @Value("${file.storage.local-path}")
    private String localPath;

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final String VOLCENGINE_API_URL = "https://ark.cn-beijing.volces.com/api/v3/chat/completions";
    private static final String VOLCENGINE_API_KEY = "8b553d46-f58c-4944-8851-c9b006efbe7e";
    private static final String QUESTION_SERVICE_URL = "http://47.115.35.162:18103";

    @PostConstruct
    public void init() {
        log.info("Loaded file.storage.local-path: {}", localPath);
    }

    @Override
    public List<Questions> analyzeFile(MultipartFile file, Integer userId, int batchSize, String authHeader) {
        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null) {
            log.warn("文件名无效");
            return new ArrayList<>();
        }

        try {
            String filePath = saveFile(file, originalFilename);
            Filerecords filerecordsEntity = new Filerecords();
            filerecordsEntity.setFileName(originalFilename);
            filerecordsEntity.setFilePath(filePath);
            String fileType = originalFilename.substring(originalFilename.lastIndexOf(".") + 1).toLowerCase();
            filerecordsEntity.setFileType(fileType);
            filerecordsEntity.setUserId(userId);
            filerecordsEntity.setUploadTime(LocalDateTime.now());
            fileMapper.insert(filerecordsEntity);

            List<Questions> questions;
            try (InputStream inputStream = file.getInputStream()) {
                questions = parseQuestions(inputStream, originalFilename, filerecordsEntity.getFileId(), userId);
            }

            if (questions.isEmpty()) {
                log.warn("未解析到任何题目: {}", originalFilename);
                return questions;
            }

            analyzeQuestionsWithAI(questions, userId, batchSize);
            saveQuestionsToQuestionModule(questions, authHeader); // 传递 authHeader

            log.info("文件处理和 AI 分析完成: {}，解析并分析出 {} 道题目", originalFilename, questions.size());
            return questions;

        } catch (Exception e) {
            log.error("文件分析失败: {}，用户ID: {}，错误: {}", originalFilename, userId, e.getMessage());
            return new ArrayList<>();
        }
    }

    private void analyzeQuestionsWithAI(List<Questions> questions, Integer userId, int batchSize) {
        for (int i = 0; i < questions.size(); i += batchSize) {
            int end = Math.min(i + batchSize, questions.size());
            List<Questions> batchQuestions = questions.subList(i, end);
            processBatchQuestionsWithAI(batchQuestions, userId);
            log.info("AI 分析批次 {}-{}，处理 {} 道题目", i, end - 1, batchQuestions.size());
        }
    }

    private void processBatchQuestionsWithAI(List<Questions> batchQuestions, Integer userId) {
        final Pattern questionTypePattern = Pattern.compile("\"question_type\"\\s*:\\s*\"([^\"]+)\""); // 提取题型
        final Pattern answerPattern = Pattern.compile("\"answer\"\\s*:\\s*\"([^\"]+)\""); // 提取答案
        final Pattern knowledgePointPattern = Pattern.compile("\"knowledge_point\"\\s*:\\s*\"([^\"]+)\""); // 提取解析
        final Pattern fallbackAnswerPattern = Pattern.compile("[\\(（][\\s\u00A0]*([A-D]+|√|×|错|对)[\\s\u00A0]*[\\)）]"); // 备用答案提取
        final Pattern fallbackTypePattern = Pattern.compile("判断题|选择题|多选题|问答题"); // 备用题型提取

        for (Questions question : batchQuestions) {
            try {
                // 将选项从 JSON 转换为纯文本格式
                String optionsText = "";
                if (question.getOptions() != null && !question.getOptions().isEmpty()) {
                    Map<String, String> optionsMap = objectMapper.readValue(question.getOptions(), Map.class);
                    optionsText = optionsMap.entrySet().stream()
                            .map(entry -> entry.getKey() + ": " + entry.getValue())
                            .collect(Collectors.joining("\n"));
                }

                // 构造 Prompt，要求 JSON 格式返回
                String prompt = String.format(
                        "请分析以下题目并生成题型、答案和解析，按照以下格式返回：\n" +
                                "{\n" +
                                "  \"question_type\": \"题型（判断题/选择题/多选题/问答题）\",\n" +
                                "  \"answer\": \"答案\",\n" +
                                "  \"knowledge_point\": \"解析内容\"\n" +
                                "}\n\n" +
                                "题目：%s\n" +
                                "选项：\n%s",
                        question.getQuestionText(),
                        optionsText.isEmpty() ? "无选项" : optionsText
                );

                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(MediaType.APPLICATION_JSON);
                headers.set("Authorization", "Bearer " + VOLCENGINE_API_KEY);

                Map<String, Object> requestBody = new HashMap<>();
                List<Map<String, String>> messages = new ArrayList<>();
                Map<String, String> userMessage = new HashMap<>();
                userMessage.put("role", "user");
                userMessage.put("content", prompt);
                messages.add(userMessage);
                requestBody.put("messages", messages);
                requestBody.put("model", "doubao-1-5-lite-32k-250115");

                log.info("发送到火山模型 API 的 prompt: {}", prompt);

                HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);
                String apiResponse = restTemplate.postForObject(VOLCENGINE_API_URL, request, String.class);
                log.info("火山模型 API 响应: {}", apiResponse);

                // 首先尝试 JSON 解析
                String questionType = null;
                String answer = null;
                String knowledgePoint = null;

                try {
                    Map<String, Object> responseMap = objectMapper.readValue(apiResponse, Map.class);
                    List<Map<String, Object>> choices = (List<Map<String, Object>>) responseMap.get("choices");
                    if (choices != null && !choices.isEmpty()) {
                        Map<String, Object> choice = choices.get(0);
                        Map<String, String> message = (Map<String, String>) choice.get("message");
                        String content = message.get("content");

                        // 直接解析 JSON 格式的 content
                        Map<String, String> result = objectMapper.readValue(content, Map.class);
                        questionType = result.get("question_type");
                        answer = result.get("answer");
                        knowledgePoint = result.get("knowledge_point");
                    }
                } catch (Exception jsonEx) {
                    log.warn("JSON 解析失败，尝试正则表达式解析: {}", jsonEx.getMessage());
                    // JSON 解析失败时，使用正则表达式提取
                    String content = apiResponse;

                    Matcher typeMatcher = questionTypePattern.matcher(content);
                    if (typeMatcher.find()) {
                        questionType = typeMatcher.group(1).trim();
                    }

                    Matcher answerMatcher = answerPattern.matcher(content);
                    if (answerMatcher.find()) {
                        answer = answerMatcher.group(1).trim();
                    }

                    Matcher knowledgeMatcher = knowledgePointPattern.matcher(content);
                    if (knowledgeMatcher.find()) {
                        knowledgePoint = knowledgeMatcher.group(1).trim();
                    }

                    // 如果正则仍未提取到完整信息，使用备用逻辑
                    if (questionType == null) {
                        Matcher fallbackTypeMatcher = fallbackTypePattern.matcher(content);
                        if (fallbackTypeMatcher.find()) {
                            questionType = fallbackTypeMatcher.group();
                        }
                    }
                    if (answer == null) {
                        Matcher fallbackAnswerMatcher = fallbackAnswerPattern.matcher(content);
                        if (fallbackAnswerMatcher.find()) {
                            answer = fallbackAnswerMatcher.group(1).trim();
                        }
                    }
                }

                // 更新 Questions 对象
                if (questionType != null && !questionType.isEmpty()) {
                    question.setQuestionType(questionType);
                } else {
                    // 根据答案推断题型
                    if (answer != null) {
                        if (answer.length() > 1) {
                            question.setQuestionType("多选题");
                        } else if (answer.equals("√") || answer.equals("×") || answer.equals("错") || answer.equals("对")) {
                            question.setQuestionType("判断题");
                        } else {
                            question.setQuestionType("选择题");
                        }
                    } else {
                        question.setQuestionType("问答题"); // 默认无选项为问答题
                    }
                }

                question.setAnswer(answer != null && !answer.isEmpty() ? answer : "未提供答案");
                question.setKnowledgePoint(knowledgePoint != null && !knowledgePoint.isEmpty() ? knowledgePoint : "无解析");

            } catch (Exception e) {
                log.error("调用火山模型 API 失败: {}，题目: {}，选项: {}",
                        e.getMessage(), question.getQuestionText(), question.getOptions());
                // 异常时使用默认值
                question.setQuestionType("判断题");
                question.setAnswer("未提供答案");
                question.setKnowledgePoint("AI解析失败: " + e.getMessage());
            }
        }
    }

    @Override
    public List<Questions> processAndSaveFile(MultipartFile file, String originalFilename, String type, Integer userId, String authHeader) {
        try {
            String filePath = saveFile(file, originalFilename);
            Filerecords filerecordsEntity = new Filerecords();
            filerecordsEntity.setFileName(originalFilename);
            filerecordsEntity.setFilePath(filePath);
            filerecordsEntity.setFileType(type);
            filerecordsEntity.setUserId(userId);
            filerecordsEntity.setUploadTime(LocalDateTime.now());
            fileMapper.insert(filerecordsEntity);

            List<Questions> questions;
            try (InputStream inputStream = file.getInputStream()) {
                questions = parseQuestions(inputStream, originalFilename, filerecordsEntity.getFileId(), userId);
            }
            if (questions.isEmpty()) {
                log.warn("未解析到任何题目: {}", originalFilename);
                return questions;
            }

            saveQuestionsToQuestionModule(questions, authHeader); // 传递 authHeader

            log.info("成功处理文件: {}, 用户ID: {}, 解析出 {} 道题目", originalFilename, userId, questions.size());
            return questions;
        } catch (Exception e) {
            log.error("文件处理失败: {}，用户ID: {}，错误: {}", originalFilename, userId, e.getMessage());
            return new ArrayList<>();
        }
    }

    @Override
    public void saveQuestionsToQuestionModule(List<Questions> questions, String authHeader) {
        try {
            String questionServiceUrl = getQuestionServiceUrl();
            if (questionServiceUrl == null) {
                log.error("Question 服务地址未配置");
                return;
            }
            if (!questions.isEmpty()) {
                Integer fileId = questions.get(0).getFileId();
                log.info("准备检查文件ID: {}", fileId);
                if (fileId == null) {
                    log.error("文件ID为空，无法检查题目是否存在");
                    return;
                }
                String checkUrl = questionServiceUrl + "/question/getByFileId";
                HttpHeaders checkHeaders = new HttpHeaders();
                checkHeaders.setContentType(MediaType.APPLICATION_JSON);
                checkHeaders.set("Authorization", authHeader); // 设置 Authorization 头
                FileIdRequest fileIdRequest = new FileIdRequest(fileId);
                HttpEntity<FileIdRequest> checkRequest = new HttpEntity<>(fileIdRequest, checkHeaders);
                log.info("发送检查请求到: {}, 请求体: {}", checkUrl, objectMapper.writeValueAsString(fileIdRequest));
                Result result = restTemplate.postForObject(checkUrl, checkRequest, Result.class);
                log.info("检查结果: {}", result);
                if (result != null && result.getCode() == 200 && !((List<?>) result.getData().get("questions")).isEmpty()) {
                    log.info("文件ID: {} 已存在题目，跳过保存", fileId);
                    return;
                }
            }

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("Authorization", authHeader); // 设置 Authorization 头
            HttpEntity<List<Questions>> request = new HttpEntity<>(questions, headers);
            String saveUrl = questionServiceUrl + "/question/save";

            int maxAttempts = 3;
            for (int attempt = 1; attempt <= maxAttempts; attempt++) {
                try {
                    log.info("尝试保存题目到: {}, 第 {} 次尝试", saveUrl, attempt);
                    Boolean success = restTemplate.postForObject(saveUrl, request, Boolean.class);
                    if (success != null && success) {
                        log.info("成功保存 {} 条题目到 questions 模块", questions.size());
                        return;
                    } else {
                        log.error("保存题目到 questions 模块失败，尝试次数: {}", attempt);
                    }
                } catch (Exception e) {
                    log.warn("调用 questions 模块失败，尝试次数: {}，错误: {}", attempt, e.getMessage());
                    if (attempt == maxAttempts) {
                        log.error("调用 questions 模块保存题目失败，已达最大尝试次数: {}", e.getMessage());
                        throw e;
                    }
                    Thread.sleep(2000);
                }
            }
        } catch (Exception e) {
            log.error("调用 questions 模块保存题目失败: {}", e.getMessage());
        }
    }

    private String saveFile(MultipartFile file, String filename) throws IOException {
        Path uploadDir = Paths.get(localPath, "uploads");
        Files.createDirectories(uploadDir);
        Path filePath = uploadDir.resolve(filename);
        Files.copy(file.getInputStream(), filePath, StandardCopyOption.REPLACE_EXISTING);
        return filePath.toString();
    }

    private List<Questions> parseQuestions(InputStream inputStream, String filename, Integer fileId, Integer userId) throws IOException {
        List<Questions> questions = new ArrayList<>();
        String fileExtension = filename.substring(filename.lastIndexOf(".") + 1).toLowerCase();

        if ("docx".equals(fileExtension) || "doc".equals(fileExtension)) {
            questions = parseWord(inputStream, filename, fileId, userId);
        } else if ("xls".equals(fileExtension) || "xlsx".equals(fileExtension)) {
            questions = parseExcel(inputStream, filename, fileId, userId);
        } else if ("pdf".equals(fileExtension)) {
            questions = parsePDF(inputStream, filename, fileId, userId);
        } else {
            log.warn("不支持的文件类型: {}", filename);
        }

        return questions;
    }

    private List<Questions> parseWord(InputStream inputStream, String filename, Integer fileId, Integer userId) throws IOException {
        List<Questions> questions = new ArrayList<>();
        String fileExtension = filename.substring(filename.lastIndexOf(".") + 1).toLowerCase();

        final Pattern questionPattern = Pattern.compile("^\\d+[、,.].*");
        final Pattern optionPattern = Pattern.compile("([A-D])\\.?\\s*(.*?)(?=([A-D]\\.|$))");
        final Pattern answerPattern = Pattern.compile("[\\(（][\\s\u00A0]*([A-D]+)[\\s\u00A0]*[\\)）]");
        final Pattern answerLinePattern = Pattern.compile("^答案[:：]\\s*([A-D]+|栈|队列|√|×|错|对)(?:、\\d+)?");
        final Pattern bracketSpacePattern = Pattern.compile("[(（][\\s\u00A0]*([A-D]+?)[\\s\u00A0]*[)）]");

        if ("docx".equals(fileExtension)) {
            XWPFDocument docx = new XWPFDocument(inputStream);
            List<XWPFParagraph> paragraphs = docx.getParagraphs();

            Questions currentQuestion = null;
            Map<String, String> optionsMap = new HashMap<>();
            StringBuilder fullText = new StringBuilder();

            for (XWPFParagraph para : paragraphs) {
                String text = para.getText().trim();
                if (text.isEmpty()) continue;

                text = text.replaceAll("\\u3000", " ");
                Matcher matcher = bracketSpacePattern.matcher(text);
                StringBuffer cleanedText = new StringBuffer();
                while (matcher.find()) {
                    String content = matcher.group(1) != null ? matcher.group(1).trim() : "";
                    matcher.appendReplacement(cleanedText, "(" + content + ")");
                }
                matcher.appendTail(cleanedText);

                String resultText = cleanedText.toString();
                fullText.append(resultText).append("\n");
            }

            String[] lines = fullText.toString().split("\\r?\\n");
            for (String line : lines) {
                line = line.trim();
                if (line.isEmpty()) continue;

                Matcher questionMatcher = questionPattern.matcher(line);
                Matcher answerLineMatcher = answerLinePattern.matcher(line);

                if (questionMatcher.matches()) {
                    if (currentQuestion != null) {
                        String optionsJson = objectMapper.writeValueAsString(optionsMap);
                        currentQuestion.setOptions(optionsJson);
                        currentQuestion.setQuestionText(currentQuestion.getQuestionText().replaceAll("[\\(（][A-D]+[\\)）]", "").trim());
                        if (currentQuestion.getAnswer() == null) currentQuestion.setAnswer("未提供答案");
                        questions.add(currentQuestion);
                    }

                    currentQuestion = new Questions();
                    currentQuestion.setFileId(fileId);
                    currentQuestion.setUserId(userId);
                    currentQuestion.setQuestionText(line);
                    currentQuestion.setCreateTime(LocalDateTime.now());
                    currentQuestion.setUpdateTime(LocalDateTime.now());
                    currentQuestion.setQuestionType("选择题");

                    Matcher answerMatcher = answerPattern.matcher(line);
                    if (answerMatcher.find()) {
                        String answer = answerMatcher.group(1).trim();
                        if (!answer.isEmpty()) {
                            currentQuestion.setAnswer(answer);
                            currentQuestion.setQuestionText(line.replaceAll("[\\(（][A-D]+[\\)）]", "").trim());
                            if (answer.length() > 1) {
                                currentQuestion.setQuestionType("多选题");
                            } else if (answer.equals("√") || answer.equals("×")) {
                                currentQuestion.setQuestionType("判断题");
                            }
                        }
                    }

                    optionsMap = new HashMap<>();
                } else if (answerLineMatcher.matches() && currentQuestion != null) {
                    String answer = answerLineMatcher.group(1).trim();
                    currentQuestion.setAnswer(answer);
                    if (answer.length() > 1) {
                        currentQuestion.setQuestionType("多选题");
                    } else if (answer.equals("√") || answer.equals("×") || answer.equals("错") || answer.equals("对")) {
                        currentQuestion.setQuestionType("判断题");
                    }
                } else if (currentQuestion != null) {
                    Matcher optionMatcher = optionPattern.matcher(line);
                    while (optionMatcher.find()) {
                        String optionKey = optionMatcher.group(1);
                        String optionValue = optionMatcher.group(2).trim();
                        optionsMap.put(optionKey, optionValue);
                    }
                }
            }

            if (currentQuestion != null) {
                String optionsJson = objectMapper.writeValueAsString(optionsMap);
                currentQuestion.setOptions(optionsJson);
                currentQuestion.setQuestionText(currentQuestion.getQuestionText().replaceAll("[\\(（][A-D]+[\\)）]", "").trim());
                if (currentQuestion.getAnswer() == null) currentQuestion.setAnswer("未提供答案");
                questions.add(currentQuestion);
            }
        } else if ("doc".equals(fileExtension)) {
            HWPFDocument doc = new HWPFDocument(inputStream);
            WordExtractor extractor = new WordExtractor(doc);
            String[] paragraphs = extractor.getParagraphText();

            Questions currentQuestion = null;
            Map<String, String> optionsMap = new HashMap<>();
            StringBuilder fullText = new StringBuilder();

            for (String para : paragraphs) {
                String text = para.trim();
                if (text.isEmpty()) continue;

                text = text.replaceAll("\\u3000", " ");
                Matcher matcher = bracketSpacePattern.matcher(text);
                StringBuffer cleanedText = new StringBuffer();
                while (matcher.find()) {
                    String leftBracket = matcher.group().contains("（") ? "（" : "(";
                    String content = matcher.group(1) != null ? matcher.group(1) : "";
                    String rightBracket = matcher.group().contains("）") ? "）" : ")";
                    matcher.appendReplacement(cleanedText, leftBracket + content + rightBracket);
                }
                matcher.appendTail(cleanedText);

                String resultText = cleanedText.toString();
                fullText.append(resultText).append("\n");
            }

            String[] lines = fullText.toString().split("\\r?\\n");
            for (String line : lines) {
                line = line.trim();
                if (line.isEmpty()) continue;

                Matcher questionMatcher = questionPattern.matcher(line);
                Matcher answerLineMatcher = answerLinePattern.matcher(line);

                if (questionMatcher.matches()) {
                    if (currentQuestion != null) {
                        String optionsJson = objectMapper.writeValueAsString(optionsMap);
                        currentQuestion.setOptions(optionsJson);
                        currentQuestion.setQuestionText(currentQuestion.getQuestionText().replaceAll("[\\(（][A-D]+[\\)）]", "").trim());
                        if (currentQuestion.getAnswer() == null) currentQuestion.setAnswer("未提供答案");
                        questions.add(currentQuestion);
                    }

                    currentQuestion = new Questions();
                    currentQuestion.setFileId(fileId);
                    currentQuestion.setUserId(userId);
                    currentQuestion.setQuestionText(line);
                    currentQuestion.setCreateTime(LocalDateTime.now());
                    currentQuestion.setUpdateTime(LocalDateTime.now());
                    currentQuestion.setQuestionType("选择题");

                    Matcher answerMatcher = answerPattern.matcher(line);
                    if (answerMatcher.find()) {
                        String answer = answerMatcher.group(1).trim();
                        if (!answer.isEmpty()) {
                            currentQuestion.setAnswer(answer);
                            currentQuestion.setQuestionText(line.replaceAll("[\\(（][A-D]+[\\)）]", "").trim());
                            if (answer.length() > 1) {
                                currentQuestion.setQuestionType("多选题");
                            } else if (answer.equals("√") || answer.equals("×")) {
                                currentQuestion.setQuestionType("判断题");
                            }
                        }
                    }

                    optionsMap = new HashMap<>();
                } else if (answerLineMatcher.matches() && currentQuestion != null) {
                    String answer = answerLineMatcher.group(1).trim();
                    currentQuestion.setAnswer(answer);
                    if (answer.length() > 1) {
                        currentQuestion.setQuestionType("多选题");
                    } else if (answer.equals("√") || answer.equals("×") || answer.equals("错") || answer.equals("对")) {
                        currentQuestion.setQuestionType("判断题");
                    }
                } else if (currentQuestion != null) {
                    Matcher optionMatcher = optionPattern.matcher(line);
                    while (optionMatcher.find()) {
                        String optionKey = optionMatcher.group(1);
                        String optionValue = optionMatcher.group(2).trim();
                        optionsMap.put(optionKey, optionValue);
                    }
                }
            }

            if (currentQuestion != null) {
                String optionsJson = objectMapper.writeValueAsString(optionsMap);
                currentQuestion.setOptions(optionsJson);
                currentQuestion.setQuestionText(currentQuestion.getQuestionText().replaceAll("[\\(（][A-D]+[\\)）]", "").trim());
                if (currentQuestion.getAnswer() == null) currentQuestion.setAnswer("未提供答案");
                questions.add(currentQuestion);
            }
        }

        log.info("成功处理 Word 文件: {}, 用户ID: {}, 解析出 {} 道题目", filename, userId, questions.size());
        return questions;
    }

    private List<Questions> parsePDF(InputStream inputStream, String filename, Integer fileId, Integer userId) throws IOException {
        List<Questions> questions = new ArrayList<>();

        try (PDDocument document = PDDocument.load(inputStream)) {
            PDFTextStripper stripper = new PDFTextStripper();
            String text = stripper.getText(document);

            text = text.replaceAll("\\u3000", " ");
            Pattern bracketSpacePattern = Pattern.compile("[\\(（]\\s*([A-D]+)?\\s*[\\)）]");
            Matcher matcher = bracketSpacePattern.matcher(text);
            StringBuffer cleanedText = new StringBuffer();
            while (matcher.find()) {
                String leftBracket = matcher.group().contains("（") ? "（" : "(";
                String content = matcher.group(1) != null ? matcher.group(1) : "";
                String rightBracket = matcher.group().contains("）") ? "）" : ")";
                matcher.appendReplacement(cleanedText, leftBracket + content + rightBracket);
            }
            matcher.appendTail(cleanedText);

            String[] lines = cleanedText.toString().split("\\r?\\n");
            Questions currentQuestion = null;
            Map<String, String> optionsMap = new HashMap<>();
            Pattern questionPattern = Pattern.compile("^\\d+[、,.].*");
            Pattern optionPattern = Pattern.compile("([A-D])\\.?\\s*(.*?)(?=([A-D]\\.|$))");
            Pattern answerPattern = Pattern.compile("[\\(（]\\s*([A-D]+)\\s*[\\)）]");
            Pattern answerLinePattern = Pattern.compile("^答案[:：]\\s*([A-D]+|栈|队列|√|×|错|对)(?:、\\d+)?");

            for (String line : lines) {
                line = line.trim();
                if (line.isEmpty()) continue;

                Matcher questionMatcher = questionPattern.matcher(line);
                Matcher answerLineMatcher = answerLinePattern.matcher(line);

                if (questionMatcher.matches()) {
                    if (currentQuestion != null) {
                        String optionsJson = objectMapper.writeValueAsString(optionsMap);
                        currentQuestion.setOptions(optionsJson);
                        currentQuestion.setQuestionText(currentQuestion.getQuestionText().replaceAll("[\\(（][A-D]+[\\)）]", "").trim());
                        if (currentQuestion.getAnswer() == null) currentQuestion.setAnswer("未提供答案");
                        questions.add(currentQuestion);
                    }

                    currentQuestion = new Questions();
                    currentQuestion.setFileId(fileId);
                    currentQuestion.setUserId(userId);
                    currentQuestion.setQuestionText(line);
                    currentQuestion.setCreateTime(LocalDateTime.now());
                    currentQuestion.setUpdateTime(LocalDateTime.now());
                    currentQuestion.setQuestionType("选择题");

                    Matcher answerMatcher = answerPattern.matcher(line);
                    if (answerMatcher.find()) {
                        String answer = answerMatcher.group(1).trim();
                        if (!answer.isEmpty()) {
                            currentQuestion.setAnswer(answer);
                            currentQuestion.setQuestionText(line.replaceAll("[\\(（][A-D]+[\\)）]", "").trim());
                            if (answer.length() > 1) {
                                currentQuestion.setQuestionType("多选题");
                            } else if (answer.equals("√") || answer.equals("×")) {
                                currentQuestion.setQuestionType("判断题");
                            }
                        }
                    }

                    optionsMap = new HashMap<>();
                } else if (answerLineMatcher.matches() && currentQuestion != null) {
                    String answer = answerLineMatcher.group(1).trim();
                    currentQuestion.setAnswer(answer);
                    if (answer.length() > 1) {
                        currentQuestion.setQuestionType("多选题");
                    } else if (answer.equals("√") || answer.equals("×") || answer.equals("错") || answer.equals("对")) {
                        currentQuestion.setQuestionType("判断题");
                    }
                } else if (currentQuestion != null) {
                    Matcher optionMatcher = optionPattern.matcher(line);
                    while (optionMatcher.find()) {
                        String optionKey = optionMatcher.group(1);
                        String optionValue = optionMatcher.group(2).trim();
                        optionsMap.put(optionKey, optionValue);
                    }
                }
            }

            if (currentQuestion != null) {
                String optionsJson = objectMapper.writeValueAsString(optionsMap);
                currentQuestion.setOptions(optionsJson);
                currentQuestion.setQuestionText(currentQuestion.getQuestionText().replaceAll("[\\(（][A-D]+[\\)）]", "").trim());
                if (currentQuestion.getAnswer() == null) currentQuestion.setAnswer("未提供答案");
                questions.add(currentQuestion);
            }

            log.info("成功处理 PDF 文件: {}, 用户ID: {}, 解析出 {} 道题目", filename, userId, questions.size());
        } catch (IOException e) {
            log.error("解析 PDF 文件失败: {}, 用户ID: {}, 错误: {}", filename, userId, e.getMessage());
            throw e;
        }

        return questions;
    }

    private List<Questions> parseExcel(InputStream inputStream, String filename, Integer fileId, Integer userId) throws IOException {
        List<Questions> questions = new ArrayList<>();
        Workbook workbook = "xlsx".equals(filename.substring(filename.lastIndexOf(".") + 1).toLowerCase()) ?
                new XSSFWorkbook(inputStream) : new HSSFWorkbook(inputStream);
        Sheet sheet = workbook.getSheetAt(0);

        Pattern questionPattern = Pattern.compile("^\\d+[、,.].*");
        Pattern answerPattern = Pattern.compile("[\\(（]\\s*([A-D]+)\\s*[\\)）]");
        Pattern answerLinePattern = Pattern.compile("^答案[:：]\\s*([A-D]+|栈|队列|√|×|错|对)(?:、\\d+)?");
        Pattern bracketSpacePattern = Pattern.compile("[\\(（]\\s*([A-D]+)?\\s*[\\)）]");

        int lastRowNum = -1;
        for (Row row : sheet) {
            if (row.getCell(0) == null) continue;
            String questionText = row.getCell(0).getStringCellValue().trim();
            if (questionText.isEmpty()) continue;

            questionText = questionText.replaceAll("\\u3000", " ");
            Matcher matcher = bracketSpacePattern.matcher(questionText);
            StringBuffer cleanedText = new StringBuffer();
            while (matcher.find()) {
                String leftBracket = matcher.group().contains("（") ? "（" : "(";
                String content = matcher.group(1) != null ? matcher.group(1) : "";
                String rightBracket = matcher.group().contains("）") ? "）" : ")";
                matcher.appendReplacement(cleanedText, leftBracket + content + rightBracket);
            }
            matcher.appendTail(cleanedText);
            questionText = cleanedText.toString();

            Matcher questionMatcher = questionPattern.matcher(questionText);
            Matcher answerLineMatcher = answerLinePattern.matcher(questionText);

            if (questionMatcher.matches()) {
                Questions question = new Questions();
                question.setFileId(fileId);
                question.setUserId(userId);
                question.setQuestionText(questionText);
                question.setQuestionType("选择题");
                question.setCreateTime(LocalDateTime.now());
                question.setUpdateTime(LocalDateTime.now());

                Map<String, String> optionsMap = new HashMap<>();
                for (int i = 1; i <= 4; i++) {
                    if (row.getCell(i) != null) {
                        String option = row.getCell(i).getStringCellValue().trim();
                        if (!option.isEmpty()) {
                            optionsMap.put(String.valueOf((char) ('A' + i - 1)), option);
                        }
                    }
                }
                question.setOptions(objectMapper.writeValueAsString(optionsMap));

                if (row.getCell(5) != null) {
                    String answer = row.getCell(5).getStringCellValue().trim();
                    if (!answer.isEmpty()) {
                        question.setAnswer(answer);
                        if (answer.length() > 1) {
                            question.setQuestionType("多选题");
                        } else if (answer.equals("√") || answer.equals("×") || answer.equals("错") || answer.equals("对")) {
                            question.setQuestionType("判断题");
                        }
                    }
                }

                if (question.getAnswer() == null) {
                    Matcher answerMatcher = answerPattern.matcher(questionText);
                    if (answerMatcher.find()) {
                        String answer = answerMatcher.group(1).trim();
                        if (!answer.isEmpty()) {
                            question.setAnswer(answer);
                            question.setQuestionText(questionText.replaceAll("[\\(（][A-D]+[\\)）]", "").trim());
                            if (answer.length() > 1) {
                                question.setQuestionType("多选题");
                            } else if (answer.equals("√") || answer.equals("×")) {
                                question.setQuestionType("判断题");
                            }
                        }
                    }
                }

                questions.add(question);
                lastRowNum = row.getRowNum();
            } else if (answerLineMatcher.matches() && lastRowNum >= 0) {
                Row prevRow = sheet.getRow(lastRowNum);
                if (prevRow != null && questions.size() > 0) {
                    Questions lastQuestion = questions.get(questions.size() - 1);
                    String answer = answerLineMatcher.group(1).trim();
                    lastQuestion.setAnswer(answer);
                    if (answer.length() > 1) {
                        lastQuestion.setQuestionType("多选题");
                    } else if (answer.equals("√") || answer.equals("×") || answer.equals("错") || answer.equals("对")) {
                        lastQuestion.setQuestionType("判断题");
                    }
                }
            }
        }

        workbook.close();
        log.info("成功处理 Excel 文件: {}, 用户ID: {}, 解析出 {} 道题目", filename, userId, questions.size());
        return questions;
    }

    @Override
    public String getQuestionServiceUrl() {
        // 直接返回硬编码地址
        return QUESTION_SERVICE_URL;
    }

    @Override
    public List<Filerecords> getUserFileRecords(Integer userId) {
        try {
            QueryWrapper<Filerecords> queryWrapper = new QueryWrapper<>();
            queryWrapper.eq("user_id", userId);
            List<Filerecords> fileRecords = fileMapper.selectList(queryWrapper);
            return fileRecords != null ? fileRecords : new ArrayList<>();
        } catch (Exception e) {
            log.error("查询用户ID: {} 的试卷记录失败: {}", userId, e.getMessage());
            return new ArrayList<>();
        }
    }

}