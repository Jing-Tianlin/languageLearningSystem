package com.cupk.controller;

import com.cupk.common.Result;
import com.cupk.mapper.UserMapper;
import com.cupk.mapper.UserProgressMapper;
import com.cupk.pojo.User;
import com.cupk.pojo.UserProgress;
import com.cupk.service.LearningDataService;
import com.cupk.util.AuthUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * PracticeController — 练习数据实时写入接口
 *
 * 所有练习（每日练习 / 语法 / 阅读）的结果
 * 统一通过此控制器写入数据库
 */
@RestController
@RequestMapping("/practice")
@RequiredArgsConstructor
public class PracticeController {

    private static final Logger log = LoggerFactory.getLogger(PracticeController.class);

    private final LearningDataService dataService;
    private final UserMapper userMapper;
    private final UserProgressMapper userProgressMapper;

    /**
     * 记录练习答案 (每日练习 / 语法练习通用)
     * POST /practice/record
     * Body: { userId:1, vocabId:5, langCode:"en", correct:true, quality:4, hesitationMs:1200, errorType:"TENSE" }
     *
     * quality 响应质量 0-5（商业化 SRS 自评）：
     *   0=完全遗忘, 1=模糊记得, 2=勉强正确, 3=正确但犹豫, 4=正确且流畅, 5=完美秒答
     * 不传 quality 时，后端根据 correct + hesitationMs 自动推断。
     */
    @PostMapping("/record")
    public Result<Map<String, Object>> recordAnswer(@RequestBody Map<String, Object> body) {
        Long userId = AuthUtil.getCurrentUserId();
        if (userId == null) return Result.error(401, "未登录");
        Long vocabId = body.containsKey("vocabId") && body.get("vocabId") != null ? Long.valueOf(body.get("vocabId").toString()) : 0L;
        String langCode = body.containsKey("langCode") && body.get("langCode") != null ? body.get("langCode").toString() : "en";
        Boolean correct = (Boolean) body.get("correct");
        Integer quality = body.containsKey("quality") && body.get("quality") != null ? ((Number) body.get("quality")).intValue() : null;
        Integer hesitationMs = body.containsKey("hesitationMs") ? ((Number) body.get("hesitationMs")).intValue() : 0;
        String errorType = body.get("errorType") != null ? body.get("errorType").toString() : null;

        UserProgress progress;
        if (quality != null) {
            progress = dataService.recordPracticeAnswer(
                    userId, vocabId, langCode,
                    quality,
                    hesitationMs != null ? hesitationMs : 0,
                    errorType
            );
        } else {
            progress = dataService.recordPracticeAnswer(
                    userId, vocabId, langCode,
                    correct != null && correct,
                    hesitationMs != null ? hesitationMs : 0,
                    errorType
            );
        }

        Map<String, Object> result = new java.util.LinkedHashMap<>();
        result.put("masteryLevel", progress.getMasteryLevel());
        result.put("familiarity", progress.getFamiliarity());
        result.put("reviewCount", progress.getReviewCount());
        result.put("nextReview", progress.getNextReviewTime() != null ? progress.getNextReviewTime().toString() : null);
        result.put("intervalDays", progress.getIntervalDays());
        result.put("repetition", progress.getRepetition());
        result.put("easeFactor", progress.getEaseFactor());
        return Result.success(result);
    }

    /** 登录时调用, 更新打卡天数 */
    @PostMapping("/checkin")
    public Result<String> checkin(@RequestBody Map<String, Object> body) {
        Long userId = AuthUtil.getCurrentUserId();
        if (userId == null) return Result.error(401, "未登录");
        dataService.updateStudyStreak(userId);
        return Result.success("打卡成功");
    }

    /** 今日练习统计 */
    @GetMapping("/today-stats")
    public Result<Map<String, Object>> todayStats(@RequestParam(required = false) Long userId) {
        Long currentUserId = AuthUtil.getCurrentUserId();
        if (currentUserId == null) return Result.error(401, "未登录");
        User user = userMapper.selectById(currentUserId);
        int streak = user != null && user.getTotalStudyDays() != null ? user.getTotalStudyDays() : 0;

        LocalDateTime todayStart = LocalDateTime.now().toLocalDate().atStartOfDay();
        com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<UserProgress> q = new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<>();
        q.eq("user_id", currentUserId).ge("last_review_time", todayStart);
        long studiedToday = userProgressMapper.selectCount(q);

        Map<String, Object> result = new java.util.LinkedHashMap<>();
        result.put("streak", streak);
        result.put("studiedToday", studiedToday);
        return Result.success(result);
    }
}
