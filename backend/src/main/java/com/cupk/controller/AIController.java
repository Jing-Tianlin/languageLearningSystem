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

    private final DeepSeekService aiService;

    /** i+1 句子生成 */
    @PostMapping("/i-plus-one")
    @SuppressWarnings("unchecked")
    public Result<Map<String, Object>> generateSentences(@RequestBody Map<String, Object> body) {
        List<String> knownWords = (List<String>) body.getOrDefault("knownWords", List.of());
        List<String> newWords = (List<String>) body.getOrDefault("newWords", List.of());
        String lang = String.valueOf(body.getOrDefault("langCode", "en"));
        int count = body.containsKey("count") ? ((Number) body.get("count")).intValue() : 3;

        Map<String, Object> result = aiService.generateIPlusOneSentences(knownWords, newWords, lang, count);
        return Result.success(result);
    }

    /** 语法纠错 */
    @PostMapping("/grammar-check")
    public Result<Map<String, Object>> checkGrammar(@RequestBody Map<String, Object> body) {
        String text = String.valueOf(body.getOrDefault("text", ""));
        String lang = String.valueOf(body.getOrDefault("langCode", "en"));
        Map<String, Object> result = aiService.correctGrammar(text, lang);
        return Result.success(result);
    }

    /** 写作评分 */
    @PostMapping("/score-writing")
    public Result<Map<String, Object>> scoreWriting(@RequestBody Map<String, Object> body) {
        String text = String.valueOf(body.getOrDefault("text", ""));
        String lang = String.valueOf(body.getOrDefault("langCode", "en"));
        String topic = String.valueOf(body.getOrDefault("topic", ""));
        Map<String, Object> result = aiService.scoreWriting(text, lang, topic);
        return Result.success(result);
    }

    /** 根据单词/汉语生成例句 */
    @PostMapping("/examples")
    public Result<Map<String, Object>> generateExamples(@RequestBody Map<String, Object> body) {
        String word = String.valueOf(body.getOrDefault("word", ""));
        String lang = String.valueOf(body.getOrDefault("langCode", "en"));
        int count = body.containsKey("count") ? ((Number) body.get("count")).intValue() : 3;
        if (word.isEmpty()) {
            return Result.error(400, "请输入单词或短语");
        }
        Map<String, Object> result = aiService.generateExampleSentences(word, lang, count);
        return Result.success(result);
    }

    /** 智能问答 */
    @PostMapping("/ask")
    public Result<Map<String, Object>> askQuestion(@RequestBody Map<String, Object> body) {
        String question = String.valueOf(body.getOrDefault("question", ""));
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

        List<Map<String, String>> history = (List<Map<String, String>>) body.get("history");
        if (history == null) {
            history = List.of();
        }

        SseEmitter emitter = new SseEmitter(90_000L);

        aiService.streamAnswerQuestion(question, lang, history,
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
        int level = body.containsKey("level") ? ((Number) body.get("level")).intValue() : 0;
        int count = body.containsKey("count") ? ((Number) body.get("count")).intValue() : 5;
        List<Map<String, Object>> result = aiService.generateGrammarPractices(lang, level, count);
        return Result.success(result);
    }

    /** AI生成写作题目 */
    @PostMapping("/generate-writing-prompt")
    public Result<Map<String, Object>> generateWritingPrompt(@RequestBody Map<String, Object> body) {
        String langCode = String.valueOf(body.getOrDefault("langCode", "en"));
        int level = body.containsKey("level") && body.get("level") != null ? ((Number) body.get("level")).intValue() : 1;
        String topic = String.valueOf(body.getOrDefault("topic", ""));
        Map<String, Object> result = aiService.generateWritingPrompt(langCode, level, topic);
        return Result.success(result);
    }

    /** AI生成阅读文章 */
    @PostMapping("/generate-reading")
    public Result<Map<String, Object>> generateReading(@RequestBody Map<String, Object> body) {
        String lang = String.valueOf(body.getOrDefault("langCode", "en"));
        int level = body.containsKey("level") ? ((Number) body.get("level")).intValue() : 2;
        String topic = String.valueOf(body.getOrDefault("topic", ""));
        Map<String, Object> result = aiService.generateReadingArticle(lang, level, topic);
        return Result.success(result);
    }
}
