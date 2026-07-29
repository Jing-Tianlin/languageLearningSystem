package com.cupk.controller;

import com.cupk.common.Result;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.web.bind.annotation.*;

import java.sql.PreparedStatement;
import java.util.*;

/**
 * WritingController — 写作训练接口（11 种语言，2 级）
 * Level 1 = 仿写, Level 3 = 自由写作
 */
@RestController
@RequestMapping("/writing")
public class WritingController {

    private static final Logger log = LoggerFactory.getLogger(WritingController.class);

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @PostConstruct
    public void init() {
        try {
            jdbcTemplate.execute("ALTER TABLE writing_history ADD COLUMN score INT DEFAULT NULL");
        } catch (Exception ignored) {}
        try {
            jdbcTemplate.execute("ALTER TABLE writing_history ADD COLUMN score_detail TEXT DEFAULT NULL");
        } catch (Exception ignored) {}
    }

    @GetMapping("/prompt")
    public Result<Map<String, Object>> getPrompt(
            @RequestParam(defaultValue = "1") Integer level,
            @RequestParam(defaultValue = "en") String langCode) {
        return Result.success(buildPrompt(level, langCode));
    }

    @GetMapping("/prompts")
    public Result<List<Map<String, Object>>> getAllPrompts(
            @RequestParam(defaultValue = "en") String langCode) {
        List<Map<String, Object>> list = new ArrayList<>();
        list.add(buildPrompt(1, langCode));
        list.add(buildPrompt(3, langCode));
        return Result.success(list);
    }

    @PostMapping("/submit")
    public Result<Map<String, Object>> submitWriting(@RequestBody Map<String, Object> body) {
        Long userId = parseLong(body.get("userId"));
        if (userId == null) return Result.error(400, "缺少 userId");

        String text = body.getOrDefault("text", "").toString();
        if (text.isBlank()) return Result.error(400, "写作内容不能为空");

        Integer revisionCount = parseInt(body.get("revisionCount"), 0);
        String langCode = body.getOrDefault("langCode", "en").toString();
        Integer level = parseInt(body.get("level"), 1);
        String type = body.getOrDefault("type", "").toString();
        String promptJson = body.getOrDefault("promptJson", "").toString();

        // 仿写无 topic → 自动从 template 截取
        String topic = body.getOrDefault("topic", "").toString();
        if (topic.isBlank()) {
            try {
                Map<String, Object> prompt = new com.fasterxml.jackson.databind.ObjectMapper().readValue(promptJson, Map.class);
                String template = prompt.getOrDefault("template", "").toString();
                if (!template.isBlank()) {
                    topic = (langCode.equals("ja") || langCode.equals("ko") || langCode.equals("zh"))
                        ? (template.length() > 20 ? template.substring(0, 20) + "…" : template)
                        : (template.length() > 40 ? template.substring(0, 40) + "…" : template);
                }
            } catch (Exception ignored) {}
            if (topic.isBlank()) topic = type;
        }
        final String finalTopic = topic;

        // 写入 writing_history，用 KeyHolder 捕获自增 ID
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(con -> {
            PreparedStatement ps = con.prepareStatement(
                "INSERT INTO writing_history (user_id, lang_code, level, topic, type, submitted_text, prompt_json) VALUES (?,?,?,?,?,?,?)",
                PreparedStatement.RETURN_GENERATED_KEYS);
            ps.setLong(1, userId);
            ps.setString(2, langCode);
            ps.setInt(3, level);
            ps.setString(4, finalTopic);
            ps.setString(5, type);
            ps.setString(6, text);
            ps.setString(7, promptJson);
            return ps;
        }, keyHolder);
        Long historyId = keyHolder.getKey() != null ? keyHolder.getKey().longValue() : null;

        // 同步写入 writing_submissions（兼容旧逻辑）
        try {
            jdbcTemplate.update(
                "INSERT INTO writing_submissions (user_id, prompt_id, submitted_text, revision_count, lang_code, level) VALUES (?,?,?,?,?,?)",
                userId, body.get("promptId"), text, revisionCount, langCode, level);
        } catch (Exception e) {
            log.warn("写入 writing_submissions 失败 userId={}", userId, e);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("submitted", true);
        result.put("historyId", historyId);
        result.put("topic", topic);
        result.put("message", "写作已提交！");
        result.put("wordCount", text.trim().split("\\s+").length);
        return Result.success(result);
    }

    @PostMapping("/save-score")
    public Result<Map<String, Object>> saveScore(@RequestBody Map<String, Object> body) {
        Long userId = parseLong(body.get("userId"));
        if (userId == null) return Result.error(400, "缺少 userId");

        Integer score = parseInt(body.get("score"), 0);
        String scoreDetail = body.getOrDefault("scoreDetail", "").toString();
        Long historyId = parseLong(body.get("historyId"));

        int updated;
        if (historyId != null) {
            updated = jdbcTemplate.update(
                "UPDATE writing_history SET score = ?, score_detail = ? WHERE id = ? AND user_id = ?",
                score, scoreDetail, historyId, userId);
        } else {
            updated = jdbcTemplate.update(
                "UPDATE writing_history SET score = ?, score_detail = ? WHERE user_id = ? ORDER BY id DESC LIMIT 1",
                score, scoreDetail, userId);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("saved", updated > 0);
        result.put("message", updated > 0 ? "评分已保存" : "未找到对应记录");
        return Result.success(result);
    }

    // ==================== 11 种语言题目 ====================

    private Map<String, Object> buildPrompt(int level, String langCode) {
        return switch (langCode) {
            case "ja" -> buildJapanesePrompt(level);
            case "ko" -> buildKoreanPrompt(level);
            case "fr" -> buildFrenchPrompt(level);
            case "de" -> buildGermanPrompt(level);
            case "es" -> buildSpanishPrompt(level);
            case "it" -> buildItalianPrompt(level);
            case "pt" -> buildPortuguesePrompt(level);
            case "ru" -> buildRussianPrompt(level);
            case "zh" -> buildChinesePrompt(level);
            default  -> buildEnglishPrompt(level);
        };
    }

    private Map<String, Object> buildEnglishPrompt(int level) {
        Map<String, Object> p = base("en", level);
        if (level == 1) {
            p.put("type", "仿写"); p.put("typeEn", "Imitation");
            p.put("template", "I usually go to school by bus, but yesterday I went by bike because the weather was nice.");
            p.put("instruction", "请替换划线部分，仿照结构写出你自己的句子。注意保持时态一致。");
            p.put("requiredWords", List.of("usually", "because"));
        } else {
            p.put("type", "自由写作"); p.put("typeEn", "Free Writing");
            p.put("topic", "Describe your favorite season and explain why you like it.");
            p.put("instruction", "请用 50-80 词描述你最喜欢的季节。");
            p.put("wordLimit", 80);
        }
        return p;
    }

    private Map<String, Object> buildJapanesePrompt(int level) {
        Map<String, Object> p = base("ja", level);
        if (level == 1) {
            p.put("type", "模写"); p.put("typeEn", "Imitation");
            p.put("template", "私は毎日電車で学校に行きますが、昨日は天気が良かったので歩いて行きました。");
            p.put("instruction", "下線部を置き換えて、自分の状況に合わせて書き直してください。");
            p.put("requiredWords", List.of("毎日", "ので"));
        } else {
            p.put("type", "自由作文"); p.put("typeEn", "Free Writing");
            p.put("topic", "あなたの一番好きな季節について説明してください。");
            p.put("instruction", "50-80文字で好きな季節を説明してください。");
            p.put("wordLimit", 80);
        }
        return p;
    }

    private Map<String, Object> buildKoreanPrompt(int level) {
        Map<String, Object> p = base("ko", level);
        if (level == 1) {
            p.put("type", "모방 작문"); p.put("typeEn", "Imitation");
            p.put("template", "저는 보통 버스로 학교에 가지만, 어제는 날씨가 좋아서 자전거로 갔습니다.");
            p.put("instruction", "밑줄 부분을 바꿔서 자신의 상황에 맞게 다시 써보세요.");
            p.put("requiredWords", List.of("보통", "그래서"));
        } else {
            p.put("type", "자유 작문"); p.put("typeEn", "Free Writing");
            p.put("topic", "가장 좋아하는 계절과 그 이유를 설명하세요.");
            p.put("instruction", "50-80단어로 좋아하는 계절을 설명하세요.");
            p.put("wordLimit", 80);
        }
        return p;
    }

    private Map<String, Object> buildFrenchPrompt(int level) {
        Map<String, Object> p = base("fr", level);
        if (level == 1) {
            p.put("type", "Imitation"); p.put("typeEn", "Imitation");
            p.put("template", "Je vais habituellement à l'école en bus, mais hier j'y suis allé à vélo parce qu'il faisait beau.");
            p.put("instruction", "Remplacez les parties soulignées et écrivez votre propre phrase.");
            p.put("requiredWords", List.of("habituellement", "parce que"));
        } else {
            p.put("type", "Écriture libre"); p.put("typeEn", "Free Writing");
            p.put("topic", "Décrivez votre saison préférée et expliquez pourquoi.");
            p.put("instruction", "Écrivez 50-80 mots sur votre saison préférée.");
            p.put("wordLimit", 80);
        }
        return p;
    }

    private Map<String, Object> buildGermanPrompt(int level) {
        Map<String, Object> p = base("de", level);
        if (level == 1) {
            p.put("type", "Nachahmung"); p.put("typeEn", "Imitation");
            p.put("template", "Ich fahre normalerweise mit dem Bus zur Schule, aber gestern bin ich mit dem Fahrrad gefahren, weil das Wetter schön war.");
            p.put("instruction", "Ersetzen Sie die unterstrichenen Teile und schreiben Sie Ihren eigenen Satz.");
            p.put("requiredWords", List.of("normalerweise", "weil"));
        } else {
            p.put("type", "Freies Schreiben"); p.put("typeEn", "Free Writing");
            p.put("topic", "Beschreiben Sie Ihre Lieblingsjahreszeit.");
            p.put("instruction", "Schreiben Sie 50-80 Wörter über Ihre Lieblingsjahreszeit.");
            p.put("wordLimit", 80);
        }
        return p;
    }

    private Map<String, Object> buildSpanishPrompt(int level) {
        Map<String, Object> p = base("es", level);
        if (level == 1) {
            p.put("type", "Imitación"); p.put("typeEn", "Imitation");
            p.put("template", "Normalmente voy a la escuela en autobús, pero ayer fui en bicicleta porque hacía buen tiempo.");
            p.put("instruction", "Reemplaza las partes subrayadas y escribe tu propia oración.");
            p.put("requiredWords", List.of("normalmente", "porque"));
        } else {
            p.put("type", "Escritura libre"); p.put("typeEn", "Free Writing");
            p.put("topic", "Describe tu estación favorita y explica por qué te gusta.");
            p.put("instruction", "Escribe 50-80 palabras sobre tu estación favorita.");
            p.put("wordLimit", 80);
        }
        return p;
    }

    private Map<String, Object> buildItalianPrompt(int level) {
        Map<String, Object> p = base("it", level);
        if (level == 1) {
            p.put("type", "Imitazione"); p.put("typeEn", "Imitation");
            p.put("template", "Di solito vado a scuola in autobus, ma ieri sono andato in bicicletta perché il tempo era bello.");
            p.put("instruction", "Sostituisci le parti sottolineate e scrivi la tua frase.");
            p.put("requiredWords", List.of("di solito", "perché"));
        } else {
            p.put("type", "Scrittura libera"); p.put("typeEn", "Free Writing");
            p.put("topic", "Descrivi la tua stagione preferita e spiega perché ti piace.");
            p.put("instruction", "Scrivi 50-80 parole sulla tua stagione preferita.");
            p.put("wordLimit", 80);
        }
        return p;
    }

    private Map<String, Object> buildPortuguesePrompt(int level) {
        Map<String, Object> p = base("pt", level);
        if (level == 1) {
            p.put("type", "Imitação"); p.put("typeEn", "Imitation");
            p.put("template", "Eu normalmente vou para a escola de ônibus, mas ontem fui de bicicleta porque o tempo estava bom.");
            p.put("instruction", "Substitua as partes sublinhadas e escreva sua própria frase.");
            p.put("requiredWords", List.of("normalmente", "porque"));
        } else {
            p.put("type", "Escrita livre"); p.put("typeEn", "Free Writing");
            p.put("topic", "Descreva sua estação favorita e explique por que você gosta dela.");
            p.put("instruction", "Escreva 50-80 palavras sobre sua estação favorita.");
            p.put("wordLimit", 80);
        }
        return p;
    }

    private Map<String, Object> buildRussianPrompt(int level) {
        Map<String, Object> p = base("ru", level);
        if (level == 1) {
            p.put("type", "Имитация"); p.put("typeEn", "Imitation");
            p.put("template", "Обычно я езжу в школу на автобусе, но вчера я поехал на велосипеде, потому что была хорошая погода.");
            p.put("instruction", "Замените подчёркнутые части и напишите своё предложение.");
            p.put("requiredWords", List.of("обычно", "потому что"));
        } else {
            p.put("type", "Свободное письмо"); p.put("typeEn", "Free Writing");
            p.put("topic", "Опишите своё любимое время года и объясните почему.");
            p.put("instruction", "Напишите 50-80 слов о вашем любимом времени года.");
            p.put("wordLimit", 80);
        }
        return p;
    }

    private Map<String, Object> buildChinesePrompt(int level) {
        Map<String, Object> p = base("zh", level);
        if (level == 1) {
            p.put("type", "模仿写作"); p.put("typeEn", "Imitation");
            p.put("template", "我通常坐公交车去上学，但昨天天气很好，所以我骑了自行车。");
            p.put("instruction", "请替换划线部分，仿照结构写出你自己的句子。");
            p.put("requiredWords", List.of("通常", "因为"));
        } else {
            p.put("type", "自由写作"); p.put("typeEn", "Free Writing");
            p.put("topic", "描述你最喜欢的季节，并说明原因。");
            p.put("instruction", "请用 80-120 字描述你最喜欢的季节。");
            p.put("wordLimit", 120);
        }
        return p;
    }

    // ==================== 工具方法 ====================

    private Map<String, Object> base(String langCode, int level) {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("level", level);
        p.put("langCode", langCode);
        return p;
    }

    private Long parseLong(Object value) {
        if (value == null) return null;
        if (value instanceof Number) return ((Number) value).longValue();
        try { return Long.valueOf(value.toString()); } catch (NumberFormatException e) { return null; }
    }

    private Integer parseInt(Object value, int defaultValue) {
        if (value == null) return defaultValue;
        if (value instanceof Number) return ((Number) value).intValue();
        try { return Integer.valueOf(value.toString()); } catch (NumberFormatException e) { return defaultValue; }
    }
}
