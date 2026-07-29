package com.cupk.controller;

import com.cupk.common.Result;
import com.cupk.mapper.FavoriteMapper;
import com.cupk.pojo.Favorite;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.*;

/**
 * ReadingController — 分级自适应阅读训练
 *
 * 支持按考试等级筛选文章：
 *   0=A1/N5/1级, 1=A2/N4/2级, 2=B1/N3/3级, 3=B2/N2/4级, 4=C1/N1/5级, 5=C2/-/6级
 */
@RestController
@RequestMapping("/reading")
@RequiredArgsConstructor
public class ReadingController {

    private static final Logger log = LoggerFactory.getLogger(ReadingController.class);

    private final JdbcTemplate jdbcTemplate;
    private final FavoriteMapper favoriteMapper;

    /**
     * 获取某语言某等级的文章列表
     * GET /reading/articles?langCode=en&levelNum=2
     */
    @GetMapping("/articles")
    public Result<List<Map<String, Object>>> getArticlesByLevel(
            @RequestParam(defaultValue = "en") String langCode,
            @RequestParam(defaultValue = "0") int levelNum) {

        List<Map<String, Object>> articles = jdbcTemplate.queryForList(
            "SELECT id, lang_code, title, content, word_count, level, level_num, tags, " +
            "core_vocabulary, quiz_questions " +
            "FROM reading_articles WHERE lang_code = ? AND level_num = ? " +
            "ORDER BY word_count ASC", langCode, levelNum);

        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> article : articles) {
            result.add(buildArticleResult(article, levelNum));
        }
        return Result.success(result);
    }

    /**
     * 获取某语言各等级的文章数量统计
     * GET /reading/level-stats?langCode=en
     */
    @GetMapping("/level-stats")
    public Result<List<Map<String, Object>>> getLevelStats(
            @RequestParam(defaultValue = "en") String langCode) {

        List<Map<String, Object>> stats = jdbcTemplate.queryForList(
            "SELECT level_num, COUNT(*) as count FROM reading_articles " +
            "WHERE lang_code = ? GROUP BY level_num ORDER BY level_num",
            langCode);
        return Result.success(stats);
    }

    /**
     * 获取推荐文章 + 生词 + 题目
     * GET /reading/article?userId=1&langCode=en&levelNum=2&articleId=xxx
     */
    @GetMapping("/article")
    public Result<Map<String, Object>> getArticle(
            @RequestParam Long userId,
            @RequestParam(defaultValue = "en") String langCode,
            @RequestParam(required = false) Integer levelNum,
            @RequestParam(required = false) Long articleId) {

        // 如果指定了 articleId → 直接返回该文章
        if (articleId != null) {
            List<Map<String, Object>> articles = jdbcTemplate.queryForList(
                "SELECT id, lang_code, title, content, word_count, level, level_num, tags, " +
                "core_vocabulary, quiz_questions FROM reading_articles WHERE id = ?", articleId);
            if (articles.isEmpty()) return Result.error(404, "文章不存在");

            Map<String, Object> article = articles.get(0);
            Map<String, Object> result = buildArticleResult(article,
                article.get("level_num") instanceof Number ? ((Number) article.get("level_num")).intValue() : 1);
            result.put("articleList", getAllArticleItems(langCode, articleId));
            return Result.success(result);
        }

        // 如果指定了 levelNum → 从该等级选文章
        if (levelNum != null) {
            List<Map<String, Object>> articles = jdbcTemplate.queryForList(
                "SELECT id, lang_code, title, content, word_count, level, level_num, tags, " +
                "core_vocabulary, quiz_questions " +
                "FROM reading_articles WHERE lang_code = ? AND level_num = ? " +
                "ORDER BY word_count ASC LIMIT 5", langCode, levelNum);

            if (!articles.isEmpty()) {
                Map<String, Object> article = articles.get(0);
                Map<String, Object> result = buildArticleResult(article, levelNum);
                result.put("articleList", getArticlesByLevelItems(langCode, levelNum));
                return Result.success(result);
            }
            return Result.error(404, "该等级暂无阅读文章");
        }

        // 未指定等级 → 自动推荐（原逻辑）
        return getRecommendedArticle(userId, langCode);
    }

    /** 自动推荐文章 */
    private Result<Map<String, Object>> getRecommendedArticle(Long userId, String langCode) {
        // 1. 查询用户历史, 计算推荐等级
        List<Map<String, Object>> history = jdbcTemplate.queryForList(
            "SELECT quiz_score, quiz_total FROM user_reading_records WHERE user_id = ? ORDER BY completed_at DESC LIMIT 5",
            userId);

        int recommendedLevel = 1;
        if (!history.isEmpty()) {
            double totalRate = 0;
            int count = 0;
            int maxLevel = 1;
            for (Map<String, Object> h : history) {
                int score = ((Number) h.get("quiz_score")).intValue();
                int total = ((Number) h.get("quiz_total")).intValue();
                if (total > 0) {
                    totalRate += (double) score / total;
                    count++;
                }
            }
            // 近5次阅读平均正确率
            double avgRate = count > 0 ? totalRate / count : 0;
            List<Map<String, Object>> levelRows = jdbcTemplate.queryForList(
                "SELECT MAX(ra.level_num) AS ml FROM user_reading_records urr " +
                "JOIN reading_articles ra ON ra.id = urr.article_id WHERE urr.user_id = ?", userId);
            Object ml = levelRows.get(0).get("ml");
            if (ml != null) maxLevel = ((Number) ml).intValue();

            // 自适应推荐等级（以用户当前最高等级为基准）
            if (avgRate >= 0.85 && maxLevel < 6) {
                recommendedLevel = maxLevel + 1;  // 正确率≥85%且未封顶→升级
            } else if (avgRate < 0.55 && maxLevel > 1) {
                recommendedLevel = maxLevel - 1;  // 正确率<55%→降级
            } else {
                recommendedLevel = maxLevel;      // 维持当前等级
            }
        }

        // 2. 从推荐等级选文章
        List<Map<String, Object>> articles = jdbcTemplate.queryForList(
            "SELECT id, lang_code, title, content, word_count, level, level_num, tags, " +
            "core_vocabulary, quiz_questions " +
            "FROM reading_articles WHERE lang_code = ? AND level_num <= ? " +
            "ORDER BY level_num DESC, word_count ASC LIMIT 5", langCode, recommendedLevel);

        if (articles.isEmpty()) {
            articles = jdbcTemplate.queryForList(
                "SELECT id, lang_code, title, content, word_count, level, level_num, tags, " +
                "core_vocabulary, quiz_questions " +
                "FROM reading_articles WHERE lang_code = ? " +
                "ORDER BY word_count ASC LIMIT 5", langCode);
        }

        if (articles.isEmpty()) {
            return Result.error(404, "该语言暂无阅读文章");
        }

        Map<String, Object> article = articles.get(0);
        Map<String, Object> result = buildArticleResult(article, recommendedLevel);
        result.put("articleList", getAllArticleItems(langCode,
            article.get("id") instanceof Long ? (Long) article.get("id") : ((Number) article.get("id")).longValue()));
        return Result.success(result);
    }

    /** 组装单篇文章返回数据 */
    private Map<String, Object> buildArticleResult(Map<String, Object> article, int recommendedLevel) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", article.get("id"));
        result.put("title", article.get("title"));
        result.put("content", article.get("content"));
        result.put("wordCount", article.get("word_count"));
        result.put("level", article.get("level"));
        result.put("levelNum", article.get("level_num"));
        result.put("tags", article.get("tags"));
        result.put("recommendedLevel", recommendedLevel);

        try { String v = (String) article.get("core_vocabulary"); if (v != null && !v.isEmpty()) result.put("coreVocabulary", v); } catch (Exception e) { log.debug("读取 core_vocabulary 失败", e); }
        try { String q = (String) article.get("quiz_questions"); if (q != null && !q.isEmpty()) result.put("quizQuestions", q); } catch (Exception e) { log.debug("读取 quiz_questions 失败", e); }
        return result;
    }

    /** 获取语言下所有文章列表(供前端切换) */
    private List<Map<String, Object>> getAllArticleItems(String langCode, Long currentId) {
        List<Map<String, Object>> all = jdbcTemplate.queryForList(
            "SELECT id, title, level, word_count, level_num FROM reading_articles WHERE lang_code = ? ORDER BY level_num ASC, word_count ASC", langCode);
        return formatArticleItems(all, currentId);
    }

    /** 获取某等级下文章列表 */
    private List<Map<String, Object>> getArticlesByLevelItems(String langCode, int levelNum) {
        List<Map<String, Object>> all = jdbcTemplate.queryForList(
            "SELECT id, title, level, word_count, level_num FROM reading_articles WHERE lang_code = ? AND level_num = ? ORDER BY word_count ASC",
            langCode, levelNum);
        return formatArticleItems(all, null);
    }

    private List<Map<String, Object>> formatArticleItems(List<Map<String, Object>> all, Long currentId) {
        List<Map<String, Object>> items = new ArrayList<>();
        for (Map<String, Object> a : all) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", a.get("id"));
            item.put("title", a.get("title"));
            item.put("level", a.get("level"));
            item.put("levelNum", a.get("level_num"));
            item.put("wordCount", a.get("word_count"));
            items.add(item);
        }
        return items;
    }

    /**
     * 提交阅读测验答案
     */
    @PostMapping("/quiz")
    public Result<Map<String, Object>> submitQuiz(@RequestBody Map<String, Object> body) {
        Object userIdRaw = body.get("userId");
        if (userIdRaw == null) return Result.error(400, "userId不能为空");
        Long userId = Long.valueOf(userIdRaw.toString());
        Object articleIdRaw = body.get("articleId");
        if (articleIdRaw == null) return Result.error(400, "articleId不能为空");
        Long articleId = Long.valueOf(articleIdRaw.toString());
        int phase1Duration = body.containsKey("phase1Duration") ? ((Number) body.get("phase1Duration")).intValue() : 0;
        int phase2Duration = body.containsKey("phase2Duration") ? ((Number) body.get("phase2Duration")).intValue() : 0;

        @SuppressWarnings("unchecked")
        List<Integer> userAnswers = (List<Integer>) body.get("answers");

        String quizJson = jdbcTemplate.query(
            "SELECT quiz_questions FROM reading_articles WHERE id = ?",
            (rs) -> rs.next() ? rs.getString("quiz_questions") : null,
            articleId);

        int score = 0;
        int total = 0;
        List<Map<String, Object>> corrections = new ArrayList<>();

        if (quizJson != null && !quizJson.isEmpty() && userAnswers != null) {
            try {
                List<Map<String, Object>> questions =
                    new com.fasterxml.jackson.databind.ObjectMapper().readValue(quizJson, new com.fasterxml.jackson.core.type.TypeReference<List<Map<String, Object>>>() {});
                total = questions.size();

                for (int i = 0; i < total; i++) {
                    Map<String, Object> q = questions.get(i);
                    int correctIndex = ((Number) q.get("answer")).intValue();
                    int userAns = i < userAnswers.size() ? userAnswers.get(i) : -1;
                    boolean isCorrect = (userAns == correctIndex);

                    if (isCorrect) score++;

                    Map<String, Object> correction = new LinkedHashMap<>();
                    correction.put("questionIndex", i);
                    correction.put("correct", isCorrect);
                    correction.put("yourAnswer", userAns);
                    correction.put("correctAnswer", correctIndex);
                    correction.put("explanation", q.getOrDefault("explanation", ""));
                    corrections.add(correction);
                }
            } catch (Exception e) {
                log.warn("解析测验答案失败，使用默认总分", e);
                total = 3;
                score = 0;
            }
        }

        jdbcTemplate.update(
            "INSERT INTO user_reading_records (user_id, article_id, phase1_duration, phase2_duration, " +
            "quiz_score, quiz_total, quiz_answers, completed_at) VALUES (?,?,?,?,?,?,?,?)",
            userId, articleId, phase1Duration, phase2Duration, score, total,
            userAnswers != null ? userAnswers.toString() : "[]",
            LocalDateTime.now());

        // 写入阅读历史
        String title = "AI生成文章";
        String level = "";
        try {
            if (articleId != null) {
                title = jdbcTemplate.queryForObject(
                    "SELECT title FROM reading_articles WHERE id = ?", String.class, articleId);
                level = jdbcTemplate.queryForObject(
                    "SELECT level FROM reading_articles WHERE id = ?", String.class, articleId);
            }
            jdbcTemplate.update(
                "INSERT INTO reading_history (user_id, lang_code, article_title, article_level, quiz_score, quiz_total) VALUES (?,?,?,?,?,?)",
                userId, body.containsKey("langCode") && body.get("langCode") != null ? body.get("langCode").toString() : "", title, level, score, total);
        } catch (Exception e) {
            log.warn("保存阅读历史失败 userId={}, title={}", userId, title, e);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("score", score);
        result.put("total", total);
        result.put("corrections", corrections);
        result.put("message", score >= total * 0.8 ? "优秀! 阅读理解能力很强!" :
                                score >= total * 0.6 ? "不错, 继续努力!" : "建议重新精读一遍文章");
        return Result.success(result);
    }

    /**
     * 生词收藏：词在 vocabulary 表则直接收藏，不在则先入库再收藏
     */
    @PostMapping("/vocab-action")
    public Result<Map<String, Object>> vocabAction(@RequestBody Map<String, Object> body) {
        Object userIdRaw = body.get("userId");
        if (userIdRaw == null) return Result.error(400, "userId不能为空");
        Long userId = Long.valueOf(userIdRaw.toString());
        String word = String.valueOf(body.getOrDefault("word", ""));
        String langCode = String.valueOf(body.getOrDefault("langCode", ""));

        if (word.isEmpty() || langCode.isEmpty()) {
            return Result.error(400, "单词和语种不能为空");
        }

        try {
            // 1. 查词汇库
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT id FROM vocabulary WHERE word = ? AND lang_code = ? LIMIT 1", word, langCode);

            long vocabId;
            if (!rows.isEmpty()) {
                // 词汇库已有，直接收藏
                vocabId = ((Number) rows.get(0).get("id")).longValue();
                log.info("阅读收藏：单词已在词汇库 word={}, vocabId={}", word, vocabId);
            } else {
                // 词汇库没有，先入库
                jdbcTemplate.update(
                    "INSERT INTO vocabulary (word, lang_code, definition) VALUES (?, ?, ?)",
                    word, langCode, "");
                List<Map<String, Object>> newRows = jdbcTemplate.queryForList(
                    "SELECT id FROM vocabulary WHERE word = ? AND lang_code = ? LIMIT 1", word, langCode);
                if (newRows.isEmpty()) {
                    return Result.error(500, "词汇入库后查询失败");
                }
                vocabId = ((Number) newRows.get(0).get("id")).longValue();
                log.info("阅读收藏：新词入库 word={}, vocabId={}", word, vocabId);
            }

            // 2. 写入收藏表
            Favorite fav = new Favorite();
            fav.setUserId(userId);
            fav.setVocabId(vocabId);
            fav.setLangCode(langCode);
            com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<Favorite> qw =
                new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<>();
            qw.eq("user_id", userId).eq("vocab_id", vocabId);
            if (favoriteMapper.selectCount(qw) == 0) {
                favoriteMapper.insert(fav);
            }

            Map<String, Object> data = new LinkedHashMap<>();
            data.put("vocabId", vocabId);
            data.put("word", word);
            return Result.success("已加入生词本", data);
        } catch (Exception e) {
            log.error("阅读收藏失败 word={}, langCode={}", word, langCode, e);
            return Result.error(500, "收藏失败：" + e.getMessage());
        }
    }
}
