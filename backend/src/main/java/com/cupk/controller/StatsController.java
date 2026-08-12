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
@RequiredArgsConstructor
public class StatsController {

    private static final Logger log = LoggerFactory.getLogger(StatsController.class);

    private final UserProgressMapper userProgressMapper;
    private final VocabularyMapper vocabularyMapper;
    private final InspectionLogMapper inspectionLogMapper;
    private final JdbcTemplate jdbcTemplate;

    // ===== 核心: 综合掌握度 (基于真实练习数据) =====
    @GetMapping("/weak-points")
    public Result<Map<String, Double>> getWeakPoints(@RequestParam(required = false) Long userId) {
        Long currentUserId = AuthUtil.getCurrentUserId();
        if (currentUserId == null) return Result.error(401, "未登录");
        // 获取用户所有练习记录(包括没有错误的)
        QueryWrapper<UserProgress> qAll = new QueryWrapper<>();
        qAll.eq("user_id", currentUserId);
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
    public Result<Map<String, Object>> getOverview(@RequestParam(required = false) Long userId) {
        Long currentUserId = AuthUtil.getCurrentUserId();
        if (currentUserId == null) return Result.error(401, "未登录");
        QueryWrapper<UserProgress> q = new QueryWrapper<>();
        q.eq("user_id", currentUserId);
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
        iq.eq("user_id", currentUserId);
        long inspections = inspectionLogMapper.selectCount(iq);

        // ===== 游戏化数据 =====
        java.time.LocalDate today = java.time.LocalDate.now();

        // 今日已学（今日有复习记录的词数）
        QueryWrapper<UserProgress> todayQ = new QueryWrapper<>();
        todayQ.eq("user_id", currentUserId).ge("last_review_time", today.atStartOfDay());
        long todayStudied = userProgressMapper.selectCount(todayQ);

        // 待复习（SRS 到期）
        QueryWrapper<UserProgress> dueQ = new QueryWrapper<>();
        dueQ.eq("user_id", currentUserId)
            .isNotNull("next_review_time")
            .le("next_review_time", new java.util.Date())
            .ne("status", 2);
        long dueCount = userProgressMapper.selectCount(dueQ);

        // 错题数（带错误标签的词汇）
        long wrongCount = all.stream()
                .filter(p -> p.getErrorTags() != null && !p.getErrorTags().isEmpty()
                        && !"null".equals(p.getErrorTags()) && !"[]".equals(p.getErrorTags()))
                .count();

        // 连胜：最近 60 天活跃日期集合（词汇复习 + 写作 + 阅读），从今天向前连续
        long streak = 0;
        try {
            Set<String> activeDays = new HashSet<>();
            for (UserProgress p : all) {
                if (p.getLastReviewTime() != null) {
                    activeDays.add(p.getLastReviewTime().toLocalDate().toString());
                }
            }
            List<Map<String, Object>> writes = jdbcTemplate.queryForList(
                "SELECT submitted_at FROM writing_history WHERE user_id = ?", currentUserId);
            for (Map<String, Object> w : writes) {
                java.time.LocalDateTime dt = toLocalDateTime(w.get("submitted_at"));
                if (dt != null) activeDays.add(dt.toLocalDate().toString());
            }
            List<Map<String, Object>> reads = jdbcTemplate.queryForList(
                "SELECT completed_at FROM reading_history WHERE user_id = ?", currentUserId);
            for (Map<String, Object> r : reads) {
                java.time.LocalDateTime dt = toLocalDateTime(r.get("completed_at"));
                if (dt != null) activeDays.add(dt.toLocalDate().toString());
            }
            // 今天没学则连胜断（0），从昨天开始连续也算"今天未断"
            java.time.LocalDate cursor = activeDays.contains(today.toString()) ? today : today.minusDays(1);
            while (activeDays.contains(cursor.toString())) {
                streak++;
                cursor = cursor.minusDays(1);
            }
        } catch (Exception e) {
            log.warn("计算连胜失败: {}", e.getMessage());
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("totalWords", total);
        result.put("masteredWords", mastered);
        result.put("masteryRate", total > 0 ? Math.round(100.0 * mastered / total) : 0);
        result.put("avgFamiliarity", Math.round(avgFam));
        result.put("avgHesitationMs", Math.round(avgHesitation));
        result.put("totalReviews", totalReviews);
        result.put("inspections", inspections);
        result.put("streak", streak);
        result.put("todayStudied", todayStudied);
        result.put("dueCount", dueCount);
        result.put("wrongCount", wrongCount);

        // 写作统计
        try {
            List<Map<String, Object>> writingStats = jdbcTemplate.queryForList(
                "SELECT COUNT(*) as total, MAX(level) as max_level FROM writing_history WHERE user_id = ?", currentUserId);
            if (!writingStats.isEmpty()) {
                result.put("writingCount", writingStats.get(0).get("total"));
                result.put("writingMaxLevel", writingStats.get(0).get("max_level"));
            }
        } catch (Exception e) { log.warn("统计写作数量失败", e); result.put("writingCount", 0); }

        // 阅读统计
        try {
            List<Map<String, Object>> readingStats = jdbcTemplate.queryForList(
                "SELECT COUNT(*) as total, COALESCE(AVG(quiz_score * 1.0 / NULLIF(quiz_total, 0)), 0) as avg_acc FROM reading_history WHERE user_id = ?",
                currentUserId);
            if (!readingStats.isEmpty()) {
                result.put("readingCount", readingStats.get(0).get("total"));
                result.put("readingAvgAccuracy", Math.round(((Number) readingStats.get(0).get("avg_acc")).doubleValue() * 100));
            }
        } catch (Exception e) { log.warn("统计阅读数量失败", e); result.put("readingCount", 0); }

        return Result.success(result);
    }

    // ===== 按语言掌握分布 =====
    @GetMapping("/by-language")
    public Result<Map<String, Object>> getByLanguage(@RequestParam(required = false) Long userId) {
        Long currentUserId = AuthUtil.getCurrentUserId();
        if (currentUserId == null) return Result.error(401, "未登录");
        QueryWrapper<UserProgress> q = new QueryWrapper<>();
        q.eq("user_id", currentUserId);
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

    // ===== 错题本：带错误标签的词汇列表 =====
    @GetMapping("/wrong-words")
    public Result<List<Map<String, Object>>> getWrongWords(@RequestParam(required = false) String langCode) {
        Long currentUserId = AuthUtil.getCurrentUserId();
        if (currentUserId == null) return Result.error(401, "未登录");
        StringBuilder sql = new StringBuilder(
            "SELECT up.vocab_id as id, v.word, v.definition, v.phonetic, v.romanization, v.part_of_speech, " +
            "up.lang_code, up.error_tags, up.last_review_time, up.mastery_level, up.familiarity " +
            "FROM user_progress up LEFT JOIN vocabulary v ON up.vocab_id = v.id " +
            "WHERE up.user_id = ? AND up.is_deleted = 0 " +
            "AND up.error_tags IS NOT NULL AND up.error_tags <> '' AND up.error_tags <> '[]' AND up.error_tags <> 'null'");
        List<Object> params = new ArrayList<>();
        params.add(currentUserId);
        if (langCode != null && !langCode.isEmpty()) {
            sql.append(" AND up.lang_code = ?");
            params.add(langCode);
        }
        sql.append(" ORDER BY up.last_review_time DESC LIMIT 200");
        try {
            List<Map<String, Object>> list = jdbcTemplate.queryForList(sql.toString(), params.toArray());
            return Result.success(list);
        } catch (Exception e) {
            log.warn("查询错题本失败: {}", e.getMessage());
            return Result.success(Collections.emptyList());
        }
    }

    // ===== 综合趋势（词汇复习 + 写作 + 阅读，按类型分级）=====
    @GetMapping("/trend")
    public Result<Map<String, Object>> getTrend(@RequestParam(required = false) Long userId) {
        Long currentUserId = AuthUtil.getCurrentUserId();
        if (currentUserId == null) return Result.error(401, "未登录");
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
        q.eq("user_id", currentUserId)
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
                currentUserId, today.minusDays(days - 1).atStartOfDay());
            for (Map<String, Object> w : writings) {
                java.time.LocalDateTime dt = toLocalDateTime(w.get("submitted_at"));
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
                currentUserId, today.minusDays(days - 1).atStartOfDay());
            for (Map<String, Object> r : readings) {
                java.time.LocalDateTime dt = toLocalDateTime(r.get("completed_at"));
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

    /**
     * JdbcTemplate queryForList 对 MySQL DATETIME 返回 java.sql.Timestamp，
     * 直接强转 LocalDateTime 会抛 ClassCastException，这里做类型安全转换。
     */
    private java.time.LocalDateTime toLocalDateTime(Object value) {
        if (value instanceof java.sql.Timestamp) {
            return ((java.sql.Timestamp) value).toLocalDateTime();
        }
        if (value instanceof java.time.LocalDateTime) {
            return (java.time.LocalDateTime) value;
        }
        return null;
    }

}

