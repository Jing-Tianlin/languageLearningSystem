package com.cupk.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.cupk.mapper.*;
import com.cupk.pojo.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;

/**
 * InspectionService — 遗忘巡检机制 (提议4)
 *
 * 核心逻辑：
 * - 当用户学习了大量新内容后，已掌握的旧知识可能被干扰
 * - 系统定时触发"轻量级巡检"：从 mastery_level>=2 的词汇中随机挑3个快速测试
 * - 答错即降级，重回复习队列
 */
@Service
public class InspectionService {

    @Autowired
    private UserProgressMapper userProgressMapper;
    @Autowired
    private VocabularyMapper vocabularyMapper;
    @Autowired
    private InspectionLogMapper inspectionLogMapper;

    /**
     * 检查用户是否需要巡检
     * 条件：最近一次巡检 > 24小时 或 学习了 >= 5 个新词
     */
    public boolean needsInspection(Long userId) {
        // 检查最近24小时内是否已有巡检记录
        QueryWrapper<InspectionLog> logQuery = new QueryWrapper<>();
        logQuery.eq("user_id", userId)
                .ge("inspection_time", LocalDateTime.now().minusHours(24));
        Long recentCount = inspectionLogMapper.selectCount(logQuery);
        if (recentCount > 0) return false;

        // 检查24小时内新学习的词汇数(review_count=1 且 createTime 在24h内)
        QueryWrapper<UserProgress> newQuery = new QueryWrapper<>();
        newQuery.eq("user_id", userId)
                .eq("review_count", 1)
                .ge("create_time", LocalDateTime.now().minusHours(24));
        Long newWords = userProgressMapper.selectCount(newQuery);
        return newWords >= 3; // >=3 个新词时触发
    }

    /**
     * 生成巡检题目
     * 从"已掌握/熟悉"词汇中随机挑3个
     * @return List<Map> 每项包含 vocabId, word, definition, phonetic
     */
    public List<Map<String, Object>> generateInspectionQuestions(Long userId, String langCode) {
        QueryWrapper<UserProgress> query = new QueryWrapper<>();
        query.eq("user_id", userId)
                .ge("mastery_level", 2); // 熟悉(2)或已掌握(3)
        if (langCode != null && !langCode.isEmpty()) {
            query.eq("lang_code", langCode);
        }

        List<UserProgress> progressList = userProgressMapper.selectList(query);
        if (progressList.size() < 3) return Collections.emptyList();

        // 随机选取3个
        Collections.shuffle(progressList);
        List<Map<String, Object>> questions = new ArrayList<>();
        for (int i = 0; i < Math.min(3, progressList.size()); i++) {
            UserProgress p = progressList.get(i);
            Vocabulary v = vocabularyMapper.selectById(p.getVocabId());
            if (v != null) {
                Map<String, Object> q = new HashMap<>();
                q.put("vocabId", v.getId());
                q.put("word", v.getWord());
                q.put("definition", v.getDefinition());
                q.put("phonetic", v.getPhonetic());
                questions.add(q);
            }
        }
        return questions;
    }

    /**
     * 提交巡检结果
     * 答错 → mastery_level 降为 1(学习中), review_count+1
     * 答对 → 保持
     */
    public void submitInspectionResult(Long userId, Long vocabId, boolean correct) {
        // 记录巡检日志
        InspectionLog log = new InspectionLog();
        log.setUserId(userId);
        log.setVocabId(vocabId);
        log.setResult(correct ? 1 : 0);
        log.setInspectionTime(LocalDateTime.now());
        inspectionLogMapper.insert(log);

        // 答错 → 降级
        if (!correct) {
            QueryWrapper<UserProgress> query = new QueryWrapper<>();
            query.eq("user_id", userId).eq("vocab_id", vocabId);
            UserProgress progress = userProgressMapper.selectOne(query);
            if (progress != null) {
                progress.setMasteryLevel(1); // 降为学习中
                progress.setReviewCount((progress.getReviewCount() == null ? 0 : progress.getReviewCount()) + 1);
                progress.setNextReviewTime(LocalDateTime.now().plusHours(4)); // 4小时后重复习
                userProgressMapper.updateById(progress);
            }
        }
    }
}
