package com.cupk.controller;

import com.cupk.common.Result;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import lombok.RequiredArgsConstructor;
import java.util.*;

/**
 * HistoryController — 学习历史记录（对话 / 写作 / 阅读）
 */
@RestController
@RequestMapping("/history")
@RequiredArgsConstructor
public class HistoryController {

    private static final Logger log = LoggerFactory.getLogger(HistoryController.class);

    private final JdbcTemplate jdbcTemplate;

    // ==================== AI 聊天 ====================

    @PostMapping("/chat")
    public Result<Void> saveChat(@RequestBody Map<String, Object> body) {
        Long userId = parseLong(body.get("userId"));
        String langCode = getStr(body, "langCode", "");
        String role = getStr(body, "role", "user");
        String content = getStr(body, "content", "");
        if (userId == null || content.isEmpty()) return Result.error(400, "缺少参数");
        jdbcTemplate.update("INSERT INTO ai_chat_history (user_id, lang_code, role, content) VALUES (?,?,?,?)",
            userId, langCode, role, content);
        return Result.success("ok");
    }

    @GetMapping("/chat")
    public Result<List<Map<String, Object>>> getChats(
            @RequestParam Long userId,
            @RequestParam(defaultValue = "50") int limit) {
        List<Map<String, Object>> list = jdbcTemplate.queryForList(
            "SELECT id, role, content, created_at FROM ai_chat_history WHERE user_id = ? ORDER BY created_at DESC LIMIT ?",
            userId, limit);
        Collections.reverse(list); // 正序显示
        return Result.success(list);
    }

    // ==================== 写作 ====================

    @PostMapping("/writing")
    public Result<Void> saveWriting(@RequestBody Map<String, Object> body) {
        Long userId = parseLong(body.get("userId"));
        if (userId == null) return Result.error(400, "缺少 userId");
        jdbcTemplate.update(
            "INSERT INTO writing_history (user_id, lang_code, level, topic, type, submitted_text, prompt_json) VALUES (?,?,?,?,?,?,?)",
            userId, getStr(body, "langCode", ""), parseInt(body.get("level")),
            getStr(body, "topic", ""), getStr(body, "type", ""),
            getStr(body, "submittedText", ""), getStr(body, "prompt_json", ""));
        return Result.success("ok");
    }

    @GetMapping("/writing")
    public Result<List<Map<String, Object>>> getWritings(
            @RequestParam Long userId,
            @RequestParam(defaultValue = "20") int limit) {
        return Result.success(jdbcTemplate.queryForList(
            "SELECT id, topic, type, level, lang_code, submitted_text, prompt_json, score, score_detail, submitted_at FROM writing_history WHERE user_id = ? ORDER BY submitted_at DESC LIMIT ?",
            userId, limit));
    }

    // ==================== 阅读 ====================

    @PostMapping("/reading")
    public Result<Void> saveReading(@RequestBody Map<String, Object> body) {
        Long userId = parseLong(body.get("userId"));
        if (userId == null) return Result.error(400, "缺少 userId");
        jdbcTemplate.update(
            "INSERT INTO reading_history (user_id, lang_code, article_title, article_level, article_id, article_content, core_vocabulary, quiz_questions, quiz_score, quiz_total) VALUES (?,?,?,?,?,?,?,?,?,?)",
            userId, getStr(body, "langCode", ""), getStr(body, "articleTitle", ""),
            getStr(body, "articleLevel", ""), body.get("articleId") != null ? parseLong(body.get("articleId")) : null,
            getStr(body, "articleContent", ""), getStr(body, "coreVocabulary", ""),
            getStr(body, "quizQuestions", ""), parseInt(body.get("quizScore")), parseInt(body.get("quizTotal")));
        return Result.success("ok");
    }

    @GetMapping("/reading")
    public Result<List<Map<String, Object>>> getReadings(
            @RequestParam Long userId,
            @RequestParam(defaultValue = "20") int limit) {
        return Result.success(jdbcTemplate.queryForList(
            "SELECT id, article_title, article_level, article_id, article_content, core_vocabulary, quiz_questions, quiz_score, quiz_total, completed_at FROM reading_history WHERE user_id = ? ORDER BY completed_at DESC LIMIT ?",
            userId, limit));
    }

    // ==================== 工具 ====================

    private Long parseLong(Object v) {
        if (v == null) return null;
        if (v instanceof Number) return ((Number) v).longValue();
        try { return Long.valueOf(v.toString()); } catch (NumberFormatException e) { return null; }
    }
    private String getStr(Map<String, Object> m, String k, String def) {
        Object v = m.get(k);
        return v != null ? v.toString() : def;
    }
    private Integer parseInt(Object v) {
        if (v == null) return null;
        if (v instanceof Number) return ((Number) v).intValue();
        try { return Integer.parseInt(v.toString()); } catch (NumberFormatException e) { return null; }
    }
}
