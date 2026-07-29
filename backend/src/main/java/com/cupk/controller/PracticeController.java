package com.cupk.controller;

import com.cupk.common.Result;
import com.cupk.mapper.UserMapper;
import com.cupk.mapper.UserProgressMapper;
import com.cupk.pojo.User;
import com.cupk.pojo.UserProgress;
import com.cupk.service.LearningDataService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
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
public class PracticeController {

    private static final Logger log = LoggerFactory.getLogger(PracticeController.class);

    @Autowired
    private LearningDataService dataService;
    @Autowired
    private UserMapper userMapper;
    @Autowired
    private UserProgressMapper userProgressMapper;

    /**
     * 记录练习答案 (每日练习 / 语法练习通用)
     * POST /practice/record
     * Body: { userId:1, vocabId:5, langCode:"en", correct:true, hesitationMs:1200, errorType:"TENSE" }
     */
    @PostMapping("/record")
    public Result<Map<String, Object>> recordAnswer(@RequestBody Map<String, Object> body) {
        Long userId = Long.valueOf(body.get("userId").toString());
        Long vocabId = body.containsKey("vocabId") ? Long.valueOf(body.get("vocabId").toString()) : 0L;
        String langCode = body.containsKey("langCode") ? body.get("langCode").toString() : "en";
        Boolean correct = (Boolean) body.get("correct");
        Integer hesitationMs = body.containsKey("hesitationMs") ? (Integer) body.get("hesitationMs") : 0;
        String errorType = body.get("errorType") != null ? body.get("errorType").toString() : null;

        var progress = dataService.recordPracticeAnswer(
                userId, vocabId, langCode,
                correct != null && correct,
                hesitationMs != null ? hesitationMs : 0,
                errorType
        );

        Map<String, Object> result = new java.util.LinkedHashMap<>();
        result.put("masteryLevel", progress.getMasteryLevel());
        result.put("familiarity", progress.getFamiliarity());
        result.put("reviewCount", progress.getReviewCount());
        result.put("nextReview", progress.getNextReviewTime().toString());
        return Result.success(result);
    }

    /** 登录时调用, 更新打卡天数 */
    @PostMapping("/checkin")
    public Result<String> checkin(@RequestBody Map<String, Object> body) {
        Long userId = Long.valueOf(body.get("userId").toString());
        dataService.updateStudyStreak(userId);
        return Result.success("打卡成功");
    }

    /** 今日练习统计 */
    @GetMapping("/today-stats")
    public Result<Map<String, Object>> todayStats(@RequestParam Long userId) {
        User user = userMapper.selectById(userId);
        int streak = user != null && user.getTotalStudyDays() != null ? user.getTotalStudyDays() : 0;

        LocalDateTime todayStart = LocalDateTime.now().toLocalDate().atStartOfDay();
        com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<UserProgress> q = new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<>();
        q.eq("user_id", userId).ge("last_review_time", todayStart);
        long studiedToday = userProgressMapper.selectCount(q);

        Map<String, Object> result = new java.util.LinkedHashMap<>();
        result.put("streak", streak);
        result.put("studiedToday", studiedToday);
        return Result.success(result);
    }
}
