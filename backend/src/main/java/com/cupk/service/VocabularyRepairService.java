package com.cupk.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.cupk.mapper.VocabularyMapper;
import com.cupk.pojo.Vocabulary;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 词汇修复服务 — 批量调用 AI 修复乱码词汇的释义/音标/例句/翻译
 */
@Service
public class VocabularyRepairService {

    private static final Logger log = LoggerFactory.getLogger(VocabularyRepairService.class);

    private static final int BATCH_SIZE = 20;
    private static final long DELAY_BETWEEN_API_MS = 500;

    @Autowired
    private VocabularyMapper vocabularyMapper;
    @Autowired
    private DeepSeekService deepSeekService;

    /**
     * 按语言批量修复乱码词汇
     * @return 修复统计 {total, fixed, failed}
     */
    public Map<String, Integer> repairByLanguage(String langCode, int limit) {
        AtomicInteger fixed = new AtomicInteger(0);
        AtomicInteger failed = new AtomicInteger(0);
        AtomicInteger total = new AtomicInteger(0);

        // 查询需要修复的词汇（definition 含 ? 或为空）
        QueryWrapper<Vocabulary> q = new QueryWrapper<>();
        q.eq("lang_code", langCode);
        q.and(w -> w.like("definition", "?%").or().isNull("definition").or().eq("definition", ""));
        q.orderByAsc("id");
        q.last("LIMIT " + Math.min(limit, 1000));
        List<Vocabulary> brokenList = vocabularyMapper.selectList(q);

        total.set(brokenList.size());
        log.info("开始修复 {} 词汇: lang={}, count={}", langCode, brokenList.size());

        for (int i = 0; i < brokenList.size(); i++) {
            Vocabulary v = brokenList.get(i);
            try {
                Map<String, String> aiResult = deepSeekService.repairVocabulary(
                    v.getWord(), v.getLangCode(), v.getPartOfSpeech());
                if (aiResult != null) {
                    applyRepair(v, aiResult);
                    vocabularyMapper.updateById(v);
                    fixed.incrementAndGet();
                    log.info("修复[{}/{}] {} OK", i + 1, brokenList.size(), v.getWord());
                } else {
                    failed.incrementAndGet();
                    log.warn("修复[{}/{}] {} FAILED", i + 1, brokenList.size(), v.getWord());
                }
            } catch (Exception e) {
                failed.incrementAndGet();
                log.error("修复异常[{}/{}] {}: {}", i + 1, brokenList.size(), v.getWord(), e.getMessage());
            }

            // API 调用限速
            if (i < brokenList.size() - 1) {
                try { Thread.sleep(DELAY_BETWEEN_API_MS); } catch (InterruptedException ignored) {}
            }
        }

        Map<String, Integer> stats = new java.util.LinkedHashMap<>();
        stats.put("total", total.get());
        stats.put("fixed", fixed.get());
        stats.put("failed", failed.get());
        log.info("修复完成: lang={}, total={}, fixed={}, failed={}", langCode, total.get(), fixed.get(), failed.get());
        return stats;
    }

    /** 将 AI 返回结果应用到词汇实体 */
    private void applyRepair(Vocabulary v, Map<String, String> ai) {
        // 只有数据被识别为乱码或为空时才覆盖
        String phonetic = ai.get("phonetic");
        if (phonetic != null && !phonetic.isEmpty() && isBroken(v.getPhonetic())) {
            v.setPhonetic(phonetic);
        }
        String definition = ai.get("definition");
        if (definition != null && !definition.isEmpty() && isBroken(v.getDefinition())) {
            v.setDefinition(definition);
        }
        String example = ai.get("example");
        if (example != null && !example.isEmpty() && isBroken(v.getExampleSentence())) {
            v.setExampleSentence(example);
        }
        String translation = ai.get("translation");
        if (translation != null && !translation.isEmpty() && isBroken(v.getExampleTranslation())) {
            v.setExampleTranslation(translation);
        }
    }

    /** 判断字段是否乱码或为空 */
    private boolean isBroken(String value) {
        return value == null || value.isEmpty() || value.contains("?");
    }
}
