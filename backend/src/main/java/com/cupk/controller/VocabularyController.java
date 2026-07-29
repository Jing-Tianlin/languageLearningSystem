package com.cupk.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cupk.common.Result;
import com.cupk.mapper.UserProgressMapper;
import com.cupk.mapper.VocabularyMapper;
import com.cupk.pojo.UserProgress;
import com.cupk.pojo.Vocabulary;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/vocabulary")
public class VocabularyController {
    private static final Logger log = LoggerFactory.getLogger(VocabularyController.class);
    @Autowired
    private VocabularyMapper vocabularyMapper;
    @Autowired
    private UserProgressMapper progressMapper;
    @Autowired
    private com.cupk.service.DeepSeekService deepSeekService;
    @Autowired
    private com.cupk.service.LearningDataService learningDataService;

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
     * 智能选词接口（基于艾宾浩斯遗忘曲线）
     * GET /vocabulary/smart-select?userId=1&langCode=en&count=20&mode=mix
     * 
     * mode 可选值:
     * - mix: 混合模式（60%待复习 + 30%新词 + 10%已掌握巩固）
     * - review: 只复习过期单词
     * - new: 只学习新词
     * - weak: 只学习薄弱词
     */
    @GetMapping("/smart-select")
    public Result<List<Vocabulary>> smartSelect(
            @RequestParam(required = false) Long userId,
            @RequestParam(defaultValue = "en") String langCode,
            @RequestParam(defaultValue = "20") Integer count,
            @RequestParam(defaultValue = "mix") String mode) {

        QueryWrapper<Vocabulary> vocabQuery = new QueryWrapper<>();
        vocabQuery.eq("lang_code", langCode);
        vocabQuery.isNotNull("word");
        vocabQuery.isNotNull("definition");
        List<Vocabulary> allVocabs = vocabularyMapper.selectList(vocabQuery);

        if (allVocabs.isEmpty()) {
            return Result.success(new ArrayList<>());
        }

        Map<Long, UserProgress> progressMap = new HashMap<>();
        if (userId != null) {
            QueryWrapper<UserProgress> progressQuery = new QueryWrapper<>();
            progressQuery.eq("user_id", userId);
            progressQuery.eq("lang_code", langCode);
            List<UserProgress> progresses = progressMapper.selectList(progressQuery);
            for (UserProgress p : progresses) {
                progressMap.put(p.getVocabId(), p);
            }
        }

        LocalDateTime now = LocalDateTime.now();

        List<Vocabulary> expiredList = new ArrayList<>();
        List<Vocabulary> newList = new ArrayList<>();
        List<Vocabulary> weakList = new ArrayList<>();
        List<Vocabulary> masteredList = new ArrayList<>();

        for (Vocabulary v : allVocabs) {
            UserProgress p = progressMap.get(v.getId());
            if (p == null) {
                newList.add(v);
            } else {
                Integer mastery = p.getMasteryLevel() != null ? p.getMasteryLevel() : 0;
                Integer fam = p.getFamiliarity() != null ? p.getFamiliarity() : 0;
                LocalDateTime nextReview = p.getNextReviewTime();

                if (nextReview != null && nextReview.isBefore(now)) {
                    expiredList.add(v);
                } else if (mastery <= 1 || fam < 30) {
                    weakList.add(v);
                } else if (mastery >= 3 && fam >= 80) {
                    masteredList.add(v);
                }
            }
        }

        List<Vocabulary> result = new ArrayList<>();

        switch (mode) {
            case "review":
                result.addAll(shuffleAndLimit(expiredList, count));
                break;
            case "new":
                result.addAll(shuffleAndLimit(newList, count));
                break;
            case "weak":
                result.addAll(shuffleAndLimit(weakList, count));
                if (result.size() < count) {
                    result.addAll(shuffleAndLimit(expiredList, count - result.size()));
                }
                break;
            case "mix":
            default:
                // 自适应比例：根据用户掌握率动态调整
                double masteryRate = (userId != null) ? learningDataService.getUserMasteryRate(userId) : 0.0;
                int reviewPct, newPct, masteredPct;
                if (masteryRate >= 0.6) {
                    // 高掌握率：集中复习巩固，少量新词
                    reviewPct = 70; newPct = 15; masteredPct = 15;
                } else if (masteryRate >= 0.3) {
                    // 中掌握率：均衡分配
                    reviewPct = 50; newPct = 35; masteredPct = 15;
                } else {
                    // 低掌握率/新手：大量新词，少量复习
                    reviewPct = 30; newPct = 60; masteredPct = 10;
                }
                int reviewCount = Math.max(1, (count * reviewPct) / 100);
                int newCount = Math.max(1, (count * newPct) / 100);
                int masteredCountReal = count - reviewCount - newCount;

                result.addAll(shuffleAndLimit(expiredList, reviewCount));
                if (result.size() < reviewCount) {
                    result.addAll(shuffleAndLimit(weakList, reviewCount - result.size()));
                }

                result.addAll(shuffleAndLimit(newList, newCount));

                if (masteredCountReal > 0 && !masteredList.isEmpty()) {
                    result.addAll(shuffleAndLimit(masteredList, masteredCountReal));
                }
                break;
        }

        Collections.shuffle(result);
        if (result.size() > count) {
            result = result.subList(0, count);
        }

        return Result.success(result);
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
            distractors = (List<String>) aiResult.get("distractors");
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
        Integer count = (Integer) body.getOrDefault("count", 3);
        Boolean useAI = (Boolean) body.getOrDefault("useAI", true);

        if (vocabIds == null || vocabIds.isEmpty()) {
            return Result.error(400, "vocabIds 不能为空");
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

        QueryWrapper<Vocabulary> vocabQuery = new QueryWrapper<>();
        vocabQuery.eq("lang_code", langCode);
        long totalVocab = vocabularyMapper.selectCount(vocabQuery);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("totalVocab", totalVocab);

        if (userId != null) {
            QueryWrapper<UserProgress> progressQuery = new QueryWrapper<>();
            progressQuery.eq("user_id", userId);
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
        String langCode = (String) body.getOrDefault("langCode", "en");
        String level = (String) body.getOrDefault("level", "");
        Integer count = (Integer) body.getOrDefault("count", 20);
        String category = (String) body.getOrDefault("category", "");

        if (level == null || level.isEmpty()) {
            return Result.error(400, "等级不能为空");
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
