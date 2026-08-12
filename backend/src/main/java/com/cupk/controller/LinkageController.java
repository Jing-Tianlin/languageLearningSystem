package com.cupk.controller;

import com.cupk.common.Result;
import com.cupk.service.HotWordsPoolService;
import com.cupk.util.AuthUtil;
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
            @RequestParam(required = false) Long userId,
            @RequestParam(defaultValue = "50") int limit) {
        Long currentUserId = AuthUtil.getCurrentUserId();
        if (currentUserId == null) return Result.error(401, "未登录");
        return Result.success(hotWordsService.getActiveVocab(currentUserId, limit));
    }

    /** 获取用户语法薄弱点 */
    @GetMapping("/weak-grammar")
    public Result<List<String>> getWeakGrammar(@RequestParam(required = false) Long userId) {
        Long currentUserId = AuthUtil.getCurrentUserId();
        if (currentUserId == null) return Result.error(401, "未登录");
        return Result.success(hotWordsService.getWeakGrammarRules(currentUserId));
    }

    /** 检测词性短缺 */
    @GetMapping("/pos-shortage")
    public Result<List<String>> getPosShortage(@RequestParam(required = false) Long userId) {
        Long currentUserId = AuthUtil.getCurrentUserId();
        if (currentUserId == null) return Result.error(401, "未登录");
        return Result.success(hotWordsService.detectPosShortage(currentUserId));
    }

    /** 生成语法题 (挂接热点词) */
    @PostMapping("/grammar-with-hot-words")
    public Result<Map<String, Object>> generateGrammar(@RequestBody Map<String, Object> body) {
        // userId 强制取 token，忽略前端传入
        Long userId = AuthUtil.getCurrentUserId();
        if (userId == null) return Result.error(401, "未登录");
        String template = (String) body.getOrDefault("template", "[SUBJECT] ___ [VERB] to [PLACE] yesterday.");
        String answerSlot = (String) body.getOrDefault("answerSlot", "went");
        Map<String, Object> result = hotWordsService.generateGrammarWithHotWords(userId, template, answerSlot);
        return Result.success(result);
    }
}
