package com.cupk.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cupk.common.Result;
import com.cupk.mapper.UserProgressMapper;
import com.cupk.mapper.VocabularyMapper;
import com.cupk.pojo.UserProgress;
import com.cupk.pojo.Vocabulary;
import com.cupk.util.AuthUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/vocabulary")
@RequiredArgsConstructor
public class VocabularyController {
    private static final Logger log = LoggerFactory.getLogger(VocabularyController.class);
    private final VocabularyMapper vocabularyMapper;
    private final UserProgressMapper progressMapper;
    private final com.cupk.service.DeepSeekService deepSeekService;
    private final com.cupk.service.LearningDataService learningDataService;

    @GetMapping("/vocabularies")
    public Result<Page<Vocabulary>> selectPages(@RequestParam(defaultValue = "") String word,
                                                @RequestParam(defaultValue = "") String langCode,
                                                @RequestParam(defaultValue = "") String partOfSpeech,
                                                @RequestParam(defaultValue = "") String level,
                                                @RequestParam(defaultValue = "") Long lessonId,
                                                @RequestParam(defaultValue = "1") Integer pageNo,
                                                @RequestParam(defaultValue = "12") Integer pageSize) {
        Page<Vocabulary> page = new Page<>(pageNo, pageSize);
        QueryWrapper<Vocabulary> queryWrapper = new QueryWrapper<>();
        if (!word.isEmpty()) {
            queryWrapper.like("word", word);
        }
        if (!langCode.isEmpty()) {
            queryWrapper.eq("lang_code", langCode);
        }
        if (!partOfSpeech.isEmpty()) {
            queryWrapper.eq("part_of_speech", partOfSpeech);
        }
        if (!level.isEmpty()) {
            queryWrapper.eq("level", level);
        }
        if (lessonId != null) {
            queryWrapper.eq("lesson_id", lessonId);
        }
        queryWrapper.orderByAsc("id");
        vocabularyMapper.selectPage(page, queryWrapper);
        return Result.success(page);
    }

    @GetMapping("/vocabularies/{id}")
    public Result<Vocabulary> selectById(@PathVariable Long id) {
        Vocabulary vocab = vocabularyMapper.selectById(id);
        if (vocab == null) {
            return Result.error(404, "单词不存在");
        }
        return Result.success(vocab);
    }

    @PostMapping("/vocabularies")
    public Result<Void> insert(@RequestBody Vocabulary vocabulary) {
        vocabulary.setId(null);
        int rows = vocabularyMapper.insert(vocabulary);
        if (rows > 0) {
            return Result.success("新增成功");
        }
        return Result.error("新增失败");
    }

    /**
     * 批量导入词汇（CSV/逐行文本解析后的结构化数据）
     * 去重规则：同语言下单词已存在（忽略大小写）则跳过，不重复导入
     * POST /vocabulary/vocabularies/batch
     */
    @PostMapping("/vocabularies/batch")
    public Result<Map<String, Object>> batchInsert(@RequestBody List<Vocabulary> list) {
        if (list == null || list.isEmpty()) {
            return Result.error(400, "导入数据为空");
        }
        int added = 0;
        int skipped = 0;
        List<String> skippedWords = new ArrayList<>();
        for (Vocabulary v : list) {
            String word = v.getWord() == null ? "" : v.getWord().trim();
            if (word.isEmpty()) {
                skipped++;
                continue;
            }
            String langCode = v.getLangCode() == null || v.getLangCode().trim().isEmpty() ? "en" : v.getLangCode().trim();
            // 大小写不敏感去重：同语言下已存在相同单词则跳过
            Long exists = vocabularyMapper.selectCount(new QueryWrapper<Vocabulary>()
                    .eq("lang_code", langCode)
                    .apply("LOWER(word) = LOWER({0})", word));
            if (exists != null && exists > 0) {
                skipped++;
                skippedWords.add(word);
                continue;
            }
            v.setId(null);
            v.setWord(word);
            v.setLangCode(langCode);
            try {
                vocabularyMapper.insert(v);
                added++;
            } catch (Exception e) {
                log.warn("批量导入单词失败: {} - {}", word, e.getMessage());
                skipped++;
            }
        }
        Map<String, Object> result = new HashMap<>();
        result.put("added", added);
        result.put("skipped", skipped);
        result.put("skippedWords", skippedWords);
        return Result.success(result);
    }

    @PutMapping("/vocabularies")
    public Result<Void> update(@RequestBody Vocabulary vocabulary) {
        int rows = vocabularyMapper.updateById(vocabulary);
        if (rows > 0) {
            return Result.success("修改成功");
        }
        return Result.error("修改失败");
    }

    @DeleteMapping("/vocabularies/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        int rows = vocabularyMapper.deleteById(id);
        if (rows > 0) {
            return Result.success("删除成功");
        }
        return Result.error("删除失败");
    }

    /**
     * 智能选词接口（商业化 SRS：按到期时间优先出队）
     * GET /vocabulary/smart-select?userId=1&langCode=en&count=20&mode=mix
     *
     * mode 可选值:
     * - mix: 混合模式（优先今日到期 → 新词 → 薄弱词 → 已掌握巩固）
     * - review: 只复习过期/今日到期单词
     * - new: 只学习新词
     * - weak: 只学习薄弱词
     */
    @GetMapping("/smart-select")
    public Result<List<Vocabulary>> smartSelect(
            @RequestParam(required = false) Long userId,
            @RequestParam(defaultValue = "en") String langCode,
            @RequestParam(defaultValue = "20") Integer count,
            @RequestParam(defaultValue = "mix") String mode) {

        // 用户相关进度一律取 token 中的身份
        Long currentUserId = AuthUtil.getCurrentUserId();

        QueryWrapper<Vocabulary> vocabQuery = new QueryWrapper<>();
        vocabQuery.eq("lang_code", langCode);
        vocabQuery.isNotNull("word");
        vocabQuery.isNotNull("definition");
        List<Vocabulary> allVocabs = vocabularyMapper.selectList(vocabQuery);

        if (allVocabs.isEmpty()) {
            return Result.success(new ArrayList<>());
        }

        Map<Long, UserProgress> progressMap = new HashMap<>();
        if (currentUserId != null) {
            QueryWrapper<UserProgress> progressQuery = new QueryWrapper<>();
            progressQuery.eq("user_id", currentUserId);
            progressQuery.eq("lang_code", langCode);
            List<UserProgress> progresses = progressMapper.selectList(progressQuery);
            for (UserProgress p : progresses) {
                progressMap.put(p.getVocabId(), p);
            }
        }

        LocalDateTime now = LocalDateTime.now();

        List<VocabWithPriority> expiredList = new ArrayList<>();
        List<VocabWithPriority> newList = new ArrayList<>();
        List<VocabWithPriority> weakList = new ArrayList<>();
        List<VocabWithPriority> masteredList = new ArrayList<>();

        for (Vocabulary v : allVocabs) {
            UserProgress p = progressMap.get(v.getId());
            if (p == null) {
                newList.add(new VocabWithPriority(v, Integer.MAX_VALUE, 0));
            } else {
                Integer mastery = p.getMasteryLevel() != null ? p.getMasteryLevel() : 0;
                Integer fam = p.getFamiliarity() != null ? p.getFamiliarity() : 0;
                LocalDateTime nextReview = p.getNextReviewTime();

                if (nextReview != null && nextReview.isBefore(now)) {
                    // 越早到期优先级越高（秒数差越小越靠前）
                    long overdueSeconds = java.time.Duration.between(nextReview, now).getSeconds();
                    expiredList.add(new VocabWithPriority(v, (int) Math.min(overdueSeconds / 60, Integer.MAX_VALUE), mastery));
                } else if (mastery <= 1 || fam < 30) {
                    weakList.add(new VocabWithPriority(v, Integer.MAX_VALUE / 2, mastery));
                } else if (mastery >= 3 && fam >= 80) {
                    masteredList.add(new VocabWithPriority(v, Integer.MAX_VALUE, mastery));
                }
            }
        }

        // SRS 优先级：到期时间升序（越过期越先）
        expiredList.sort(Comparator.comparingInt(a -> a.priority));
        Collections.shuffle(newList);
        // 薄弱词按掌握度升序（越薄弱越先）
        weakList.sort(Comparator.comparingInt(a -> a.mastery));
        Collections.shuffle(masteredList);

        List<Vocabulary> result = new ArrayList<>();

        switch (mode) {
            case "review":
                result.addAll(limitVocab(expiredList, count));
                break;
            case "new":
                result.addAll(limitVocab(newList, count));
                break;
            case "weak":
                result.addAll(limitVocab(weakList, count));
                if (result.size() < count) {
                    result.addAll(limitVocab(expiredList, count - result.size()));
                }
                break;
            case "mix":
            default:
                // 商业化 SRS 队列：今日到期优先，其次新词，再次薄弱词，最后已掌握巩固
                int expiredCount = Math.min(expiredList.size(), (int) Math.round(count * 0.55));
                int newCount = Math.min(newList.size(), (int) Math.round(count * 0.30));
                int weakCount = Math.min(weakList.size(), count - expiredCount - newCount);
                int masteredCount = count - expiredCount - newCount - weakCount;

                result.addAll(limitVocab(expiredList, expiredCount));
                result.addAll(limitVocab(newList, newCount));
                result.addAll(limitVocab(weakList, weakCount));
                if (masteredCount > 0) {
                    result.addAll(limitVocab(masteredList, masteredCount));
                }
                break;
        }

        if (result.size() > count) {
            result = result.subList(0, count);
        }

        return Result.success(result);
    }

    /** 带 SRS 优先级的词汇包装 */
    private static class VocabWithPriority {
        final Vocabulary vocab;
        final int priority;
        final int mastery;
        VocabWithPriority(Vocabulary vocab, int priority, int mastery) {
            this.vocab = vocab;
            this.priority = priority;
            this.mastery = mastery;
        }
    }

    private List<Vocabulary> limitVocab(List<VocabWithPriority> list, int limit) {
        if (list == null || list.isEmpty() || limit <= 0) return new ArrayList<>();
        return list.stream().limit(limit).map(vp -> vp.vocab).collect(Collectors.toList());
    }

    /**
     * 获取选择题选项（AI 驱动，数据库兜底）
     * GET /vocabulary/quiz-options?vocabId=1&langCode=en&count=3&useAI=true
     * 
     * 返回结构:
     * {
     *   vocabId: 1,
     *   word: "convenience",
     *   correct: { definition, partOfSpeech },
     *   options: [{ definition, isCorrect }, ...]
     * }
     */
    @GetMapping("/quiz-options")
    public Result<Map<String, Object>> getQuizOptions(
            @RequestParam Long vocabId,
            @RequestParam(defaultValue = "en") String langCode,
            @RequestParam(defaultValue = "3") Integer count,
            @RequestParam(defaultValue = "true") Boolean useAI) {

        // count 上限保护：防止恶意传大数值放大 AI 成本
        if (count == null || count < 1) {
            count = 3;
        } else if (count > 6) {
            count = 6;
        }

        Vocabulary correctVocab = vocabularyMapper.selectById(vocabId);
        if (correctVocab == null) {
            return Result.error(404, "单词不存在");
        }

        List<String> distractors;
        boolean aiGenerated = false;

        if (Boolean.TRUE.equals(useAI)) {
            Map<String, Object> aiResult = deepSeekService.generateQuizOptions(
                correctVocab.getWord(),
                correctVocab.getDefinition(),
                correctVocab.getPartOfSpeech(),
                langCode,
                count
            );
            @SuppressWarnings("unchecked")
            List<String> aiDistractors = (List<String>) aiResult.get("distractors");
            distractors = aiDistractors;
            aiGenerated = (Boolean) aiResult.get("aiGenerated");
        } else {
            distractors = Collections.emptyList();
        }

        if (distractors == null || distractors.size() < count) {
            distractors = getDbDistractors(correctVocab, langCode, count);
            aiGenerated = false;
        }

        List<Map<String, Object>> options = new ArrayList<>();
        options.add(Map.of("definition", correctVocab.getDefinition(), "isCorrect", true));
        for (String def : distractors) {
            options.add(Map.of("definition", def, "isCorrect", false));
        }
        
        Collections.shuffle(options);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("vocabId", vocabId);
        result.put("word", correctVocab.getWord());
        result.put("correct", Map.of(
            "definition", correctVocab.getDefinition(),
            "partOfSpeech", correctVocab.getPartOfSpeech()
        ));
        result.put("options", options);
        result.put("aiGenerated", aiGenerated);

        return Result.success(result);
    }

    /**
     * 批量获取选择题选项（用于并行预加载）
     * POST /vocabulary/quiz-options/batch
     * Body: { vocabIds: [1, 2, 3], langCode: "en", count: 3 }
     */
    @PostMapping("/quiz-options/batch")
    public Result<List<Map<String, Object>>> getQuizOptionsBatch(
            @RequestBody Map<String, Object> body) {
        
        @SuppressWarnings("unchecked")
        List<Long> vocabIds = (List<Long>) body.get("vocabIds");
        String langCode = (String) body.getOrDefault("langCode", "en");
        Object countRaw = body.getOrDefault("count", 3);
        Integer count = countRaw instanceof Number ? ((Number) countRaw).intValue() : 3;
        Object useAIRaw = body.getOrDefault("useAI", true);
        Boolean useAI = useAIRaw instanceof Boolean ? (Boolean) useAIRaw : true;

        if (vocabIds == null || vocabIds.isEmpty()) {
            return Result.error(400, "vocabIds 不能为空");
        }
        // 批量上限保护：防止单次请求触发海量 AI 调用
        if (vocabIds.size() > 50) {
            return Result.error(400, "单次最多处理 50 个单词");
        }
        if (count == null || count < 1) {
            count = 3;
        } else if (count > 6) {
            count = 6;
        }

        List<Map<String, Object>> results = new ArrayList<>();
        
        for (Long vocabId : vocabIds) {
            try {
                Result<Map<String, Object>> singleResult = getQuizOptions(vocabId, langCode, count, useAI);
                if (singleResult.getCode() == 200) {
                    results.add(singleResult.getData());
                }
            } catch (Exception e) {
                log.warn("单词处理失败", e);
            }
        }

        return Result.success(results);
    }

    private List<String> getDbDistractors(Vocabulary correctVocab, String langCode, int count) {
        String correctPos = correctVocab.getPartOfSpeech();
        String correctDef = correctVocab.getDefinition();

        QueryWrapper<Vocabulary> query = new QueryWrapper<>();
        query.eq("lang_code", langCode);
        query.isNotNull("word");
        query.isNotNull("definition");
        query.ne("id", correctVocab.getId());
        if (correctPos != null && !correctPos.isEmpty()) {
            query.eq("part_of_speech", correctPos);
        }
        List<Vocabulary> samePosWords = vocabularyMapper.selectList(query);

        List<Vocabulary> otherPosWords = new ArrayList<>();
        if (samePosWords.size() < count) {
            QueryWrapper<Vocabulary> otherQuery = new QueryWrapper<>();
            otherQuery.eq("lang_code", langCode);
            otherQuery.isNotNull("word");
            otherQuery.isNotNull("definition");
            otherQuery.ne("id", correctVocab.getId());
            if (correctPos != null && !correctPos.isEmpty()) {
                otherQuery.ne("part_of_speech", correctPos);
            }
            otherPosWords = vocabularyMapper.selectList(otherQuery);
        }

        List<Vocabulary> allCandidates = new ArrayList<>();
        allCandidates.addAll(samePosWords);
        allCandidates.addAll(otherPosWords);

        Collections.shuffle(allCandidates);

        List<String> distractors = new ArrayList<>();
        Set<String> defSet = new HashSet<>();
        defSet.add(correctDef);

        for (Vocabulary v : allCandidates) {
            String def = v.getDefinition();
            if (def != null && !def.isEmpty() && !defSet.contains(def)) {
                distractors.add(def);
                defSet.add(def);
                if (distractors.size() >= count) break;
            }
        }

        return distractors;
    }

    private List<Vocabulary> shuffleAndLimit(List<Vocabulary> list, int limit) {
        if (list.isEmpty()) {
            return new ArrayList<>();
        }
        List<Vocabulary> shuffled = new ArrayList<>(list);
        Collections.shuffle(shuffled);
        return shuffled.size() > limit ? shuffled.subList(0, limit) : shuffled;
    }

    /**
     * 获取词汇统计信息
     * GET /vocabulary/stats?langCode=en&userId=1
     */
    @GetMapping("/stats")
    public Result<Map<String, Object>> getStats(
            @RequestParam(defaultValue = "en") String langCode,
            @RequestParam(required = false) Long userId) {

        // 学习进度统计取 token 身份
        Long currentUserId = AuthUtil.getCurrentUserId();

        QueryWrapper<Vocabulary> vocabQuery = new QueryWrapper<>();
        vocabQuery.eq("lang_code", langCode);
        long totalVocab = vocabularyMapper.selectCount(vocabQuery);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("totalVocab", totalVocab);

        if (currentUserId != null) {
            QueryWrapper<UserProgress> progressQuery = new QueryWrapper<>();
            progressQuery.eq("user_id", currentUserId);
            progressQuery.eq("lang_code", langCode);

            long studiedCount = progressMapper.selectCount(progressQuery);
            result.put("studiedCount", studiedCount);

            progressQuery.eq("mastery_level", 3);
            long masteredCount = progressMapper.selectCount(progressQuery);
            result.put("masteredCount", masteredCount);

            result.put("notStudiedCount", totalVocab - studiedCount);
        }

        return Result.success(result);
    }

    /**
     * AI 生成例句并保存
     * POST /vocabulary/generate-example?vocabId=1
     */
    @PostMapping("/generate-example")
    public Result<Map<String, Object>> generateExample(@RequestParam Long vocabId) {
        Vocabulary vocab = vocabularyMapper.selectById(vocabId);
        if (vocab == null) {
            return Result.error(404, "单词不存在");
        }

        try {
            Map<String, Object> aiResult = deepSeekService.generateExampleSentences(
                vocab.getWord(), vocab.getLangCode(), 1
            );

            @SuppressWarnings("unchecked")
            List<Map<String, String>> sentences = (List<Map<String, String>>) aiResult.get("sentences");
            if (sentences != null && !sentences.isEmpty()) {
                Map<String, String> first = sentences.get(0);
                String sentence = first.get("sentence");
                String translation = first.get("translation");

                if (sentence != null && !sentence.isEmpty()) {
                    vocab.setExampleSentence(sentence);
                    vocab.setExampleTranslation(translation);
                    vocabularyMapper.updateById(vocab);
                }
            }

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("vocabId", vocabId);
            result.put("exampleSentence", vocab.getExampleSentence());
            result.put("exampleTranslation", vocab.getExampleTranslation());
            return Result.success(result);
        } catch (Exception e) {
            return Result.error("生成例句失败：" + e.getMessage());
        }
    }

    /**
     * AI 批量生成词汇并导入数据库
     * POST /vocabulary/generate-batch
     * Body: { langCode: "en", level: "CET4", count: 50, category: "商务" }
     */
    @PostMapping("/generate-batch")
    public Result<Map<String, Object>> generateVocabularyBatch(@RequestBody Map<String, Object> body) {
        // 批量 AI 生成并写入库属于内容管理操作，仅管理员可用
        if (!AuthUtil.hasRole("ROLE_ADMIN")) {
            return Result.error(403, "无权限：AI 批量生成词汇仅管理员可用");
        }

        String langCode = String.valueOf(body.getOrDefault("langCode", "en"));
        String level = String.valueOf(body.getOrDefault("level", ""));
        Object countRaw = body.getOrDefault("count", 20);
        Integer count = countRaw instanceof Number ? ((Number) countRaw).intValue() : 20;
        String category = String.valueOf(body.getOrDefault("category", ""));

        if (level.isEmpty()) {
            return Result.error(400, "等级不能为空");
        }
        if (level.length() > 50) {
            return Result.error(400, "等级描述过长（最多 50 字符）");
        }
        if (category.length() > 50) {
            return Result.error(400, "类别描述过长（最多 50 字符）");
        }
        if (count == null || count <= 0 || count > 200) {
            count = 20;
        }

        try {
            List<Map<String, Object>> generatedWords = deepSeekService.generateVocabularyBatch(
                langCode, level, count, category
            );

            int inserted = 0;
            int skipped = 0;
            List<String> failedWords = new ArrayList<>();

            for (Map<String, Object> wordData : generatedWords) {
                String word = (String) wordData.get("word");
                if (word == null || word.isEmpty()) {
                    skipped++;
                    continue;
                }

                QueryWrapper<Vocabulary> queryWrapper = new QueryWrapper<>();
                queryWrapper.eq("lang_code", langCode);
                queryWrapper.eq("word", word);
                Long existing = vocabularyMapper.selectCount(queryWrapper);
                if (existing != null && existing > 0) {
                    skipped++;
                    continue;
                }

                try {
                    Vocabulary vocab = new Vocabulary();
                    vocab.setLangCode(langCode);
                    vocab.setWord(word);
                    vocab.setPhonetic((String) wordData.get("phonetic"));
                    vocab.setDefinition((String) wordData.get("definition"));
                    vocab.setPartOfSpeech((String) wordData.get("partOfSpeech"));
                    vocab.setLevel(level);
                    vocab.setExampleSentence((String) wordData.get("exampleSentence"));
                    vocab.setExampleTranslation((String) wordData.get("exampleTranslation"));
                    int rows = vocabularyMapper.insert(vocab);
                    if (rows > 0) {
                        inserted++;
                    } else {
                        failedWords.add(word);
                    }
                } catch (Exception e) {
                    failedWords.add(word + " (" + e.getMessage() + ")");
                }
            }

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("langCode", langCode);
            result.put("level", level);
            result.put("requested", count);
            result.put("generated", generatedWords.size());
            result.put("inserted", inserted);
            result.put("skipped", skipped);
            result.put("failed", failedWords.size());
            if (!failedWords.isEmpty()) {
                result.put("failedWords", failedWords);
            }
            return Result.success(result);
        } catch (Exception e) {
            log.error("批量生成词汇失败: {}", e.getMessage(), e);
            return Result.error("批量生成词汇失败：" + e.getMessage());
        }
    }
}
