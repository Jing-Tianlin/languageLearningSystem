package com.cupk.controller;

import com.cupk.common.Result;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/sentences")
public class LongSentenceController {

    private static final Logger log = LoggerFactory.getLogger(LongSentenceController.class);

    @Autowired
    private JdbcTemplate jdbcTemplate;

    /**
     * 获取某语言的长难句列表
     * GET /sentences/list?langCode=en&level=Advanced&limit=20
     */
    @GetMapping("/list")
    public Result<List<Map<String, Object>>> list(
            @RequestParam(defaultValue = "en") String langCode,
            @RequestParam(defaultValue = "") String level,
            @RequestParam(defaultValue = "20") int limit) {

        StringBuilder sql = new StringBuilder(
            "SELECT id, lang_code, sentence, translation, grammar_points, analysis, level, source " +
            "FROM long_sentences WHERE lang_code = ?");
        Object[] params;
        if (!level.isEmpty()) {
            sql.append(" AND level = ? ORDER BY id ASC LIMIT ?");
            params = new Object[]{langCode, level, limit};
        } else {
            sql.append(" ORDER BY id ASC LIMIT ?");
            params = new Object[]{langCode, limit};
        }
        List<Map<String, Object>> list = jdbcTemplate.queryForList(sql.toString(), params);
        return Result.success(list);
    }

    /**
     * 获取单个长难句详情
     * GET /sentences/1
     */
    @GetMapping("/{id}")
    public Result<Map<String, Object>> getById(@PathVariable Long id) {
        Map<String, Object> sentence = jdbcTemplate.queryForMap(
            "SELECT id, lang_code, sentence, translation, grammar_points, analysis, level, source " +
            "FROM long_sentences WHERE id = ?", id);
        return Result.success(sentence);
    }

    /**
     * 随机获取一句 (每日一句)
     * GET /sentences/daily?langCode=en
     */
    @GetMapping("/daily")
    public Result<Map<String, Object>> daily(@RequestParam(defaultValue = "en") String langCode) {
        List<Map<String, Object>> list = jdbcTemplate.queryForList(
            "SELECT id, lang_code, sentence, translation, grammar_points, analysis, level, source " +
            "FROM long_sentences WHERE lang_code = ? ORDER BY RAND() LIMIT 1", langCode);
        if (list.isEmpty()) {
            return Result.error(404, "该语言暂无长难句数据");
        }
        return Result.success(list.get(0));
    }
}
