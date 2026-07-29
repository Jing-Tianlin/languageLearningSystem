package com.cupk.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * GrammarLessonService — 语法教程查询服务
 */
@Service
public class GrammarLessonService {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    /**
     * 获取某语言的全部语法教程（包含章节）
     */
    public List<Map<String, Object>> getLessonsWithSections(String langCode) {
        List<Map<String, Object>> lessons = jdbcTemplate.queryForList(
            "SELECT id, title, sort_order, video_url FROM grammar_lessons WHERE lang_code = ? ORDER BY sort_order",
            langCode);

        for (Map<String, Object> lesson : lessons) {
            Long lessonId = ((Number) lesson.get("id")).longValue();
            List<Map<String, Object>> sections = jdbcTemplate.queryForList(
                "SELECT subtitle, content FROM grammar_lesson_sections WHERE lesson_id = ? ORDER BY sort_order",
                lessonId);
            lesson.put("sections", sections);
        }
        return lessons;
    }
}
