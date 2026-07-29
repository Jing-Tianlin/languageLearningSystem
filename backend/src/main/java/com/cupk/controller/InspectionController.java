package com.cupk.controller;

import com.cupk.common.Result;
import com.cupk.service.InspectionService;
import com.cupk.util.AuthUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/inspection")
@RequiredArgsConstructor
public class InspectionController {

    private static final Logger log = LoggerFactory.getLogger(InspectionController.class);

    private final InspectionService inspectionService;

    /**
     * 检查是否需要巡检
     * GET /inspection/check?userId=1
     */
    @GetMapping("/check")
    public Result<Map<String, Object>> checkInspection(
            @RequestParam(required = false) Long userId,
            @RequestParam(defaultValue = "") String langCode) {
        Long currentUserId = AuthUtil.getCurrentUserId();
        if (currentUserId == null) return Result.error(401, "未登录");
        boolean needed = inspectionService.needsInspection(currentUserId);
        Map<String, Object> result = new java.util.HashMap<>();
        result.put("needsInspection", needed);
        if (needed) {
            List<Map<String, Object>> questions = inspectionService.generateInspectionQuestions(currentUserId, langCode);
            result.put("questions", questions);
        }
        return Result.success(result);
    }

    /**
     * 提交巡检结果
     * POST /inspection/submit
     * Body: { results: [{ vocabId: 1, correct: true }, ...] }
     */
    @PostMapping("/submit")
    public Result<Void> submitInspection(@RequestBody Map<String, Object> body) {
        Long userId = AuthUtil.getCurrentUserId();
        if (userId == null) return Result.error(401, "未登录");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> results = (List<Map<String, Object>>) body.get("results");
        if (results != null) {
            for (Map<String, Object> r : results) {
                Object vocabIdRaw = r.get("vocabId");
                if (vocabIdRaw == null) continue;
                Long vocabId = Long.valueOf(vocabIdRaw.toString());
                Boolean correct = (Boolean) r.get("correct");
                inspectionService.submitInspectionResult(userId, vocabId, correct != null && correct);
            }
        }
        return Result.success("巡检结果已记录");
    }
}
