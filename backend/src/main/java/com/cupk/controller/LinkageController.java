package com.cupk.controller;

import com.cupk.common.Result;
import com.cupk.service.HotWordsPoolService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/link")
@RequiredArgsConstructor
public class LinkageController {

    private static final Logger log = LoggerFactory.getLogger(LinkageController.class);

    private final HotWordsPoolService hotWordsService;

    /** 获取热点词库 */
    @GetMapping("/hot-words")
    public Result<List<Map<String, Object>>> getHotWords(
            @RequestParam Long userId,
            @RequestParam(defaultValue = "50") int limit) {
        return Result.success(hotWordsService.getActiveVocab(userId, limit));
    }

    /** 获取用户语法薄弱点 */
    @GetMapping("/weak-grammar")
    public Result<List<String>> getWeakGrammar(@RequestParam Long userId) {
        return Result.success(hotWordsService.getWeakGrammarRules(userId));
    }

    /** 检测词性短缺 */
    @GetMapping("/pos-shortage")
    public Result<List<String>> getPosShortage(@RequestParam Long userId) {
        return Result.success(hotWordsService.detectPosShortage(userId));
    }

    /** 生成语法题 (挂接热点词) */
    @PostMapping("/grammar-with-hot-words")
    public Result<Map<String, Object>> generateGrammar(@RequestBody Map<String, Object> body) {
        Object userIdRaw = body.get("userId");
        if (userIdRaw == null) {
            return Result.error(400, "userId不能为空");
        }
        Long userId = Long.valueOf(userIdRaw.toString());
        String template = (String) body.getOrDefault("template", "[SUBJECT] ___ [VERB] to [PLACE] yesterday.");
        String answerSlot = (String) body.getOrDefault("answerSlot", "went");
        Map<String, Object> result = hotWordsService.generateGrammarWithHotWords(userId, template, answerSlot);
        return Result.success(result);
    }
}
