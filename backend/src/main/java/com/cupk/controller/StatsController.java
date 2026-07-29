package com.cupk.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.cupk.common.Result;
import com.cupk.mapper.*;
import com.cupk.pojo.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@RestController
@RequestMapping("/stats")
public class StatsController {

    private static final Logger log = LoggerFactory.getLogger(StatsController.class);

    @Autowired
    private UserProgressMapper userProgressMapper;
    @Autowired
    private VocabularyMapper vocabularyMapper;
    @Autowired
    private InspectionLogMapper inspectionLogMapper;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    // ===== 核心: 综合掌握度 (基于真实练习数据) =====
    @GetMapping("/weak-points")
    public Result<Map<String, Double>> getWeakPoints(@RequestParam Long userId) {
        // 获取用户所有练习记录(包括没有错误的)
        QueryWrapper<UserProgress> qAll = new QueryWrapper<>();
        qAll.eq("user_id", userId);
        List<UserProgress> allList = userProgressMapper.selectList(qAll);

        // 总练习次数(分母) —— 至少为1，避免除零
        int totalAttempts = Math.max(1, allList.size());

        // 聚合每个维度的错误次数
        Map<String, Integer> errCount = new HashMap<>();

        for (UserProgress p : allList) {
            String tags = p.getErrorTags();
            boolean hasError = (tags != null && !tags.isEmpty() && !"null".equals(tags));
            if (hasError) {
                for (String t : tags.replaceAll("[\\[\\]\"]", "").split(",")) {
                    String tag = t.trim();
                    if (!tag.isEmpty()) {
                        errCount.merge(tag, 1, Integer::sum);
                    }
                }
            }
        }

        // 每个维度: 正确率 = 1 - (该维度错误数 / 总练习次数)
        // 使用总练习次数作为统一分母，避免"只做对不加分母"的偏差
        String[] dims = { "spelling", "preposition", "tense", "article", "word_order", "conjugation", "vocabulary" };
        Map<String, Double> result = new LinkedHashMap<>();
        for (String d : dims) {
            int errs = errCount.getOrDefault(d, 0);
            double acc;
            if (totalAttempts > 0) {
                acc = 1.0 - (double) errs / totalAttempts;
            } else {
                acc = 0.0;
            }
            result.put(d, Math.max(0, Math.min(1, Math.round(acc * 100.0) / 100.0)));
        }
        return Result.success(result);
    }

    // ===== 总体指标 =====
    @GetMapping("/overview")
    public Result<Map<String, Object>> getOverview(@RequestParam Long userId) {
        QueryWrapper<UserProgress> q = new QueryWrapper<>();
        q.eq("user_id", userId);
        List<UserProgress> all = userProgressMapper.selectList(q);

        long total = all.size();
        long mastered = all.stream().filter(p -> p.getMasteryLevel() != null && p.getMasteryLevel() >= 3).count();
        double avgFam = all.stream().mapToInt(p -> p.getFamiliarity() != null ? p.getFamiliarity() : 0).average().orElse(0);
        double avgHesitation = all.stream()
                .filter(p -> p.getHesitationMs() != null && p.getHesitationMs() > 0)
                .mapToInt(UserProgress::getHesitationMs).average().orElse(0);
        int totalReviews = all.stream().mapToInt(p -> p.getReviewCount() != null ? p.getReviewCount() : 0).sum();

        // 巡检统计
        QueryWrapper<InspectionLog> iq = new QueryWrapper<>();
        iq.eq("user_id", userId);
        long inspections = inspectionLogMapper.selectCount(iq);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("totalWords", total);
        result.put("masteredWords", mastered);
        result.put("masteryRate", total > 0 ? Math.round(100.0 * mastered / total) : 0);
        result.put("avgFamiliarity", Math.round(avgFam));
        result.put("avgHesitationMs", Math.round(avgHesitation));
        result.put("totalReviews", totalReviews);
        result.put("inspections", inspections);

        // 写作统计
        try {
            List<Map<String, Object>> writingStats = jdbcTemplate.queryForList(
                "SELECT COUNT(*) as total, MAX(level) as max_level FROM writing_history WHERE user_id = ?", userId);
            if (!writingStats.isEmpty()) {
                result.put("writingCount", writingStats.get(0).get("total"));
                result.put("writingMaxLevel", writingStats.get(0).get("max_level"));
            }
        } catch (Exception e) { log.warn("统计写作数量失败", e); result.put("writingCount", 0); }

        // 阅读统计
        try {
            List<Map<String, Object>> readingStats = jdbcTemplate.queryForList(
                "SELECT COUNT(*) as total, COALESCE(AVG(quiz_score * 1.0 / NULLIF(quiz_total, 0)), 0) as avg_acc FROM reading_history WHERE user_id = ?",
                userId);
            if (!readingStats.isEmpty()) {
                result.put("readingCount", readingStats.get(0).get("total"));
                result.put("readingAvgAccuracy", Math.round(((Number) readingStats.get(0).get("avg_acc")).doubleValue() * 100));
            }
        } catch (Exception e) { log.warn("统计阅读数量失败", e); result.put("readingCount", 0); }

        return Result.success(result);
    }

    // ===== 按语言掌握分布 =====
    @GetMapping("/by-language")
    public Result<Map<String, Object>> getByLanguage(@RequestParam Long userId) {
        QueryWrapper<UserProgress> q = new QueryWrapper<>();
        q.eq("user_id", userId);
        List<UserProgress> all = userProgressMapper.selectList(q);

        Map<String, Map<String, Long>> grouped = new LinkedHashMap<>();
        for (UserProgress p : all) {
            String lang = p.getLangCode() != null ? p.getLangCode() : "unknown";
            grouped.computeIfAbsent(lang, k -> {
                Map<String, Long> m = new LinkedHashMap<>();
                m.put("total", 0L); m.put("mastered", 0L);
                return m;
            });
            Map<String, Long> stats = grouped.get(lang);
            stats.put("total", stats.get("total") + 1);
            if (p.getMasteryLevel() != null && p.getMasteryLevel() >= 3) {
                stats.put("mastered", stats.get("mastered") + 1);
            }
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("languages", grouped);
        return Result.success(result);
    }

    // ===== 综合趋势（词汇复习 + 写作 + 阅读，按类型分级）=====
    @GetMapping("/trend")
    public Result<Map<String, Object>> getTrend(@RequestParam Long userId) {
        int days = 7;
        // 日期数组 (MM-DD) 和各类型日均值
        List<String> dayLabels = new ArrayList<>();
        long[] vocabPerDay = new long[days];
        long[] writingPerDay = new long[days];
        long[] readingPerDay = new long[days];

        java.time.LocalDate today = java.time.LocalDate.now();
        for (int i = 0; i < days; i++) {
            java.time.LocalDate date = today.minusDays(days - 1 - i);
            dayLabels.add(date.toString().substring(5));
        }

        // 词汇复习量
        QueryWrapper<UserProgress> q = new QueryWrapper<>();
        q.eq("user_id", userId)
         .ge("last_review_time", today.minusDays(days - 1).atStartOfDay());
        List<UserProgress> list = userProgressMapper.selectList(q);
        for (UserProgress p : list) {
            if (p.getLastReviewTime() != null) {
                int idx = (int) java.time.temporal.ChronoUnit.DAYS.between(today, p.getLastReviewTime().toLocalDate()) + (days - 1);
                if (idx >= 0 && idx < days) vocabPerDay[idx]++;
            }
        }

        // 写作提交历史
        try {
            List<Map<String, Object>> writings = jdbcTemplate.queryForList(
                "SELECT submitted_at FROM writing_history WHERE user_id = ? AND submitted_at >= ?",
                userId, today.minusDays(days - 1).atStartOfDay());
            for (Map<String, Object> w : writings) {
                java.time.LocalDateTime dt = (java.time.LocalDateTime) w.get("submitted_at");
                if (dt != null) {
                    int idx = (int) java.time.temporal.ChronoUnit.DAYS.between(today, dt.toLocalDate()) + (days - 1);
                    if (idx >= 0 && idx < days) writingPerDay[idx]++;
                }
            }
        } catch (Exception e) {
            log.warn("写作历史查询失败: {}", e.getMessage());
        }

        // 阅读提交历史
        try {
            List<Map<String, Object>> readings = jdbcTemplate.queryForList(
                "SELECT completed_at FROM reading_history WHERE user_id = ? AND completed_at >= ?",
                userId, today.minusDays(days - 1).atStartOfDay());
            for (Map<String, Object> r : readings) {
                java.time.LocalDateTime dt = (java.time.LocalDateTime) r.get("completed_at");
                if (dt != null) {
                    int idx = (int) java.time.temporal.ChronoUnit.DAYS.between(today, dt.toLocalDate()) + (days - 1);
                    if (idx >= 0 && idx < days) readingPerDay[idx]++;
                }
            }
        } catch (Exception e) {
            log.warn("阅读历史查询失败: {}", e.getMessage());
        }

        // 日均值
        long total = 0;
        for (int i = 0; i < days; i++) total += vocabPerDay[i] + writingPerDay[i] + readingPerDay[i];
        int avgDaily = Math.round((float) total / days);

        Map<String, Object> trendData = new LinkedHashMap<>();
        trendData.put("days", dayLabels);
        trendData.put("vocab", vocabPerDay);
        trendData.put("writing", writingPerDay);
        trendData.put("reading", readingPerDay);
        trendData.put("avgDaily", avgDaily);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("trend", trendData);
        return Result.success(result);
    }

}

