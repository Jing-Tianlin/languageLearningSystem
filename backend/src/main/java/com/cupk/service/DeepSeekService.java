package com.cupk.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;
import java.util.function.Consumer;
import com.fasterxml.jackson.core.type.TypeReference;

/**
 * DeepSeekService — AI 辅助学习服务
 * 通过 DeepSeek API 提供: i+1句子生成 / 语法纠错 / 写作评分
 */
@Service
public class DeepSeekService {

    private static final Logger log = LoggerFactory.getLogger(DeepSeekService.class);

    @Value("${deepseek.api.key:sk-your-default-key}")
    private String apiKey;

    private static final String API_URL = "https://api.deepseek.com/v1/chat/completions";
    private final HttpClient client = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .build();
    private final ObjectMapper mapper = new ObjectMapper();

    // 已生成例句缓存（防止重复）
    private final Set<String> generatedExamples = Collections.synchronizedSet(new HashSet<>());

    /**
     * i+1 可理解输入句子生成
     * 传入用户已掌握的词汇和当前目标语言，返回含有掌握词+略高级词汇的句子
     */
    public Map<String, Object> generateIPlusOneSentences(List<String> knownWords, List<String> newWords, String targetLang, int count) {
        String prompt = String.format(
            "你是一位%s语言教师。你的学生已经掌握了以下词汇: %s。现在正在学习这些新词汇: %s。\n" +
            "请生成%d个简单、自然的句子，每个句子应包含1-2个新词汇(标记为**newWord**)，其余的用已掌握的词汇填充。\n" +
            "输出格式为JSON数组: [{\"sentence\": \"...\", \"newWords\": [\"word1\", \"word2\"], \"translation\": \"...\"}]\n" +
            "注意: 句子不要太长，适合中级学习者。只返回JSON，不要其他文字。",
            getLangName(targetLang),
            String.join(", ", knownWords.isEmpty() ? List.of("hello", "goodbye", "thank you", "water", "food") : knownWords),
            String.join(", ", newWords),
            count
        );

        try {
            String reply = chatCompletion(prompt);
            String json = extractJSON(reply);
            JsonNode parsed = mapper.readTree(json);
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("sentences", mapper.convertValue(parsed, new TypeReference<List<Map<String, Object>>>() {}));
            result.put("raw", reply);
            return result;
        } catch (Exception e) {
            return fallbackIPlusOne(knownWords, newWords);
        }
    }

    /** 语法纠错 (强化版) */
    public Map<String, Object> correctGrammar(String text, String lang) {
        String prompt = String.format(
            "你是一位%s语言教师。请仔细检查以下句子的语法错误。必须以JSON格式返回，不要其他文字。\n"
            + "如果句子没有错误，hasErrors为false，errors为空数组。\n"
            + "如果有错误，请在errors数组中详细列出每一个错误，包括原始错误部分、正确形式和对应的语法规则说明。\n"
            + "格式: {\"hasErrors\": true/false, \"correctedText\": \"修正后的完整句子\", \"errors\": [{\"original\": \"错误部分\", \"correction\": \"正确形式\", \"rule\": \"语法规则解释\"}]}\n\n"
            + "需要检查的句子: \"%s\"\n\n"
            + "重要提示: 请务必仔细检查。即使是微小的语法错误(如缺少定冠词、主谓不一致、时态错误、介词错误等)都应该被检出。只返回JSON。",
            getLangName(lang), text
        );
        try {
            String reply = chatCompletion(prompt);
            String json = extractJSON(reply);
            JsonNode parsed = mapper.readTree(json);
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("hasErrors", parsed.get("hasErrors").asBoolean());
            result.put("correctedText", parsed.get("correctedText").asText());
            result.put("errors", mapper.convertValue(parsed.get("errors"), new TypeReference<List<Map<String, String>>>() {}));
            return result;
        } catch (Exception e) {
            // 本地纠错回退
            return localGrammarCheck(text);
        }
    }

    /** 本地语法检查 (API不可用时的回退) */
    private Map<String, Object> localGrammarCheck(String text) {
        List<Map<String, String>> errors = new ArrayList<>();
        StringBuilder corrected = new StringBuilder();

        // 规则1: He/She/It + 动词原形 (缺 -s/-es)
        String[] words = text.split("\\s+");
        for (int i = 0; i < words.length - 1; i++) {
            String w = words[i].toLowerCase();
            String next = words[i + 1].toLowerCase();
            if (("he".equals(w) || "she".equals(w) || "it".equals(w)) && next.matches("[a-z]+") && !next.endsWith("s") && !next.equals("is") && !next.equals("has") && !next.equals("does") && !"can,will,may,must".contains(next)) {
                errors.add(Map.of("original", next, "correction", next + "s", "rule", "一般现在时第三人称单数需加 -s/-es"));
            }
        }
        // 规则2: I is → I am
        if (text.toLowerCase().contains("i is")) errors.add(Map.of("original", "is", "correction", "am", "rule", "I 后面用 am，不是 is"));
        // 规则3: are → is (第三人称)
        if (text.toLowerCase().matches(".*\\b(he|she|it)\\b.*\\bare\\b.*")) errors.add(Map.of("original", "are", "correction", "is", "rule", "第三人称单数用 is，不用 are"));
        // 规则4: good in → good at
        if (text.toLowerCase().contains("good in")) errors.add(Map.of("original", "in", "correction", "at", "rule", "be good at 固定搭配，不用 in"));

        boolean hasErrors = !errors.isEmpty();
        String correctedText = hasErrors ? text + " [有错误需修正]" : text;
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("hasErrors", hasErrors);
        result.put("correctedText", correctedText);
        result.put("errors", errors);
        return result;
    }

    /** 写作多维度评分 (强化版) */
    public Map<String, Object> scoreWriting(String text, String lang, String topic) {
        String prompt = String.format(
            "你是一位严格的%s语言考官。请认真评分以下作文并给出具体的修改建议。\n"
            + "作文题目: %s\n"
            + "作文内容: \"%s\"\n\n"
            + "评分标准:\n"
            + "- grammar (语法准确度 0-100): 检查主谓一致、时态、冠词、介词等具体问题。满分100，有明显语法错误则低于60。\n"
            + "- vocabulary (词汇丰富度 0-100): 是否有重复用词? 是否使用基础词汇? 满分100，只使用小学词汇给30-50。\n"
            + "- coherence (结构连贯性 0-100): 句子衔接是否自然? 逻辑是否清晰?\n"
            + "- overall (综合分): grammar*0.4 + vocabulary*0.3 + coherence*0.3\n\n"
            + "你必须返回真实的、有价值的评分和建议。不要给出虚高的分数。\n"
            + "返回JSON格式: {\"grammar\": 85, \"vocabulary\": 72, \"coherence\": 80, \"overall\": 79, \"suggestions\": [\"具体建议1\", \"具体建议2\", \"具体建议3\"]}\n只返回JSON。",
            getLangName(lang), topic.isEmpty() ? "自由写作" : topic, text
        );
        try {
            String reply = chatCompletion(prompt);
            String json = extractJSON(reply);
            JsonNode parsed = mapper.readTree(json);
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("grammar", parsed.get("grammar").asInt());
            result.put("vocabulary", parsed.get("vocabulary").asInt());
            result.put("coherence", parsed.get("coherence").asInt());
            result.put("overall", parsed.get("overall").asInt());
            result.put("suggestions", mapper.convertValue(parsed.get("suggestions"), new TypeReference<List<String>>() {}));
            return result;
        } catch (Exception e) {
            // 本地评分回退
            return localScoreWriting(text);
        }
    }

    /** 本地写作评分 (API不可用时的回退) */
    private Map<String, Object> localScoreWriting(String text) {
        int wordCount = text.trim().split("\\s+").length;
        int grammar = wordCount < 20 ? 40 : 60;
        int vocab = wordCount < 15 ? 30 : 50;
        int coherence = wordCount < 10 ? 20 : 50;
        int overall = (int)(grammar * 0.4 + vocab * 0.3 + coherence * 0.3);
        List<String> suggestions = new ArrayList<>();
        if (wordCount < 30) suggestions.add("建议增加内容，目前仅" + wordCount + "词，建议扩充到50词以上");
        if (wordCount < 15) suggestions.add("作文过短，建议包含至少3-4句完整句子");
        if (!text.contains(".")) suggestions.add("缺少句号，请使用完整的句子结构");
        if (suggestions.isEmpty()) suggestions.add("整体结构不错，可以尝试使用更复杂的句式");
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("grammar", grammar);
        result.put("vocabulary", vocab);
        result.put("coherence", coherence);
        result.put("overall", overall);
        result.put("suggestions", suggestions);
        return result;
    }

    /**
     * 生成单词选择题干扰选项（AI 驱动）
     * @param word 目标单词
     * @param correctDefinition 正确释义
     * @param partOfSpeech 词性
     * @param langCode 语言代码
     * @param count 干扰选项数量
     * @return Map包含 correct（正确答案）和 distractors（干扰选项列表）
     */
    public Map<String, Object> generateQuizOptions(String word, String correctDefinition, String partOfSpeech, String langCode, int count) {
        String posName = getPosName(partOfSpeech);
        String prompt = String.format(
            "你是一位%s语言教师。请为以下单词生成%d个高质量的干扰选项（错误释义），用于选择题。\n\n" +
            "目标单词: %s\n" +
            "词性: %s\n" +
            "正确释义: %s\n\n" +
            "要求:\n" +
            "1. 干扰选项必须与正确释义完全不同，但看起来合理且具有迷惑性\n" +
            "2. 干扰选项必须是与目标单词相同词性的词汇释义\n" +
            "3. 干扰选项应该是同领域或同语义场的词汇，例如同义词、反义词、相关概念\n" +
            "4. 每个干扰选项格式为：词性缩写.释义，如\"n.记忆力\"或\"v.拒绝\"\n" +
            "5. 输出格式必须是严格的JSON: {\"distractors\": [\"释义1\", \"释义2\", \"释义3\"]}\n" +
            "6. 只返回JSON对象，不要其他文字，不要Markdown格式\n" +
            "7. 每个释义要简洁，不超过20个汉字",
            getLangName(langCode), count, word, posName, correctDefinition
        );

        try {
            String reply = chatCompletion(prompt, 500);
            String json = extractJSON(reply);
            JsonNode parsed = mapper.readTree(json);
            
            List<String> distractors = new ArrayList<>();
            if (parsed.has("distractors") && parsed.get("distractors").isArray()) {
                for (JsonNode d : parsed.get("distractors")) {
                    String def = d.asText().trim();
                    if (!def.isEmpty() && !def.equals(correctDefinition)) {
                        distractors.add(def);
                    }
                }
            }
            
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("word", word);
            result.put("correct", correctDefinition);
            result.put("distractors", distractors);
            result.put("aiGenerated", true);
            return result;
        } catch (Exception e) {
            log.error("AI 选项生成失败: word={}, error={}", word, e.getMessage());
            return Map.of("word", word, "correct", correctDefinition, "distractors", List.of(), "aiGenerated", false);
        }
    }

    private static String getPosName(String pos) {
        if (pos == null || pos.isEmpty()) return "未知词性";
        return switch (pos.toLowerCase()) {
            case "noun" -> "名词";
            case "verb" -> "动词";
            case "adjective", "adj" -> "形容词";
            case "adverb", "adv" -> "副词";
            case "pronoun", "pron" -> "代词";
            case "preposition", "prep" -> "介词";
            case "conjunction", "conj" -> "连词";
            case "interjection", "int" -> "感叹词";
            case "article" -> "冠词";
            default -> pos;
        };
    }

    // === 内部工具 ===

    private String chatCompletion(String prompt) throws Exception {
        return chatCompletion(prompt, 1000);
    }

    private String chatCompletion(String prompt, int maxTokens) throws Exception {
        ObjectNode body = mapper.createObjectNode();
        body.put("model", "deepseek-chat");
        body.put("max_tokens", maxTokens);
        ArrayNode msgs = mapper.createArrayNode();
        ObjectNode msg = mapper.createObjectNode();
        msg.put("role", "user");
        msg.put("content", prompt);
        msgs.add(msg);
        body.set("messages", msgs);

        HttpRequest req = HttpRequest.newBuilder()
            .uri(URI.create(API_URL))
            .header("Content-Type", "application/json")
            .header("Authorization", "Bearer " + apiKey)
            .timeout(Duration.ofSeconds(30))
            .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body)))
            .build();

        HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() != 200) {
            throw new RuntimeException("DeepSeek API error: " + resp.statusCode() + " " + resp.body());
        }
        JsonNode root = mapper.readTree(resp.body());
        JsonNode choices = root.get("choices");
        if (choices != null && choices.size() > 0) {
            JsonNode message = choices.get(0).get("message");
            if (message != null && message.get("content") != null) {
                return message.get("content").asText();
            }
        }
        throw new RuntimeException("DeepSeek API 返回格式异常: " + resp.body());
    }

    /**
     * 根据用户输入的单词或汉语生成例句
     */
    public Map<String, Object> generateExampleSentences(String wordOrPhrase, String targetLang, int count) {
        String prompt = String.format(
            "你是一位%s语言教师。请围绕「%s」用目标语言生成%d个简洁自然的例句，每句配中文翻译。\n" +
            "格式: 例句1\n翻译1\n\n例句2\n翻译2\n...\n" +
            "只输出例句和翻译，不要序号、不要解释。",
            getLangName(targetLang), wordOrPhrase, count
        );
        try {
            String reply = chatCompletion(prompt, 1000);
            log.info("AI 例句生成: word={}, lang={}, reply_preview={}", wordOrPhrase, targetLang,
                reply.substring(0, Math.min(200, reply.length())));
            String[] lines = reply.trim().split("\\n");
            List<Map<String, String>> sList = new ArrayList<>();
            for (int i = 0; i + 1 < lines.length; i += 2) {
                String s = lines[i].trim();
                String t = lines[i + 1].trim();
                if (!s.isEmpty() && !t.isEmpty() && !s.contains("{") && !s.contains("[")) {
                    sList.add(Map.of("sentence", s, "translation", t));
                }
            }
            if (sList.isEmpty()) throw new RuntimeException("no valid pairs extracted");
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("sentences", sList);
            return result;
        } catch (Exception e) {
            log.error("AI 例句生成失败: word={}, lang={}, error={}", wordOrPhrase, targetLang, e.getMessage());
            return Map.of("sentences", List.of(
                Map.of("sentence", "（AI 例句服务暂不可用，请稍后重试）",
                       "translation", "（AI example service temporarily unavailable）")
            ));
        }
    }

    // 已废弃，保留以避免编译错误
    private Map<String, Object> fallbackExamples(String word, String lang, int count) {
        List<Map<String, String>> sentences = new ArrayList<>();
        String langName = getLangName(lang);
        // 按目标语言给出合理回退
        String[][] templates = switch (lang) {
            case "ja" -> new String[][] {
                {"「" + word + "」は日常会話でよく使われる表現です。", "「" + word + "」是日常会话中经常使用的表达。"},
                {"先生に「" + word + "」と言われました。", "老师对我说了「" + word + "」。"},
                {"「" + word + "」の使い方を教えてください。", "请教我「" + word + "」的用法。"},
            };
            case "ko" -> new String[][] {
                {"'" + word + "'은(는) 일상 대화에서 자주 사용하는 표현입니다.", "'" + word + "'是日常会话中常用的表达。"},
                {"선생님께서 '" + word + "'라고 말씀하셨어요.", "老师说了'" + word + "'。"},
                {"'" + word + "'의 사용법을 가르쳐 주세요.", "请教我'" + word + "'的用法。"},
            };
            case "fr" -> new String[][] {
                {"« " + word + " » est une expression courante en français.", "「" + word + "」是法语常用表达。"},
                {"Le professeur a dit « " + word + " ».", "老师说了「" + word + "」。"},
                {"Pouvez-vous m'expliquer l'usage de « " + word + " » ?", "能解释一下「" + word + "」的用法吗？"},
            };
            case "de" -> new String[][] {
                {"„" + word + "“ ist ein häufig verwendeter Ausdruck im Deutschen.", "„" + word + "“ 是德语常用表达。"},
                {"Der Lehrer hat „" + word + "“ gesagt.", "老师说了„" + word + "“。"},
                {"Können Sie mir die Verwendung von „" + word + "“ erklären?", "能解释一下„" + word + "“的用法吗？"},
            };
            default -> new String[][] {
                {"The word \"" + word + "\" is commonly used in everyday conversation.", "单词 \"" + word + "\" 常用于日常对话中。"},
                {"Could you show me how to use \"" + word + "\" in a sentence?", "你能教我怎么在句子中用 \"" + word + "\" 吗？"},
                {"I learned the word \"" + word + "\" from my language teacher.", "我从语言老师那里学到了 \"" + word + "\" 这个词。"},
            };
        };
        for (int i = 0; i < Math.min(count, templates.length); i++) {
            sentences.add(Map.of("sentence", templates[i][0], "translation", templates[i][1]));
        }
        return Map.of("sentences", sentences);
    }

    /** 智能问答 */
    public Map<String, Object> answerQuestion(String question, String lang) {
        String prompt = String.format(
            "你是一位%s语言教师。请用目标语言回答用户的问题。\n"
            + "用户问题: \"%s\"\n"
            + "注意：\n"
            + "1. 不要使用星号(*)或其他Markdown标记。\n"
            + "2. 只用一种语言回答，不要双语言混用。\n"
            + "3. 用自然段落回答，不要使用分隔线。",
            getLangName(lang), question
        );
        try {
            String reply = chatCompletion(prompt, 2000);
            return Map.of("answer", reply);
        } catch (Exception e) {
            return Map.of("answer", "AI 服务暂时不可用，请稍后再试。");
        }
    }

    // ==================== 流式输出 ====================

    /**
     * 流式调用 DeepSeek chat/completions API（异步非阻塞）
     * 每收到一个 token 就回调 onToken，流结束后回调 onComplete
     */
    /**
     * 流式调用 DeepSeek，支持传入历史对话作为上下文记忆
     * @param history 历史消息列表，每条含 role(user/assistant) 和 content
     */
    public void streamChatCompletion(String systemPrompt, String userMessage,
                                      List<Map<String, String>> history,
                                      int maxTokens, Consumer<String> onToken,
                                      Consumer<String> onError, Runnable onComplete) {
        try {
            ObjectNode body = mapper.createObjectNode();
            body.put("model", "deepseek-chat");
            body.put("max_tokens", maxTokens);
            body.put("stream", true);
            body.put("temperature", 0.7);

            ArrayNode msgs = mapper.createArrayNode();

            // 1. 系统提示
            if (systemPrompt != null && !systemPrompt.isBlank()) {
                ObjectNode sysMsg = mapper.createObjectNode();
                sysMsg.put("role", "system");
                sysMsg.put("content", systemPrompt);
                msgs.add(sysMsg);
            }

            // 2. 历史对话（最近 N 轮，前端已控制数量）
            if (history != null && !history.isEmpty()) {
                for (Map<String, String> h : history) {
                    String role = h.get("role");
                    String content = h.get("content");
                    if (role != null && content != null && !content.isBlank()
                        && ("user".equals(role) || "assistant".equals(role))) {
                        ObjectNode histMsg = mapper.createObjectNode();
                        histMsg.put("role", role);
                        histMsg.put("content", content);
                        msgs.add(histMsg);
                    }
                }
            }

            // 3. 当前用户消息
            ObjectNode userMsg = mapper.createObjectNode();
            userMsg.put("role", "user");
            userMsg.put("content", userMessage);
            msgs.add(userMsg);

            body.set("messages", msgs);

            HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(API_URL))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + apiKey)
                .timeout(Duration.ofSeconds(90))
                .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body)))
                .build();

            client.sendAsync(req, HttpResponse.BodyHandlers.ofLines())
                .thenAccept(resp -> {
                    if (resp.statusCode() != 200) {
                        log.error("DeepSeek 流式API 错误: status={}", resp.statusCode());
                        onError.accept("API error: " + resp.statusCode());
                        onComplete.run();
                        return;
                    }
                    resp.body().forEach(line -> {
                        if (line == null || line.isBlank()) return;
                        if (line.startsWith("data: ")) {
                            String data = line.substring(6).trim();
                            if ("[DONE]".equals(data)) return;
                            try {
                                JsonNode node = mapper.readTree(data);
                                JsonNode delta = node.at("/choices/0/delta/content");
                                if (!delta.isMissingNode()) {
                                    String token = delta.asText();
                                    if (!token.isEmpty()) onToken.accept(token);
                                }
                            } catch (Exception e) {
                                log.debug("SSE 解析跳过: {}",
                                    line.substring(0, Math.min(80, line.length())));
                            }
                        }
                    });
                })
                .thenRun(onComplete)
                .exceptionally(e -> {
                    log.error("DeepSeek 流式调用失败: {}", e.getMessage(), e);
                    onError.accept("流式调用失败: " + e.getMessage());
                    onComplete.run();
                    return null;
                });
        } catch (Exception e) {
            log.error("DeepSeek 流式请求构建失败: {}", e.getMessage(), e);
            onError.accept("请求构建失败: " + e.getMessage());
            onComplete.run();
        }
    }

    /** 无历史对话的流式调用（兼容旧调用方） */
    public void streamChatCompletion(String systemPrompt, String userMessage,
                                      int maxTokens, Consumer<String> onToken,
                                      Consumer<String> onError, Runnable onComplete) {
        try {
            ObjectNode body = mapper.createObjectNode();
            body.put("model", "deepseek-chat");
            body.put("max_tokens", maxTokens);
            body.put("stream", true);
            body.put("temperature", 0.7);

            ArrayNode msgs = mapper.createArrayNode();
            if (systemPrompt != null && !systemPrompt.isBlank()) {
                ObjectNode sysMsg = mapper.createObjectNode();
                sysMsg.put("role", "system");
                sysMsg.put("content", systemPrompt);
                msgs.add(sysMsg);
            }
            ObjectNode userMsg = mapper.createObjectNode();
            userMsg.put("role", "user");
            userMsg.put("content", userMessage);
            msgs.add(userMsg);
            body.set("messages", msgs);

            HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(API_URL))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + apiKey)
                .timeout(Duration.ofSeconds(90))
                .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body)))
                .build();

            // sendAsync 才是真正的异步流式：每收到一行立即回调
            client.sendAsync(req, HttpResponse.BodyHandlers.ofLines())
                .thenAccept(resp -> {
                    if (resp.statusCode() != 200) {
                        log.error("DeepSeek 流式API 错误: status={}", resp.statusCode());
                        onError.accept("API error: " + resp.statusCode());
                        onComplete.run();
                        return;
                    }
                    resp.body().forEach(line -> {
                        if (line == null || line.isBlank()) return;
                        if (line.startsWith("data: ")) {
                            String data = line.substring(6).trim();
                            if ("[DONE]".equals(data)) return;
                            try {
                                JsonNode node = mapper.readTree(data);
                                JsonNode delta = node.at("/choices/0/delta/content");
                                if (!delta.isMissingNode()) {
                                    String token = delta.asText();
                                    if (!token.isEmpty()) onToken.accept(token);
                                }
                            } catch (Exception e) {
                                log.debug("SSE 解析跳过: {}",
                                    line.substring(0, Math.min(80, line.length())));
                            }
                        }
                    });
                })
                .thenRun(onComplete)
                .exceptionally(e -> {
                    log.error("DeepSeek 流式调用失败: {}", e.getMessage(), e);
                    onError.accept("流式调用失败: " + e.getMessage());
                    onComplete.run();
                    return null;
                });
        } catch (Exception e) {
            log.error("DeepSeek 流式请求构建失败: {}", e.getMessage(), e);
            onError.accept("请求构建失败: " + e.getMessage());
            onComplete.run();
        }
    }

    /** 流式智能问答（带上下文记忆） */
    public void streamAnswerQuestion(String question, String lang,
                                      List<Map<String, String>> history,
                                      Consumer<String> onToken, Consumer<String> onError,
                                      Runnable onComplete) {
        String prompt = String.format(
            "你是一位%s语言教师。请用中文回答用户的问题，简洁清晰。\n"
            + "注意：\n"
            + "1. 不要使用星号(*)或其他Markdown标记。\n"
            + "2. 用自然段落回答。\n"
            + "3. 回答要精炼，不超过300字。\n"
            + "4. 如果用户的问题与之前的对话相关，请结合上下文回答。",
            getLangName(lang)
        );
        streamChatCompletion(prompt, question, history, 1000, onToken, onError, onComplete);
    }

    private String extractJSON(String text) {
        if (text == null || text.isBlank()) return "{}";
        text = text.trim();
        // 去掉 markdown 代码块标记
        text = text.replaceAll("^```(?:json)?\\s*", "").replaceAll("\\s*```$", "");

        // 找最外层 JSON 对象
        int braceOpen = text.indexOf('{');
        if (braceOpen >= 0) {
            int depth = 0, end = -1;
            for (int i = braceOpen; i < text.length(); i++) {
                char ch = text.charAt(i);
                if (ch == '{') depth++;
                else if (ch == '}') {
                    depth--;
                    if (depth == 0) { end = i; break; }
                }
            }
            if (end > braceOpen) return text.substring(braceOpen, end + 1);
        }

        // 找最外层 JSON 数组
        int bracketOpen = text.indexOf('[');
        if (bracketOpen >= 0) {
            int depth = 0, end = -1;
            for (int i = bracketOpen; i < text.length(); i++) {
                char ch = text.charAt(i);
                if (ch == '[') depth++;
                else if (ch == ']') {
                    depth--;
                    if (depth == 0) { end = i; break; }
                }
            }
            if (end > bracketOpen) return text.substring(bracketOpen, end + 1);
        }

        return "{}";
    }

    /**
     * 生成语法练习题
     */
    public List<Map<String, Object>> generateGrammarPractices(String langCode, int level, int count) {
        String levelName = switch (level) {
            case 0 -> "初级 (Beginner)";
            case 1 -> "中级 (Intermediate)";
            default -> "高级 (Advanced)";
        };
        String prompt = String.format(
            "你是一位%s语言教师。请生成%d道%s语法练习题。\n" +
            "要求:\n" +
            "1. 题型包括填空题(fill)和纠错题(correct)。\n" +
            "2. 填空题: 给出一个带空格的句子，要求填写正确的词形。\n" +
            "3. 纠错题: 给出一个有语法错误的句子，要求写出正确形式。\n" +
            "4. 每道题包含: question(题目)、answer(答案)、hint(提示)、explanation(解析)。\n" +
            "5. 难度必须严格适合%s学习者。\n" +
            "6. 覆盖该语言的核心语法点(时态、介词、冠词、语序、变位等)。\n" +
            "输出JSON数组:\n" +
            "[{\"type\":\"fill\",\"question\":\"...\",\"answer\":\"...\",\"hint\":\"...\",\"explanation\":\"...\"},\n" +
            " {\"type\":\"correct\",\"question\":\"...\",\"answer\":\"...\",\"hint\":\"...\",\"explanation\":\"...\"}]\n" +
            "只返回JSON数组，不要其他文字。",
            getLangName(langCode), count, levelName, levelName
        );
        try {
            String reply = chatCompletion(prompt);
            String json = extractJSON(reply);
            JsonNode parsed = mapper.readTree(json);
            List<Map<String, Object>> result = new ArrayList<>();
            if (parsed.isArray()) {
                for (JsonNode node : parsed) {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("type", node.get("type").asText("fill"));
                    item.put("question", node.get("question").asText(""));
                    item.put("answer", node.get("answer").asText(""));
                    item.put("hint", node.has("hint") ? node.get("hint").asText("") : "");
                    item.put("explanation", node.has("explanation") ? node.get("explanation").asText("") : "");
                    result.add(item);
                }
            }
            return result;
        } catch (Exception e) {
            return fallbackGrammarPractices(langCode, level, count);
        }
    }

    private List<Map<String, Object>> fallbackGrammarPractices(String langCode, int level, int count) {
        List<Map<String, Object>> list = new ArrayList<>();
        String[][] templates = {
            {"fill", "She ___ (go) to school every day.", "goes", "第三人称单数", "一般现在时中，第三人称单数动词要加 -s/-es。"},
            {"correct", "He don't like apples.", "He doesn't like apples.", "否定句", "第三人称单数的否定句要用 doesn't + 动词原形。"},
            {"fill", "They ___ (be) playing football now.", "are", "be动词", "主语是复数时用 are。"},
            {"correct", "I am go to the park yesterday.", "I went to the park yesterday.", "过去时", "yesterday 表示过去，动词要用过去式。"}
        };
        for (int i = 0; i < count; i++) {
            String[] t = templates[i % templates.length];
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("type", t[0]);
            item.put("question", t[1]);
            item.put("answer", t[2]);
            item.put("hint", t[3]);
            item.put("explanation", t[4]);
            list.add(item);
        }
        return list;
    }

    private String getRecentExamples(String word) {
        String key = normalizeExample(word);
        return generatedExamples.stream()
            .filter(e -> e.contains(key))
            .limit(5)
            .reduce((a, b) -> a + "; " + b)
            .orElse("无");
    }

    private String normalizeExample(String s) {
        return s.toLowerCase().replaceAll("[^a-zA-Z0-9\\u4e00-\\u9fff\\u3040-\\u309f\\u30a0-\\u30ff\\uac00-\\ud7af]", "");
    }

    private static String getLangName(String code) { return switch (code) { case "ja" -> "日语"; case "ko" -> "韩语"; case "fr" -> "法语"; case "de" -> "德语"; default -> "英语"; }; }

    /**
     * 批量生成词汇
     * @param langCode 语言代码
     * @param level 等级名称（如"CET4"、"N2"、"B1"等）
     * @param count 生成数量
     * @param category 词汇类别（可选，如"商务"、"科技"、"日常"等）
     * @return 生成的词汇列表
     */
    public List<Map<String, Object>> generateVocabularyBatch(String langCode, String level, int count, String category) {
        String langName = getLangName(langCode);
        String categoryPart = (category != null && !category.isEmpty()) ? "，侧重" + category + "领域" : "";
        
        String prompt = String.format(
            "生成%d个%s水平的%s词汇%s。\n\n" +
            "严格按以下JSON数组格式返回，不要其他文字：\n" +
            "[{\"word\":\"单词\",\"phonetic\":\"音标\",\"partOfSpeech\":\"noun/verb/adjective等\",\"definition\":\"中文释义\",\"exampleSentence\":\"例句\",\"exampleTranslation\":\"例句翻译\"}]\n\n" +
            "要求: 词性均衡(名30%%动30%%形20%%副10%%其他10%%)，例句自然，不重复。",
            count, level, langName, categoryPart
        );
        
        try {
            String reply = chatCompletion(prompt, 6000);
            log.debug("AI 批量生成词汇原始回复: {}", reply);
            String json = extractJSON(reply);
            log.debug("提取的JSON: {}", json);
            JsonNode parsed = mapper.readTree(json);
            List<Map<String, Object>> result = new ArrayList<>();
            if (parsed.isArray()) {
                for (JsonNode node : parsed) {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("word", node.has("word") ? node.get("word").asText("") : "");
                    item.put("phonetic", node.has("phonetic") ? node.get("phonetic").asText("") : "");
                    item.put("partOfSpeech", node.has("partOfSpeech") ? node.get("partOfSpeech").asText("") : "");
                    item.put("definition", node.has("definition") ? node.get("definition").asText("") : "");
                    item.put("exampleSentence", node.has("exampleSentence") ? node.get("exampleSentence").asText("") : "");
                    item.put("exampleTranslation", node.has("exampleTranslation") ? node.get("exampleTranslation").asText("") : "");
                    result.add(item);
                }
            }
            log.info("AI 批量生成词汇: lang={}, level={}, count={}, actual={}", langCode, level, count, result.size());
            return result;
        } catch (Exception e) {
            log.error("AI 批量生成词汇失败: lang={}, level={}, error={}", langCode, level, e.getMessage(), e);
            return Collections.emptyList();
        }
    }

    /** API不可用时的本地替代 */
    // ==================== 阅读文章生成 ====================

    private static final Map<String, int[]> READING_WORD_RANGE = Map.of(
        "en", new int[]{80, 150, 200, 300, 400, 500}
    );

    public Map<String, Object> generateReadingArticle(String langCode, int level, String topic) {
        int[] ranges = READING_WORD_RANGE.getOrDefault(langCode, new int[]{120, 180, 250, 320, 400, 500});
        int min = level >= 0 && level < ranges.length ? ranges[level] : 120;
        int max = level + 1 < ranges.length ? ranges[level + 1] : min + 150;

        String levelName = switch (langCode) {
            case "en" -> switch (level) {
                case 0 -> "小学 (80-150词)";
                case 1 -> "初中 (150-200词)";
                case 2 -> "高中 (200-300词)";
                case 3 -> "CET4 (300-400词)";
                case 4 -> "CET6 (400-500词)";
                default -> "专业人士";
            };
            default -> switch (level) {
                case 0, 1 -> "初级";
                case 2, 3 -> "中级";
                default -> "高级";
            };
        };
        String topicPart = (topic != null && !topic.isEmpty()) ? "主题: \"" + topic + "\"\n" : "";

        String prompt = String.format(
            "你是一位%s语言教师。请生成一篇适合%s学习者的阅读文章，并附带核心生词表和阅读理解题。\n" +
            "%s" +
            "要求:\n" +
            "1. 文章长度严格控制在%d-%d词之间。\n" +
            "2. 内容自然流畅，有教育意义，段落之间用空行隔开。\n" +
            "3. 附带5个核心生词（含音标和中文释义）。\n" +
            "4. 附带5道阅读理解选择题（4选项，正确答案随机分布在A/B/C/D中，标注正确答案索引0-3）。\n" +
            "输出JSON格式: {\"title\":\"...\",\"content\":\"...\",\"wordCount\":数字,\"level\":\"...\",\"coreVocabulary\":[{\"word\":\"...\",\"phonetic\":\"...\",\"definition\":\"...\"}],\"quizQuestions\":[{\"question\":\"...\",\"options\":[\"A\",\"B\",\"C\",\"D\"],\"answer\":0,\"explanation\":\"...\"}]}\n" +
            "只返回JSON对象，不要其他文字。",
            getLangName(langCode), levelName, topicPart, min, max
        );
        try {
            String reply = chatCompletion(prompt, 3000);
            String json = extractJSON(reply);
            JsonNode parsed = mapper.readTree(json);
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("title", parsed.get("title").asText(""));
            result.put("content", parsed.get("content").asText(""));
            result.put("wordCount", parsed.has("wordCount") ? parsed.get("wordCount").asInt() : 0);
            result.put("level", parsed.get("level").asText(""));
            result.put("coreVocabulary", mapper.convertValue(parsed.get("coreVocabulary"), new TypeReference<List<Map<String, Object>>>() {}));
            result.put("quizQuestions", mapper.convertValue(parsed.get("quizQuestions"), new TypeReference<List<Map<String, Object>>>() {}));
            result.put("aiGenerated", true);
            return result;
        } catch (Exception e) {
            log.error("AI 生成阅读文章失败: {}", e.getMessage(), e);
            Map<String, Object> fallback = new LinkedHashMap<>();
            fallback.put("title", "Reading Practice");
            fallback.put("content", "This is a sample reading passage. Please try again later.");
            fallback.put("wordCount", 8);
            fallback.put("level", "Intermediate");
            fallback.put("coreVocabulary", List.of());
            fallback.put("quizQuestions", List.of());
            fallback.put("aiGenerated", true);
            return fallback;
        }
    }

    private Map<String, Object> fallbackIPlusOne(List<String> known, List<String> news) {
        List<Map<String, String>> sentences = new ArrayList<>();
        String[] nwArr = news.isEmpty() ? new String[]{"beautiful", "important", "experience"} : news.toArray(new String[0]);
        for (String nw : nwArr) {
            sentences.add(Map.of("sentence", "I think learning languages is very " + nw + ".", "newWords", nw, "translation", "我觉得学习语言非常" + nw + "。"));
        }
        return Map.of("sentences", sentences, "raw", "local fallback");
    }

    // ==================== 写作题目生成 ====================

    /**
     * 生成写作题目
     */
    public Map<String, Object> generateWritingPrompt(String langCode, int level, String topic) {
        String levelName = switch (level) {
            case 1 -> "初级 (仿写)";
            case 2 -> "中级 (连词成句)";
            default -> "高级 (自由写作)";
        };
        String topicPart = (topic != null && !topic.isEmpty())
            ? "主题: \"" + topic + "\"\n"
            : "请自行选择一个适合" + levelName + "学习者的主题。\n";

        String prompt = String.format(
            "你是一位%s语言教师。请为%s学习者出一道写作题。\n" +
            "%s" +
            "要求:\n" +
            "1. 如果是仿写(level=1): 给出一句范例模板，列出替换部分，给出2-3个必用词汇。\n" +
            "2. 如果是连词成句(level=2): 给出3句打乱顺序的句子，让学生排列。\n" +
            "3. 如果是自由写作(level=3): 给出作文主题，字数限制80词，2-3个必用词汇。\n" +
            "输出JSON格式:\n" +
            "level=1: {\"type\":\"仿写\",\"template\":\"...\",\"instruction\":\"替换划线部分...\",\"requiredWords\":[\"word1\",\"word2\"]}\n" +
            "level=2: {\"type\":\"连词成句\",\"sentences\":[\"sentence1\",\"sentence2\",\"sentence3\"],\"instruction\":\"将句子按正确顺序排列\"}\n" +
            "level=3: {\"type\":\"自由写作\",\"topic\":\"...\",\"wordLimit\":80,\"requiredWords\":[\"word1\"],\"instruction\":\"用50-80词...\"}\n" +
            "所有type字段用中文。只返回JSON对象，不要其他文字。",
            getLangName(langCode), levelName, topicPart
        );
        try {
            String reply = chatCompletion(prompt, 2000);
            String json = extractJSON(reply);
            log.info("AI 写作出题原始回复: {}", reply.substring(0, Math.min(300, reply.length())));
            log.info("AI 写作出题提取JSON: {}", json);
            JsonNode parsed = mapper.readTree(json);
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("level", level);
            result.put("langCode", langCode);
            result.put("type", parsed.has("type") ? parsed.get("type").asText() : "自由写作");
            result.put("instruction", parsed.has("instruction") ? parsed.get("instruction").asText() : "");
            if (parsed.has("template")) result.put("template", parsed.get("template").asText());
            if (parsed.has("topic")) result.put("topic", parsed.get("topic").asText());
            if (parsed.has("sentences")) result.put("sentences", mapper.convertValue(parsed.get("sentences"), new TypeReference<List<Object>>() {}));
            if (parsed.has("requiredWords")) result.put("requiredWords", mapper.convertValue(parsed.get("requiredWords"), new TypeReference<List<String>>() {}));
            if (parsed.has("wordLimit")) result.put("wordLimit", parsed.get("wordLimit").asInt());
            result.put("aiGenerated", true);
            return result;
        } catch (Exception e) {
            log.error("AI 写作出题解析失败，启用 fallback: {}", e.getMessage(), e);
            return fallbackWritingPrompt(langCode, level, topic);
        }
    }

    private Map<String, Object> fallbackWritingPrompt(String langCode, int level, String topic) {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("level", level);
        p.put("langCode", langCode);
        p.put("aiGenerated", true);
        switch (level) {
            case 1 -> {
                p.put("type", "仿写");
                p.put("template", "I usually go to school by bus, but yesterday I went by bike because the weather was nice.");
                p.put("instruction", "请替换划线部分，保持结构写你自己的句子");
                p.put("requiredWords", List.of("usually", "because"));
            }
            case 2 -> {
                p.put("type", "连词成句");
                p.put("sentences", List.of("I woke up late this morning.", "Therefore I missed the bus.", "Luckily my friend gave me a ride."));
                p.put("instruction", "将以上句子按正确顺序排列");
            }
            default -> {
                p.put("type", "自由写作");
                p.put("topic", topic != null && !topic.isEmpty() ? topic : "Describe your favorite season and why.");
                p.put("wordLimit", 80);
                p.put("requiredWords", List.of("favorite", "because"));
                p.put("instruction", "用50-80词围绕主题写作");
            }
        }
        return p;
    }

    /**
     * 修复乱码词汇：根据单词和语言重新生成音标、释义、例句、翻译
     * @return map 包含 phonetic, definition, example, translation
     */
    public Map<String, String> repairVocabulary(String word, String langCode, String partOfSpeech) {
        String posHint = (partOfSpeech != null && !partOfSpeech.isEmpty()) ? "，词性是" + partOfSpeech : "";
        String prompt = String.format(
            "你是一位%s语言专家。请为以下单词生成准确的词汇信息，用JSON返回：\n" +
            "单词: %s\n" +
            "要求：\n" +
            "1. phonetic: 国际音标(IPA)，如 /həˈloʊ/\n" +
            "2. definition: 简洁的中文释义（10字以内）\n" +
            "3. example: 一个自然例句（10-20词）\n" +
            "4. translation: 该例句的中文翻译\n" +
            "格式: {\"phonetic\":\"...\",\"definition\":\"...\",\"example\":\"...\",\"translation\":\"...\"}\n" +
            "只返回JSON，不要其他文字。",
            getLangName(langCode), word + posHint
        );
        try {
            String reply = chatCompletion(prompt, 600);
            String json = extractJSON(reply);
            JsonNode parsed = mapper.readTree(json);
            Map<String, String> result = new LinkedHashMap<>();
            result.put("phonetic", parsed.has("phonetic") ? parsed.get("phonetic").asText() : "");
            result.put("definition", parsed.has("definition") ? parsed.get("definition").asText() : "");
            result.put("example", parsed.has("example") ? parsed.get("example").asText() : "");
            result.put("translation", parsed.has("translation") ? parsed.get("translation").asText() : "");
            return result;
        } catch (Exception e) {
            log.error("修复词汇失败: word={}, lang={}, error={}", word, langCode, e.getMessage());
            return null;
        }
    }
}
