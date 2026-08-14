package com.cupk.controller;

import com.cupk.common.Result;
import com.cupk.service.DeepSeekService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.*;

@RestController
@RequestMapping("/ai")
@RequiredArgsConstructor
public class AIController {

    private static final Logger log = LoggerFactory.getLogger(AIController.class);

    // ==== 入参上限（防止恶意请求放大 API 成本） ====
    private static final int MAX_TEXT_LEN = 5000;       // 语法纠错 / 写作文本
    private static final int MAX_QUESTION_LEN = 2000;   // 问答问题
    private static final int MAX_WORD_LEN = 200;        // 单词/短语
    private static final int MAX_TOPIC_LEN = 200;       // 写作/阅读主题
    private static final int MAX_COUNT = 10;            // 生成数量
    private static final int MAX_LIST_SIZE = 100;       // 词汇列表长度
    private static final int MAX_HISTORY = 20;          // 历史对话条数
    private static final int MAX_HISTORY_ITEM_LEN = 1000; // 单条历史内容长度
    private static final int MAX_LEVEL = 3;             // 材料等级上限

    private final DeepSeekService aiService;

    /** i+1 句子生成 */
    @PostMapping("/i-plus-one")
    @SuppressWarnings("unchecked")
    public Result<Map<String, Object>> generateSentences(@RequestBody Map<String, Object> body) {
        List<String> knownWords = (List<String>) body.getOrDefault("knownWords", List.of());
        List<String> newWords = (List<String>) body.getOrDefault("newWords", List.of());
        if (knownWords.size() > MAX_LIST_SIZE || newWords.size() > MAX_LIST_SIZE) {
            return Result.error(400, "词汇列表过长（最多 " + MAX_LIST_SIZE + " 个）");
        }
        String lang = String.valueOf(body.getOrDefault("langCode", "en"));
        String countErr = checkCount(body);
        if (countErr != null) {
            return Result.error(400, countErr);
        }
        int count = body.get("count") != null ? ((Number) body.get("count")).intValue() : 3;

        Map<String, Object> result = aiService.generateIPlusOneSentences(knownWords, newWords, lang, count);
        return Result.success(result);
    }

    /** 语法纠错 */
    @PostMapping("/grammar-check")
    public Result<Map<String, Object>> checkGrammar(@RequestBody Map<String, Object> body) {
        String text = String.valueOf(body.getOrDefault("text", ""));
        if (text.isBlank()) {
            return Result.error(400, "文本不能为空");
        }
        if (text.length() > MAX_TEXT_LEN) {
            return Result.error(400, "文本过长（最多 " + MAX_TEXT_LEN + " 字符）");
        }
        String lang = String.valueOf(body.getOrDefault("langCode", "en"));
        Map<String, Object> result = aiService.correctGrammar(text, lang);
        return Result.success(result);
    }

    /** 写作评分 */
    @PostMapping("/score-writing")
    public Result<Map<String, Object>> scoreWriting(@RequestBody Map<String, Object> body) {
        String text = String.valueOf(body.getOrDefault("text", ""));
        if (text.isBlank()) {
            return Result.error(400, "文本不能为空");
        }
        if (text.length() > MAX_TEXT_LEN) {
            return Result.error(400, "文本过长（最多 " + MAX_TEXT_LEN + " 字符）");
        }
        String lang = String.valueOf(body.getOrDefault("langCode", "en"));
        String topic = String.valueOf(body.getOrDefault("topic", ""));
        Map<String, Object> result = aiService.scoreWriting(text, lang, topic);
        return Result.success(result);
    }

    /** 根据单词/汉语生成例句 */
    @PostMapping("/examples")
    public Result<Map<String, Object>> generateExamples(@RequestBody Map<String, Object> body) {
        String word = String.valueOf(body.getOrDefault("word", ""));
        if (word.isBlank()) {
            return Result.error(400, "请输入单词或短语");
        }
        if (word.length() > MAX_WORD_LEN) {
            return Result.error(400, "单词过长（最多 " + MAX_WORD_LEN + " 字符）");
        }
        String lang = String.valueOf(body.getOrDefault("langCode", "en"));
        String countErr = checkCount(body);
        if (countErr != null) {
            return Result.error(400, countErr);
        }
        int count = body.get("count") != null ? ((Number) body.get("count")).intValue() : 3;
        Map<String, Object> result = aiService.generateExampleSentences(word, lang, count);
        return Result.success(result);
    }

    /** 智能问答 */
    @PostMapping("/ask")
    public Result<Map<String, Object>> askQuestion(@RequestBody Map<String, Object> body) {
        String question = String.valueOf(body.getOrDefault("question", ""));
        if (question.isBlank()) {
            return Result.error(400, "问题不能为空");
        }
        if (question.length() > MAX_QUESTION_LEN) {
            return Result.error(400, "问题过长（最多 " + MAX_QUESTION_LEN + " 字符）");
        }
        String lang = String.valueOf(body.getOrDefault("langCode", "en"));
        Map<String, Object> result = aiService.answerQuestion(question, lang);
        return Result.success(result);
    }

    /** 智能问答（流式 SSE，支持上下文记忆） */
    @PostMapping(value = "/ask/stream", produces = "text/event-stream")
    @SuppressWarnings("unchecked")
    public SseEmitter askQuestionStream(@RequestBody Map<String, Object> body) {
        String question = String.valueOf(body.getOrDefault("question", ""));
        String lang = String.valueOf(body.getOrDefault("langCode", "en"));

        Object rawHistory = body.get("history");
        List<?> history = rawHistory instanceof List<?> l ? l : List.of();

        SseEmitter emitter = new SseEmitter(90_000L);

        // 入参校验失败也走 SSE 错误通道，保证流式协议一致
        if (question.isBlank() || question.length() > MAX_QUESTION_LEN) {
            try {
                emitter.send(SseEmitter.event().data("[问题为空或过长（最多 " + MAX_QUESTION_LEN + " 字符）]"));
            } catch (IOException e) {
                emitter.completeWithError(e);
                return emitter;
            }
            emitter.complete();
            return emitter;
        }
        if (history.size() > MAX_HISTORY) {
            history = history.subList(history.size() - MAX_HISTORY, history.size());
        }

        // 防御性清洗：过滤非法条目并限制单条长度，防止超长 history 放大 token 成本
        List<Map<String, String>> sanitized = new ArrayList<>(history.size());
        for (Object item : history) {
            if (!(item instanceof Map<?, ?> m)) continue;
            Object role = m.get("role");
            Object content = m.get("content");
            if (!(role instanceof String r) || !(content instanceof String c)) continue;
            if (!"user".equals(r) && !"assistant".equals(r)) continue;
            if (c.isBlank()) continue;
            if (c.length() > MAX_HISTORY_ITEM_LEN) {
                c = c.substring(0, MAX_HISTORY_ITEM_LEN);
            }
            sanitized.add(Map.of("role", r, "content", c));
        }

        List<Map<String, String>> finalHistory = sanitized;
        aiService.streamAnswerQuestion(question, lang, finalHistory,
            token -> {
                try {
                    emitter.send(SseEmitter.event().data(token));
                } catch (IOException e) {
                    log.debug("SSE 发送中断: {}", e.getMessage());
                }
            },
            errorMsg -> {
                log.warn("流式AI异常: question={}, error={}",
                    question.substring(0, Math.min(30, question.length())), errorMsg);
                try {
                    emitter.send(SseEmitter.event().data("[AI 服务暂不可用，请稍后重试]"));
                    emitter.complete();
                } catch (IOException e) {
                    emitter.completeWithError(e);
                }
            },
            () -> {
                try {
                    emitter.complete();
                } catch (Exception e) {
                    log.debug("SseEmitter 关闭异常: {}", e.getMessage());
                }
            }
        );

        return emitter;
    }

    /** AI生成语法练习题 */
    @PostMapping("/generate-practices")
    public Result<List<Map<String, Object>>> generatePractices(@RequestBody Map<String, Object> body) {
        String lang = String.valueOf(body.getOrDefault("langCode", "en"));
        String levelErr = checkLevel(body);
        if (levelErr != null) {
            return Result.error(400, levelErr);
        }
        int level = ((Number) body.get("level")).intValue();
        String countErr = checkCount(body);
        if (countErr != null) {
            return Result.error(400, countErr);
        }
        int count = body.get("count") != null ? ((Number) body.get("count")).intValue() : 5;
        List<Map<String, Object>> result = aiService.generateGrammarPractices(lang, level, count);
        return Result.success(result);
    }

    /** AI生成写作题目 */
    @PostMapping("/generate-writing-prompt")
    public Result<Map<String, Object>> generateWritingPrompt(@RequestBody Map<String, Object> body) {
        String langCode = String.valueOf(body.getOrDefault("langCode", "en"));
        String levelErr = checkLevel(body);
        if (levelErr != null) {
            return Result.error(400, levelErr);
        }
        int level = body.get("level") != null ? ((Number) body.get("level")).intValue() : 1;
        String topic = String.valueOf(body.getOrDefault("topic", ""));
        if (topic.length() > MAX_TOPIC_LEN) {
            return Result.error(400, "主题过长（最多 " + MAX_TOPIC_LEN + " 字符）");
        }
        Map<String, Object> result = aiService.generateWritingPrompt(langCode, level, topic);
        return Result.success(result);
    }

    /** AI生成阅读文章 */
    @PostMapping("/generate-reading")
    public Result<Map<String, Object>> generateReading(@RequestBody Map<String, Object> body) {
        String lang = String.valueOf(body.getOrDefault("langCode", "en"));
        String levelErr = checkLevel(body);
        if (levelErr != null) {
            return Result.error(400, levelErr);
        }
        int level = body.get("level") != null ? ((Number) body.get("level")).intValue() : 2;
        String topic = String.valueOf(body.getOrDefault("topic", ""));
        if (topic.length() > MAX_TOPIC_LEN) {
            return Result.error(400, "主题过长（最多 " + MAX_TOPIC_LEN + " 字符）");
        }
        Map<String, Object> result = aiService.generateReadingArticle(lang, level, topic);
        return Result.success(result);
    }

    // ==== 校验工具 ====

    /** 校验 count 参数：缺失/非法/越界返回错误信息，合法返回 null */
    private String checkCount(Map<String, Object> body) {
        Object raw = body.get("count");
        if (raw == null) {
            return null;
        }
        if (!(raw instanceof Number)) {
            return "count 参数必须是数字";
        }
        int count = ((Number) raw).intValue();
        if (count < 1 || count > MAX_COUNT) {
            return "count 参数需在 1-" + MAX_COUNT + " 之间";
        }
        return null;
    }

    /** 校验 level 参数：缺失/非法/越界返回错误信息，合法返回 null */
    private String checkLevel(Map<String, Object> body) {
        Object raw = body.get("level");
        if (raw == null) {
            return null;
        }
        if (!(raw instanceof Number)) {
            return "level 参数必须是数字";
        }
        int level = ((Number) raw).intValue();
        if (level < 0 || level > MAX_LEVEL) {
            return "level 参数需在 0-" + MAX_LEVEL + " 之间";
        }
        return null;
    }
}
