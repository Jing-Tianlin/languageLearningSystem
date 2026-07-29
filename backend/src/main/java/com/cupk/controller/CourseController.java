package com.cupk.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cupk.common.Result;
import com.cupk.mapper.CourseMapper;
import com.cupk.pojo.Course;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/course")
@RequiredArgsConstructor
public class CourseController {
    private static final Logger log = LoggerFactory.getLogger(CourseController.class);
    private final CourseMapper courseMapper;

    @GetMapping("/courses")
    public Result<Page<Course>> selectPages(@RequestParam(defaultValue = "") String title,
                                            @RequestParam(defaultValue = "") String langCode,
                                            @RequestParam(defaultValue = "") String level,
                                            @RequestParam(defaultValue = "1") Integer pageNo,
                                            @RequestParam(defaultValue = "5") Integer pageSize) {
        Page<Course> page = new Page<>(pageNo, pageSize);
        QueryWrapper<Course> queryWrapper = new QueryWrapper<>();
        if (!title.isEmpty()) {
            queryWrapper.like("title", title);
        }
        if (!langCode.isEmpty()) {
            queryWrapper.eq("lang_code", langCode);
        }
        if (!level.isEmpty()) {
            queryWrapper.eq("level", level);
        }
        queryWrapper.orderByDesc("create_time");
        courseMapper.selectPage(page, queryWrapper);
        return Result.success(page);
    }

    @GetMapping("/courses/{id}")
    public Result<Course> selectById(@PathVariable Long id) {
        Course course = courseMapper.selectById(id);
        if (course == null) {
            return Result.error(404, "课程不存在");
        }
        return Result.success(course);
    }

    @PostMapping("/courses")
    public Result<Void> insert(@RequestBody Course course) {
        course.setId(null);
        int rows = courseMapper.insert(course);
        if (rows > 0) {
            return Result.success("新增成功");
        }
        return Result.error("新增失败");
    }

    @PutMapping("/courses")
    public Result<Void> update(@RequestBody Course course) {
        int rows = courseMapper.updateById(course);
        if (rows > 0) {
            return Result.success("修改成功");
        }
        return Result.error("修改失败");
    }

    @DeleteMapping("/courses/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        int rows = courseMapper.deleteById(id);
        if (rows > 0) {
            return Result.success("删除成功");
        }
        return Result.error("删除失败");
    }
}
