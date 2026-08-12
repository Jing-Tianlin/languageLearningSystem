package com.cupk.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cupk.common.Result;
import com.cupk.mapper.UserProgressMapper;
import com.cupk.mapper.VocabularyMapper;
import com.cupk.pojo.UserProgress;
import com.cupk.pojo.Vocabulary;
import com.cupk.service.LearningDataService;
import com.cupk.util.AuthUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/progress")
@RequiredArgsConstructor
public class UserProgressController {
    private static final Logger log = LoggerFactory.getLogger(UserProgressController.class);
    private final UserProgressMapper userProgressMapper;
    private final VocabularyMapper vocabularyMapper;
    private final LearningDataService learningDataService;

    @GetMapping("/progresses")
    public Result<Page<UserProgress>> selectPages(@RequestParam(required = false) Long userId,
                                                  @RequestParam(defaultValue = "") String langCode,
                                                  @RequestParam(defaultValue = "1") Integer pageNo,
                                                  @RequestParam(defaultValue = "5") Integer pageSize) {
        // 只能查询自己的学习记录，userId 强制取 token
        Long currentUserId = AuthUtil.getCurrentUserId();
        Page<UserProgress> page = new Page<>(pageNo, pageSize);
        QueryWrapper<UserProgress> queryWrapper = new QueryWrapper<>();
        if (currentUserId != null) {
            queryWrapper.eq("user_id", currentUserId);
        }
        if (!langCode.isEmpty()) {
            queryWrapper.eq("lang_code", langCode);
        }
        queryWrapper.orderByDesc("create_time");
        userProgressMapper.selectPage(page, queryWrapper);
        return Result.success(page);
    }

    @GetMapping("/progresses/{id}")
    public Result<UserProgress> selectById(@PathVariable Long id) {
        UserProgress progress = userProgressMapper.selectById(id);
        if (progress == null) {
            return Result.error(404, "学习记录不存在");
        }
        // 校验记录归属
        Long currentUserId = AuthUtil.getCurrentUserId();
        if (currentUserId == null || !currentUserId.equals(progress.getUserId())) {
            return Result.error(403, "无权访问他人学习记录");
        }
        return Result.success(progress);
    }

    @PostMapping("/progresses")
    public Result<Void> insert(@RequestBody UserProgress progress) {
        Long currentUserId = AuthUtil.getCurrentUserId();
        if (currentUserId == null) {
            return Result.error(401, "未登录");
        }
        progress.setId(null);
        // 归属强制取 token
        progress.setUserId(currentUserId);
        int rows = userProgressMapper.insert(progress);
        if (rows > 0) {
            return Result.success("新增成功");
        }
        return Result.error("新增失败");
    }

    @PutMapping("/progresses")
    public Result<Void> update(@RequestBody UserProgress progress) {
        // 只能修改自己的记录
        Long currentUserId = AuthUtil.getCurrentUserId();
        if (currentUserId == null || progress.getUserId() == null || !currentUserId.equals(progress.getUserId())) {
            return Result.error(403, "无权修改他人学习记录");
        }
        int rows = userProgressMapper.updateById(progress);
        if (rows > 0) {
            return Result.success("修改成功");
        }
        return Result.error("修改失败");
    }

    @DeleteMapping("/progresses/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        UserProgress progress = userProgressMapper.selectById(id);
        if (progress == null) {
            return Result.error(404, "学习记录不存在");
        }
        // 只能删除自己的记录
        Long currentUserId = AuthUtil.getCurrentUserId();
        if (currentUserId == null || !currentUserId.equals(progress.getUserId())) {
            return Result.error(403, "无权删除他人学习记录");
        }
        int rows = userProgressMapper.deleteById(id);
        if (rows > 0) {
            return Result.success("删除成功");
        }
        return Result.error("删除失败");
    }

    /**
     * 上报犹豫度与错误标签 (提议2)
     * POST /progress/report-hesitation
     * Body: { vocabId:5, hesitationMs:3200, correct:true, quality:4, errorTags:["preposition"] }
     *
     * quality 响应质量 0-5（商业化 SRS 自评）：
     *   0=完全遗忘, 1=模糊记得, 2=勉强正确, 3=正确但犹豫, 4=正确且流畅, 5=完美秒答
     */
    @PostMapping("/report-hesitation")
    public Result<Void> reportHesitation(@RequestBody Map<String, Object> body) {
        // userId 强制取 token，忽略前端传入
        Long userId = AuthUtil.getCurrentUserId();
        if (userId == null) return Result.error(401, "未登录");
        Long vocabId = Long.valueOf(body.get("vocabId").toString());
        Integer hesitationMs = body.containsKey("hesitationMs") ? (Integer) body.get("hesitationMs") : 0;
        Boolean correct = body.get("correct") != null ? (Boolean) body.get("correct") : false;
        Integer quality = body.containsKey("quality") && body.get("quality") != null ? ((Number) body.get("quality")).intValue() : null;
        String langCode = body.containsKey("langCode") && body.get("langCode") != null ? body.get("langCode").toString() : "en";
        String errorType = body.containsKey("errorType") && body.get("errorType") != null ? body.get("errorType").toString() : null;

        // 统一走 SM-2 算法
        if (quality != null) {
            learningDataService.recordPracticeAnswer(userId, vocabId, langCode,
                    quality, hesitationMs, errorType);
        } else {
            learningDataService.recordPracticeAnswer(userId, vocabId, langCode,
                    correct != null && correct, hesitationMs, errorType);
        }

        return Result.success("犹豫度已记录");
    }

    /**
     * 获取薄弱词列表（需要复习的单词）
     * GET /progress/weak-words?langCode=en&limit=20
     * 返回词汇完整信息，按优先级排序：过期未复习 > 低熟练度 > 高犹豫度
     */
    @GetMapping("/weak-words")
    public Result<List<Map<String, Object>>> getWeakWords(
            @RequestParam(required = false) Long userId,
            @RequestParam(defaultValue = "en") String langCode,
            @RequestParam(defaultValue = "20") Integer limit) {

        Long currentUserId = AuthUtil.getCurrentUserId();
        if (currentUserId == null) return Result.error(401, "未登录");
        LocalDateTime now = LocalDateTime.now();

        QueryWrapper<UserProgress> query = new QueryWrapper<>();
        query.eq("user_id", currentUserId);
        if (!langCode.isEmpty()) {
            query.eq("lang_code", langCode);
        }

        List<UserProgress> progresses = userProgressMapper.selectList(query);

        List<UserProgress> weakProgresses = progresses.stream()
                .filter(p -> {
                    Integer mastery = p.getMasteryLevel() != null ? p.getMasteryLevel() : 0;
                    LocalDateTime nextReview = p.getNextReviewTime();

                    boolean isExpired = nextReview != null && nextReview.isBefore(now);
                    boolean isLowMastery = mastery <= 1;
                    boolean isHighHesitation = p.getHesitationMs() != null && p.getHesitationMs() > 3000;

                    return isExpired || isLowMastery || isHighHesitation;
                })
                .sorted((a, b) -> {
                    LocalDateTime nowTime = LocalDateTime.now();

                    boolean aExpired = a.getNextReviewTime() != null && a.getNextReviewTime().isBefore(nowTime);
                    boolean bExpired = b.getNextReviewTime() != null && b.getNextReviewTime().isBefore(nowTime);

                    if (aExpired && !bExpired) return -1;
                    if (!aExpired && bExpired) return 1;

                    Integer aMastery = a.getMasteryLevel() != null ? a.getMasteryLevel() : 0;
                    Integer bMastery = b.getMasteryLevel() != null ? b.getMasteryLevel() : 0;
                    if (!aMastery.equals(bMastery)) return aMastery - bMastery;

                    Integer aFam = a.getFamiliarity() != null ? a.getFamiliarity() : 0;
                    Integer bFam = b.getFamiliarity() != null ? b.getFamiliarity() : 0;
                    return aFam - bFam;
                })
                .limit(limit)
                .collect(Collectors.toList());

        List<Long> vocabIds = weakProgresses.stream()
                .map(UserProgress::getVocabId)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        List<Map<String, Object>> result = new ArrayList<>();
        if (!vocabIds.isEmpty()) {
            List<Vocabulary> vocabs = vocabularyMapper.selectBatchIds(vocabIds);
            Map<Long, Vocabulary> vocabMap = vocabs.stream()
                    .collect(Collectors.toMap(Vocabulary::getId, v -> v));

            for (UserProgress p : weakProgresses) {
                Vocabulary v = vocabMap.get(p.getVocabId());
                if (v != null) {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("id", v.getId());
                    item.put("word", v.getWord());
                    item.put("phonetic", v.getPhonetic());
                    item.put("definition", v.getDefinition());
                    item.put("partOfSpeech", v.getPartOfSpeech());
                    item.put("exampleSentence", v.getExampleSentence());
                    item.put("exampleTranslation", v.getExampleTranslation());
                    item.put("langCode", v.getLangCode());
                    item.put("masteryLevel", p.getMasteryLevel());
                    item.put("familiarity", p.getFamiliarity());
                    item.put("nextReviewTime", p.getNextReviewTime() != null ? p.getNextReviewTime().toString() : null);
                    item.put("reviewCount", p.getReviewCount());
                    result.add(item);
                }
            }
        }

        return Result.success(result);
    }

    /**
     * 获取今日待复习单词数量
     * GET /progress/today-count?userId=1&langCode=en
     */
    @GetMapping("/today-count")
    public Result<Map<String, Object>> getTodayReviewCount(
            @RequestParam(required = false) Long userId,
            @RequestParam(defaultValue = "en") String langCode) {

        Long currentUserId = AuthUtil.getCurrentUserId();
        if (currentUserId == null) return Result.error(401, "未登录");
        LocalDateTime now = LocalDateTime.now();

        QueryWrapper<UserProgress> query = new QueryWrapper<>();
        query.eq("user_id", currentUserId);
        if (!langCode.isEmpty()) {
            query.eq("lang_code", langCode);
        }
        query.lt("next_review_time", now);

        long expiredCount = userProgressMapper.selectCount(query);

        query.clear();
        query.eq("user_id", currentUserId);
        if (!langCode.isEmpty()) {
            query.eq("lang_code", langCode);
        }
        query.eq("mastery_level", 3);
        long masteredCount = userProgressMapper.selectCount(query);

        query.clear();
        query.eq("user_id", currentUserId);
        if (!langCode.isEmpty()) {
            query.eq("lang_code", langCode);
        }
        long totalCount = userProgressMapper.selectCount(query);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("todayReviewCount", expiredCount);
        result.put("masteredCount", masteredCount);
        result.put("totalCount", totalCount);
        result.put("unmasteredCount", totalCount - masteredCount);

        return Result.success(result);
    }
}
