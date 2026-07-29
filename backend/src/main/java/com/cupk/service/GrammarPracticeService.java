package com.cupk.service;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * GrammarPracticeService — 语法练习与记录服务
 */
@Service
@RequiredArgsConstructor
public class GrammarPracticeService {

    private final JdbcTemplate jdbcTemplate;

    /**
     * 获取某语言某级别的练习题
     */
    public List<Map<String, Object>> getPractices(String langCode, int level) {
        return jdbcTemplate.queryForList(
            "SELECT id, lang_code, level, type, question, answer, hint, explanation, sort_order " +
            "FROM grammar_practices WHERE lang_code = ? AND level = ? ORDER BY sort_order",
            langCode, level);
    }

    /**
     * 提交练习记录
     */
    @Transactional
    public void recordPractice(Long userId, Long practiceId, boolean isCorrect,
                                String answerGiven, String langCode) {
        jdbcTemplate.update(
            "INSERT INTO practice_records (user_id, practice_id, is_correct, answer_given, lang_code) VALUES (?,?,?,?,?)",
            userId, practiceId, isCorrect ? 1 : 0, answerGiven, langCode);
    }

    /**
     * 获取用户某语言的练习统计
     */
    public Map<String, Object> getStats(Long userId, String langCode) {
        Map<String, Object> result = new HashMap<>();

        Long total = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM practice_records WHERE user_id = ? AND lang_code = ?",
            Long.class, userId, langCode);
        total = total != null ? total : 0L;

        Long correct = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM practice_records WHERE user_id = ? AND lang_code = ? AND is_correct = 1",
            Long.class, userId, langCode);
        correct = correct != null ? correct : 0L;

        result.put("totalAttempts", total);
        result.put("correctCount", correct);
        result.put("accuracy", total > 0 ? Math.round(100.0 * correct / total) : 0);
        return result;
    }

    /**
     * 获取用户最近几天的练习记录（用于趋势分析）
     */
    public List<Map<String, Object>> getRecentRecords(Long userId, String langCode, int days) {
        return jdbcTemplate.queryForList(
            "SELECT DATE(create_time) as date, COUNT(*) as count, " +
            "SUM(CASE WHEN is_correct = 1 THEN 1 ELSE 0 END) as correct_count " +
            "FROM practice_records WHERE user_id = ? AND lang_code = ? " +
            "AND create_time >= DATE_SUB(CURDATE(), INTERVAL ? DAY) " +
            "GROUP BY DATE(create_time) ORDER BY date DESC",
            userId, langCode, days);
    }
}
