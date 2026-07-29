package com.cupk.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.cupk.mapper.UserProgressMapper;
import com.cupk.pojo.UserProgress;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * GrammarService — 语法练习业务逻辑
 * 核心功能: 错误聚类 / 顽固薄弱点检测 / 规则卡片生成
 */
@Service
@RequiredArgsConstructor
public class GrammarService {

    private final UserProgressMapper userProgressMapper;

    private final ObjectMapper objectMapper = new ObjectMapper();

    /** 错误类型映射为中文规则卡片 */
    public static final Map<String, String> RULE_CARDS = Map.ofEntries(
        Map.entry("TENSE_ERROR", "动词时态错误：请确认时间状语对应的时态。一般现在时主语为第三人称单数时，动词需加 -s/-es。"),
        Map.entry("PREPOSITION_ERROR", "介词搭配错误：注意固定搭配，如 'interested in', 'good at', 'arrive at/in'。"),
        Map.entry("WORD_ORDER_ERROR", "语序错误：英语基本语序为 SVO（主谓宾），疑问句需倒装。"),
        Map.entry("ARTICLE_ERROR", "冠词错误：可数名词单数前需加 a/an，特指时用 the。"),
        Map.entry("PARTICLE", "助词错误：注意格助词与接续助词的区别，如日语では/には、韩语은/는 vs 을/를。"),
        Map.entry("CONJ", "活用/变位错误：确认动词、形容词在对应时态下的正确变形规则。"),
        Map.entry("KEIGO", "敬语错误：日语注意尊敬语、谦让语、礼貌语的区分；韩语注意语尾和主客体敬语。"),
        Map.entry("CASE", "格错误：德语注意名词的性、数、格与冠词/形容词词尾的一致变化。"),
        Map.entry("ADJ", "形容词错误：法语注意形容词与名词的性数配合，位置变化。"),
        Map.entry("NEG", "否定错误：注意否定形式与动词活用的组合规则。"),
        Map.entry("TRENNBAR", "可分动词错误：德语可分动词在陈述句中前缀需置于句末。"),
        Map.entry("UNKNOWN", "语法错误：请回顾相关语法规则后再尝试。")
    );

    /**
     * 获取顽固薄弱点标签
     * 同类型错误 ≥3 次 → 标记 stubborn
     */
    public List<String> getStubbornTags(Long userId) {
        QueryWrapper<UserProgress> q = new QueryWrapper<>();
        q.eq("user_id", userId).isNotNull("error_tags");
        List<UserProgress> list = userProgressMapper.selectList(q);

        Map<String, Integer> counts = new HashMap<>();
        for (UserProgress p : list) {
            List<String> tags = parseErrorTags(p.getErrorTags());
            for (String tag : tags) {
                if (!tag.isEmpty()) {
                    counts.merge(tag, 1, Integer::sum);
                }
            }
        }

        List<String> stubborn = new ArrayList<>();
        for (Map.Entry<String, Integer> e : counts.entrySet()) {
            if (e.getValue() >= 3) {
                stubborn.add(e.getKey());
            }
        }
        return stubborn;
    }

    /**
     * 生成语法规则卡片内容
     */
    public String getRuleCard(String errorType) {
        return RULE_CARDS.getOrDefault(errorType, RULE_CARDS.get("UNKNOWN"));
    }

    /**
     * 安全解析 error_tags JSON 字符串
     */
    private List<String> parseErrorTags(String raw) {
        if (raw == null || raw.isBlank()) {
            return Collections.emptyList();
        }
        String trimmed = raw.trim();
        // 尝试作为 JSON 数组解析
        if (trimmed.startsWith("[") && trimmed.endsWith("]")) {
            try {
                return objectMapper.readValue(trimmed, new TypeReference<List<String>>() {});
            } catch (Exception e) {
                // 回退到手动解析
            }
        }
        // 回退：按逗号分割并清理
        List<String> result = new ArrayList<>();
        for (String part : trimmed.split(",")) {
            String cleaned = part.replaceAll("[\\[\\]\"' ]", "").trim();
            if (!cleaned.isEmpty()) {
                result.add(cleaned);
            }
        }
        return result;
    }
}
