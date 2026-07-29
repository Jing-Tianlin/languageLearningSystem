package com.cupk.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.cupk.mapper.UserMapper;
import com.cupk.mapper.UserProgressMapper;
import com.cupk.mapper.InspectionLogMapper;
import com.cupk.pojo.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

/**
 * LearningDataService — 统一数据写入服务（SM-2 自适应间隔重复算法）
 *
 * 职责:
 *   - 每日练习结果写入 user_progress (SM-2 自适应间隔 + 掌握度)
 *   - 巡检结果处理
 *   - 用户统计更新（积分、学习天数）
 */
@Service
public class LearningDataService {

    /** SM-2 算法常量 */
    private static final double EF_MIN = 1.3;
    private static final double EF_INIT = 2.5;
    private static final double EF_MAX = 2.5;
    /** 响应质量 → 间隔乘数: q=0→无间隔, q=1→1天, q=2→1天, q=3→EF*间隔, q=4→EF*1.25*间隔, q=5→EF*1.5*间隔 */
    private static final double[] Q_MULTIPLIER = {0, 1.0, 1.0, 1.0, 1.25, 1.5};
    /** 最短间隔（小时） */
    private static final int MIN_INTERVAL_HOURS = 4;
    /** 初始间隔（小时） */
    private static final int INIT_INTERVAL_HOURS = 8;

    @Autowired
    private UserProgressMapper progressMapper;
    @Autowired
    private UserMapper userMapper;
    @Autowired
    private InspectionLogMapper inspectionLogMapper;

    /**
     * SM-2 自适应间隔重复算法核心
     *
     * @param responseQuality 响应质量 0-5:
     *        0=完全遗忘, 1=模糊记得, 2=勉强正确,
     *        3=正确但犹豫, 4=正确且流畅, 5=完美秒答
     */
    @Transactional
    public UserProgress recordPracticeAnswer(Long userId, Long vocabId, String langCode,
                                             boolean correct, int hesitationMs, String errorType) {
        QueryWrapper<UserProgress> q = new QueryWrapper<>();
        q.eq("user_id", userId).eq("vocab_id", vocabId);
        UserProgress p = progressMapper.selectOne(q);

        boolean isNew = (p == null);
        if (isNew) {
            p = new UserProgress();
            p.setUserId(userId);
            p.setVocabId(vocabId);
            p.setLangCode(langCode != null ? langCode : "en");
            p.setFamiliarity(0);
            p.setReviewCount(0);
            p.setMasteryLevel(0);
            p.setEaseFactor(EF_INIT);
            p.setStability(0);
            p.setConsecutiveCorrect(0);
        }

        // 基础更新
        p.setReviewCount((p.getReviewCount() != null ? p.getReviewCount() : 0) + 1);
        p.setLastReviewTime(LocalDateTime.now());
        p.setHesitationMs(hesitationMs);
        if (hesitationMs > 0) p.setLastHesitationAt(LocalDateTime.now());

        // 错误标签
        if (!correct && errorType != null && !errorType.isEmpty()) {
            p.setErrorTags(mergeTags(p.getErrorTags(), errorType));
        }

        // ===== SM-2 响应质量计算 =====
        int qScore = calcQualityScore(correct, hesitationMs);

        // ===== 连续正确/稳定性更新 =====
        int consecutive = (p.getConsecutiveCorrect() != null ? p.getConsecutiveCorrect() : 0);
        int stability = (p.getStability() != null ? p.getStability() : 0);
        if (correct) {
            consecutive++;
            stability = Math.min(20, stability + 1);
        } else {
            consecutive = 0;
            stability = Math.max(0, stability - 3);
        }
        p.setConsecutiveCorrect(consecutive);
        p.setStability(stability);

        // ===== SM-2 难度因子 (Ease Factor) 更新 =====
        double ef = (p.getEaseFactor() != null ? p.getEaseFactor() : EF_INIT);
        ef = calcNewEF(ef, qScore);
        p.setEaseFactor(Math.max(EF_MIN, Math.min(EF_MAX, ef)));

        // ===== Familiarity (基于稳定性和响应质量) =====
        int fam = calcFamiliarity(stability, consecutive, qScore, correct);
        p.setFamiliarity(Math.max(0, Math.min(100, fam)));

        // ===== Mastery Level (基于 familiarity + stability) =====
        p.setMasteryLevel(calcMastery(fam, stability, consecutive));

        // ===== SM-2 间隔计算 =====
        long intervalMillis = calcSM2Interval(p);
        p.setNextReviewTime(LocalDateTime.now().plusSeconds(intervalMillis / 1000));

        if (isNew) progressMapper.insert(p);
        else progressMapper.updateById(p);

        updateUserStats(userId);
        return p;
    }

    // ---- SM-2 子算法 ----

    /** 根据正确性和犹豫时间计算响应质量 (0-5) */
    private int calcQualityScore(boolean correct, int hesitationMs) {
        if (!correct) {
            return 0; // 答错 → 完全遗忘
        }
        if (hesitationMs <= 0) return 3; // 无犹豫数据默认中等
        if (hesitationMs < 1000) return 5;   // <1s 秒答
        if (hesitationMs < 2000) return 4;   // <2s 流畅
        if (hesitationMs < 4000) return 3;   // <4s 犹豫
        if (hesitationMs < 8000) return 2;   // <8s 勉强
        return 1;                             // ≥8s 模糊
    }

    /** SM-2 难度因子更新公式 */
    private double calcNewEF(double oldEF, int q) {
        // EF' = EF + (0.1 - (5-q) * (0.08 + (5-q) * 0.02))
        int diff = 5 - q;
        return oldEF + (0.1 - diff * (0.08 + diff * 0.02));
    }

    /** 综合 familiarity: 稳定性*4 + 连续正确*3 + 响应质量相关 */
    private int calcFamiliarity(int stability, int consecutive, int qScore, boolean correct) {
        int base = stability * 4;
        base += Math.min(consecutive, 10) * 3;
        if (correct) {
            base += qScore * 5;              // 正确: 响应越好加分越多
        } else {
            base = Math.max(0, base - 20);   // 错误: 大幅惩罚
        }
        return Math.min(100, base);
    }

    /** mastery 综合考虑熟练度和稳定性 */
    private int calcMastery(int fam, int stability, int consecutive) {
        if (fam >= 85 && stability >= 12 && consecutive >= 5) return 3;  // 已掌握
        if (fam >= 60 && stability >= 6) return 2;                       // 熟悉
        if (fam >= 30) return 1;                                          // 学习中
        return 0;                                                         // 新词
    }

    /** SM-2 间隔计算：间隔 = 上次间隔 × EF × 质量乘数 */
    private long calcSM2Interval(UserProgress p) {
        int stability = p.getStability() != null ? p.getStability() : 0;
        double ef = p.getEaseFactor() != null ? p.getEaseFactor() : EF_INIT;
        int consecutive = p.getConsecutiveCorrect() != null ? p.getConsecutiveCorrect() : 0;

        // 基础间隔随稳定性指数增长
        long baseHours;
        if (stability >= 15) baseHours = 720;      // 30天
        else if (stability >= 12) baseHours = 336; // 14天
        else if (stability >= 9)  baseHours = 168;  // 7天
        else if (stability >= 6)  baseHours = 72;   // 3天
        else if (stability >= 3)  baseHours = 24;   // 1天
        else                      baseHours = INIT_INTERVAL_HOURS;

        // EF 微调间隔
        long adjustedHours = (long) (baseHours * (ef / EF_INIT));
        // 连续正确加速扩展间隔
        if (consecutive >= 3) adjustedHours = (long) (adjustedHours * 1.3);

        return Math.max(MIN_INTERVAL_HOURS * 3600L, adjustedHours * 3600L);
    }

    // ---- 用户统计 ----

    /** 多维度积分: 掌握*10 + 稳定性*2 + 连续学习天*5 */
    void updateUserStats(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) return;

        QueryWrapper<UserProgress> totalQ = new QueryWrapper<>();
        totalQ.eq("user_id", userId);
        Long total = progressMapper.selectCount(totalQ);

        QueryWrapper<UserProgress> masteredQ = new QueryWrapper<>();
        masteredQ.eq("user_id", userId).eq("mastery_level", 3);
        Long mastered = progressMapper.selectCount(masteredQ);

        // 总稳定性加成
        QueryWrapper<UserProgress> stableQ = new QueryWrapper<>();
        stableQ.eq("user_id", userId).select("COALESCE(SUM(stability),0) as totalStability");
        var stableResult = progressMapper.selectMaps(stableQ);
        long totalStability = stableResult.isEmpty() ? 0 :
            Long.parseLong(stableResult.get(0).getOrDefault("totalStability", "0").toString());

        int points = (int)(mastered * 10L + totalStability * 2L
            + (user.getTotalStudyDays() != null ? user.getTotalStudyDays() : 0) * 5L);

        user.setTotalWordsLearned(total.intValue());
        user.setPoints(points);
        userMapper.updateById(user);
    }

    /** 登录时更新连续学习天数 */
    public void updateStudyStreak(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) return;

        LocalDateTime lastLogin = user.getUpdateTime();
        LocalDateTime now = LocalDateTime.now();
        int currentDays = user.getTotalStudyDays() != null ? user.getTotalStudyDays() : 0;

        if (lastLogin != null) {
            long days = ChronoUnit.DAYS.between(lastLogin.toLocalDate(), now.toLocalDate());
            if (days == 1) {
                user.setTotalStudyDays(currentDays + 1);
            } else if (days == 0) {
                // 同天多次登录不重复计算
            } else {
                // 断签重置
                user.setTotalStudyDays(1);
            }
        } else {
            user.setTotalStudyDays(1);
        }
        userMapper.updateById(user);
    }

    // ---- 工具方法 ----

    private String mergeTags(String existing, String newTag) {
        if (existing == null || existing.isEmpty() || "null".equals(existing)) {
            return "[\"" + newTag + "\"]";
        }
        if (existing.contains("\"" + newTag + "\"")) return existing;
        return existing.replace("]", ",\"" + newTag + "\"]");
    }

    /** 巡检结果（SM-2: 答错重置间隔） */
    public void recordInspection(Long userId, Long vocabId, boolean correct) {
        InspectionLog log = new InspectionLog();
        log.setUserId(userId);
        log.setVocabId(vocabId);
        log.setResult(correct ? 1 : 0);
        log.setInspectionTime(LocalDateTime.now());
        inspectionLogMapper.insert(log);

        if (!correct) {
            QueryWrapper<UserProgress> q = new QueryWrapper<>();
            q.eq("user_id", userId).eq("vocab_id", vocabId);
            UserProgress p = progressMapper.selectOne(q);
            if (p != null) {
                // SM-2 重置: 降低稳定性, 重置间隔
                int stability = Math.max(0, (p.getStability() != null ? p.getStability() : 0) - 2);
                p.setStability(stability);
                p.setConsecutiveCorrect(0);
                p.setMasteryLevel(Math.min(1, calcMastery(
                    p.getFamiliarity() != null ? p.getFamiliarity() : 0, stability, 0)));
                p.setNextReviewTime(LocalDateTime.now().plusHours(MIN_INTERVAL_HOURS));
                progressMapper.updateById(p);
            }
        }
    }

    /**
     * 获取用户综合掌握率，用于自适应选词比例
     * @return 0.0-1.0 的掌握率
     */
    public double getUserMasteryRate(Long userId) {
        QueryWrapper<UserProgress> totalQ = new QueryWrapper<>();
        totalQ.eq("user_id", userId);
        Long total = progressMapper.selectCount(totalQ);
        if (total == null || total == 0) return 0.0;

        QueryWrapper<UserProgress> masteredQ = new QueryWrapper<>();
        masteredQ.eq("user_id", userId).ge("mastery_level", 2);
        Long familiar = progressMapper.selectCount(masteredQ);
        return (familiar != null ? familiar : 0) * 1.0 / total;
    }
}
