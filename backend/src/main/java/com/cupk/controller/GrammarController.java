package com.cupk.controller;

import com.cupk.common.Result;
import com.cupk.service.GrammarLessonService;
import com.cupk.service.GrammarPracticeService;
import com.cupk.service.GrammarService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * GrammarController — 语法中心统一入口
 * 整合：语法教程、分级练习、练习记录、薄弱点分析
 */
@RestController
@RequestMapping("/grammar")
@RequiredArgsConstructor
public class GrammarController {

    private static final Logger log = LoggerFactory.getLogger(GrammarController.class);

    private final GrammarService grammarService;

    private final GrammarLessonService grammarLessonService;

    private final GrammarPracticeService grammarPracticeService;

    // ==================== 语法教程 ====================

    /**
     * 获取某语言的全部语法教程
     * GET /grammar/lessons?langCode=en
     */
    @GetMapping("/lessons")
    public Result<List<Map<String, Object>>> getLessons(
            @RequestParam(defaultValue = "en") String langCode) {
        List<Map<String, Object>> lessons = grammarLessonService.getLessonsWithSections(langCode);
        return Result.success(lessons);
    }

    // ==================== 分级练习 ====================

    /**
     * 获取某语言某级别的练习题
     * GET /grammar/practices?langCode=en&level=0
     */
    @GetMapping("/practices")
    public Result<List<Map<String, Object>>> getPractices(
            @RequestParam(defaultValue = "en") String langCode,
            @RequestParam(defaultValue = "0") int level) {
        List<Map<String, Object>> list = grammarPracticeService.getPractices(langCode, level);
        return Result.success(list);
    }

    /**
     * 提交练习记录
     * POST /grammar/record
     * Body: { userId:1, practiceId:1, isCorrect:true, answerGiven:"goes", langCode:"en" }
     */
    @PostMapping("/record")
    public Result<String> recordPractice(@RequestBody Map<String, Object> body) {
        Long userId = parseLong(body.get("userId"));
        Long practiceId = parseLong(body.get("practiceId"));
        if (userId == null || practiceId == null) {
            return Result.error(400, "缺少必要参数 userId 或 practiceId");
        }

        Boolean isCorrect = Boolean.TRUE.equals(body.get("isCorrect"));
        String answerGiven = getString(body, "answerGiven", "");
        String langCode = getString(body, "langCode", "en");

        grammarPracticeService.recordPractice(userId, practiceId, isCorrect, answerGiven, langCode);
        return Result.success("记录成功");
    }

    /**
     * 获取用户某语言的练习统计
     * GET /grammar/stats?userId=1&langCode=en
     */
    @GetMapping("/stats")
    public Result<Map<String, Object>> getStats(
            @RequestParam Long userId,
            @RequestParam(defaultValue = "en") String langCode) {
        Map<String, Object> stats = grammarPracticeService.getStats(userId, langCode);
        return Result.success(stats);
    }

    /**
     * 获取用户最近练习趋势
     * GET /grammar/trend?userId=1&langCode=en&days=7
     */
    @GetMapping("/trend")
    public Result<List<Map<String, Object>>> getTrend(
            @RequestParam Long userId,
            @RequestParam(defaultValue = "en") String langCode,
            @RequestParam(defaultValue = "7") int days) {
        List<Map<String, Object>> trend = grammarPracticeService.getRecentRecords(userId, langCode, days);
        return Result.success(trend);
    }

    // ==================== 答案校验 & 薄弱点 ====================

    /**
     * 提交语法题答案校验
     * POST /grammar/check
     * Body: { userId:1, errorType:"TENSE_ERROR", correct:false }
     * 返回: { correct:false, ruleCard:"动词时态错误..." }
     */
    @PostMapping("/check")
    public Result<Map<String, Object>> checkAnswer(@RequestBody Map<String, Object> body) {
        Long userId = parseLong(body.get("userId"));
        String errorType = getString(body, "errorType", "TENSE_ERROR");
        Boolean correct = Boolean.TRUE.equals(body.get("correct"));

        Map<String, Object> result = new HashMap<>();
        result.put("correct", correct);
        if (!correct) {
            result.put("ruleCard", grammarService.getRuleCard(errorType));
        }
        return Result.success(result);
    }

    /**
     * 获取顽固薄弱点
     * GET /grammar/stubborn?userId=1
     */
    @GetMapping("/stubborn")
    public Result<Map<String, Object>> getStubborn(@RequestParam Long userId) {
        List<String> tags = grammarService.getStubbornTags(userId);
        Map<String, Object> result = new HashMap<>();
        result.put("stubbornTags", tags);
        result.put("count", tags.size());
        return Result.success(result);
    }

    // ==================== 工具方法 ====================

    private Long parseLong(Object value) {
        if (value == null) return null;
        if (value instanceof Number) return ((Number) value).longValue();
        try {
            return Long.valueOf(value.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private String getString(Map<String, Object> map, String key, String defaultValue) {
        Object value = map.get(key);
        return value != null ? value.toString() : defaultValue;
    }
}
