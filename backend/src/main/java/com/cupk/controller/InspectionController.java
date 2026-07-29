package com.cupk.controller;

import com.cupk.common.Result;
import com.cupk.service.InspectionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/inspection")
public class InspectionController {

    private static final Logger log = LoggerFactory.getLogger(InspectionController.class);

    @Autowired
    private InspectionService inspectionService;

    /**
     * 检查是否需要巡检
     * GET /inspection/check?userId=1
     */
    @GetMapping("/check")
    public Result<Map<String, Object>> checkInspection(
            @RequestParam Long userId,
            @RequestParam(defaultValue = "") String langCode) {
        boolean needed = inspectionService.needsInspection(userId);
        Map<String, Object> result = new java.util.HashMap<>();
        result.put("needsInspection", needed);
        if (needed) {
            List<Map<String, Object>> questions = inspectionService.generateInspectionQuestions(userId, langCode);
            result.put("questions", questions);
        }
        return Result.success(result);
    }

    /**
     * 提交巡检结果
     * POST /inspection/submit
     * Body: { userId: 1, results: [{ vocabId: 1, correct: true }, ...] }
     */
    @PostMapping("/submit")
    public Result<Void> submitInspection(@RequestBody Map<String, Object> body) {
        Long userId = Long.valueOf(body.get("userId").toString());
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> results = (List<Map<String, Object>>) body.get("results");
        if (results != null) {
            for (Map<String, Object> r : results) {
                Long vocabId = Long.valueOf(r.get("vocabId").toString());
                Boolean correct = (Boolean) r.get("correct");
                inspectionService.submitInspectionResult(userId, vocabId, correct != null && correct);
            }
        }
        return Result.success("巡检结果已记录");
    }
}
