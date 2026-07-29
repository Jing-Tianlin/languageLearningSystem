package com.cupk.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cupk.common.Result;
import com.cupk.mapper.LanguageMapper;
import com.cupk.pojo.Language;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/language")
public class LanguageController {
    private static final Logger log = LoggerFactory.getLogger(LanguageController.class);
    @Autowired
    private LanguageMapper languageMapper;

    @GetMapping("/languages")
    public Result<Page<Language>> selectPages(@RequestParam(defaultValue = "") String nameCn,
                                              @RequestParam(defaultValue = "1") Integer pageNo,
                                              @RequestParam(defaultValue = "5") Integer pageSize) {
        Page<Language> page = new Page<>(pageNo, pageSize);
        QueryWrapper<Language> queryWrapper = new QueryWrapper<>();
        if (!nameCn.isEmpty()) {
            queryWrapper.like("name_cn", nameCn);
        }
        queryWrapper.orderByAsc("code");
        languageMapper.selectPage(page, queryWrapper);
        return Result.success(page);
    }

    @GetMapping("/languages/{id}")
    public Result<Language> selectById(@PathVariable Long id) {
        Language language = languageMapper.selectById(id);
        if (language == null) {
            return Result.error(404, "语言不存在");
        }
        return Result.success(language);
    }

    @PostMapping("/languages")
    public Result<Void> insert(@RequestBody Language language) {
        language.setId(null);
        int rows = languageMapper.insert(language);
        if (rows > 0) {
            return Result.success("新增成功");
        }
        return Result.error("新增失败");
    }

    @PutMapping("/languages")
    public Result<Void> update(@RequestBody Language language) {
        int rows = languageMapper.updateById(language);
        if (rows > 0) {
            return Result.success("修改成功");
        }
        return Result.error("修改失败");
    }

    @DeleteMapping("/languages/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        int rows = languageMapper.deleteById(id);
        if (rows > 0) {
            return Result.success("删除成功");
        }
        return Result.error("删除失败");
    }
}
