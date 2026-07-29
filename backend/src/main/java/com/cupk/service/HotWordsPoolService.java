package com.cupk.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.cupk.mapper.UserProgressMapper;
import com.cupk.mapper.VocabularyMapper;
import com.cupk.pojo.UserProgress;
import com.cupk.pojo.Vocabulary;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * HotWordsPoolService — 用户热点词库服务 (联动引擎核心)
 *
 * 提供:
 *   1. getActiveVocab(userId, limit) → 最近活跃的热点词(正在学/临界遗忘)
 *   2. getWeakGrammarRules(userId) → 用户语法薄弱维度
 *   3. getGrammarSlotFill(userId) → 把热点词挂接到语法模板中
 *   4. isVerbShortage(userId) → 检测某词性是否短缺
 */
@Service
public class HotWordsPoolService {

    @Autowired
    private UserProgressMapper progressMapper;
    @Autowired
    private VocabularyMapper vocabularyMapper;

    /**
     * 获取用户热点词库
     * 规则: status=learning 且在3天内复习过, 或 status=mastered 但已到/超过下次复习时间
     */
    public List<Map<String, Object>> getActiveVocab(Long userId, int limit) {
        List<Map<String, Object>> result = new ArrayList<>();
        Set<Long> seen = new HashSet<>();

        // 1. 正在学习 + 3天内复习过
        QueryWrapper<UserProgress> q1 = new QueryWrapper<>();
        q1.eq("user_id", userId)
          .ge("mastery_level", 1)    // 至少学过
          .le("mastery_level", 2)    // 未完全掌握
          .ge("last_review_time", LocalDateTime.now().minusDays(3));
        List<UserProgress> learning = progressMapper.selectList(q1);
        for (UserProgress p : learning) {
            if (seen.size() >= limit) break;
            if (seen.add(p.getVocabId())) {
                result.add(progressToMap(p));
            }
        }

        // 2. 已掌握但临界遗忘 (next_review_time < now)
        QueryWrapper<UserProgress> q2 = new QueryWrapper<>();
        q2.eq("user_id", userId)
          .ge("mastery_level", 3)
          .lt("next_review_time", LocalDateTime.now())
          .orderByAsc("next_review_time");
        List<UserProgress> forgetting = progressMapper.selectList(q2);
        for (UserProgress p : forgetting) {
            if (seen.size() >= limit) break;
            if (seen.add(p.getVocabId())) {
                result.add(progressToMap(p));
            }
        }

        return result;
    }

    private Map<String, Object> progressToMap(UserProgress p) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("vocabId", p.getVocabId());
        m.put("langCode", p.getLangCode());
        m.put("masteryLevel", p.getMasteryLevel());
        m.put("familiarity", p.getFamiliarity());
        m.put("reviewCount", p.getReviewCount());
        // 查词汇表获得 word + part_of_speech
        if (p.getVocabId() != null) {
            Vocabulary v = vocabularyMapper.selectById(p.getVocabId());
            if (v != null) {
                m.put("word", v.getWord());
                m.put("partOfSpeech", v.getPartOfSpeech());
                m.put("definition", v.getDefinition());
            }
        }
        return m;
    }

    /**
     * 获取用户语法薄弱维度 (错误最多的前3个规则)
     */
    public List<String> getWeakGrammarRules(Long userId) {
        QueryWrapper<UserProgress> q = new QueryWrapper<>();
        q.eq("user_id", userId).isNotNull("error_tags").ne("error_tags", "");
        List<UserProgress> list = progressMapper.selectList(q);

        Map<String, Integer> errCount = new HashMap<>();
        for (UserProgress p : list) {
            String tags = p.getErrorTags();
            if (tags == null || tags.isEmpty()) continue;
            for (String t : tags.replaceAll("[\\[\\]\"]", "").split(",")) {
                String tag = t.trim();
                if (!tag.isEmpty()) errCount.merge(tag, 1, Integer::sum);
            }
        }
        return errCount.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(3)
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());
    }

    /**
     * 检测用户词性短缺
     * 如果热点词中某类词性少于 total 的 10%, 返回短缺的词性列表
     */
    public List<String> detectPosShortage(Long userId) {
        List<Map<String, Object>> active = getActiveVocab(userId, 50);
        Map<String, Long> posCount = new HashMap<>();
        for (Map<String, Object> w : active) {
            String pos = (String) w.getOrDefault("partOfSpeech", "unknown");
            posCount.merge(pos, 1L, Long::sum);
        }
        long total = active.size();
        List<String> shortages = new ArrayList<>();
        for (String expectedPos : List.of("verb", "noun", "adjective", "adverb")) {
            long count = posCount.getOrDefault(expectedPos, 0L);
            if (total > 0 && (double) count / total < 0.10) {
                shortages.add(expectedPos);
            }
        }
        return shortages;
    }

    /**
     * 语法模板挂接: 把热点词填入槽位
     * 模板: "[SUBJECT] ___ [VERB] to [PLACE] yesterday."
     * 返回 { sentence: "He bought a phone yesterday.", answer: "bought", rule: "PAST_TENSE" }
     */
    public Map<String, Object> generateGrammarWithHotWords(Long userId, String template, String answerSlot) {
        List<Map<String, Object>> hotWords = getActiveVocab(userId, 30);
        if (hotWords.isEmpty()) {
            return Map.of("sentence", "He ___ (go) to school every day.", "answer", "goes", "rule", "TENSE");
        }

        // 从热点词中找对应的词性
        List<Map<String, Object>> verbs = hotWords.stream()
                .filter(w -> "verb".equals(w.get("partOfSpeech")))
                .collect(Collectors.toList());
        List<Map<String, Object>> nouns = hotWords.stream()
                .filter(w -> "noun".equals(w.get("partOfSpeech")))
                .collect(Collectors.toList());

        // 降级: 无对应词性时使用系统词库
        String verb = verbs.isEmpty() ? "go" : (String) verbs.get(new Random().nextInt(verbs.size())).get("word");
        String noun = nouns.isEmpty() ? "school" : (String) nouns.get(new Random().nextInt(nouns.size())).get("word");

        // 生成句子 (简单化: 直接替换)
        String sentence = template
                .replace("[SUBJECT]", "He")
                .replace("[VERB]", "___")
                .replace("[PLACE]", noun);
        // answerSlot 如 "went"
        return Map.of("sentence", sentence.replace("___", answerSlot), "answer", answerSlot, "rule", "PAST_TENSE",
                "usedWord", verb, "usedNoun", noun);
    }
}
