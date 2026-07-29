package com.cupk.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cupk.common.Result;
import com.cupk.mapper.FavoriteMapper;
import com.cupk.pojo.Favorite;
import com.cupk.util.AuthUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.web.bind.annotation.*;

import lombok.RequiredArgsConstructor;
import java.util.List;

@RestController
@RequestMapping("/favorite")
@RequiredArgsConstructor
public class FavoriteController {

    private static final Logger log = LoggerFactory.getLogger(FavoriteController.class);

    private final FavoriteMapper favoriteMapper;

    @GetMapping("/favorites")
    public Result<Page<Favorite>> selectPages(@RequestParam(required = false) Long userId,
                                              @RequestParam(defaultValue = "") String langCode,
                                              @RequestParam(defaultValue = "1") Integer pageNo,
                                              @RequestParam(defaultValue = "500") Integer pageSize) {
        // 只能查询自己的收藏，userId 强制取 token
        Long currentUserId = AuthUtil.getCurrentUserId();
        Page<Favorite> page = new Page<>(pageNo, pageSize);
        QueryWrapper<Favorite> queryWrapper = new QueryWrapper<>();
        if (currentUserId != null) {
            queryWrapper.eq("user_id", currentUserId);
        }
        if (!langCode.isEmpty()) {
            queryWrapper.eq("lang_code", langCode);
        }
        queryWrapper.orderByDesc("create_time");
        favoriteMapper.selectPage(page, queryWrapper);
        return Result.success(page);
    }

    @GetMapping("/favorites/{id}")
    public Result<Favorite> selectById(@PathVariable Long id) {
        Favorite favorite = favoriteMapper.selectById(id);
        if (favorite == null) {
            return Result.error(404, "收藏不存在");
        }
        // 校验收藏归属
        Long currentUserId = AuthUtil.getCurrentUserId();
        if (currentUserId == null || !currentUserId.equals(favorite.getUserId())) {
            return Result.error(403, "无权访问该收藏");
        }
        return Result.success(favorite);
    }

    /**
     * 收藏单词（幂等：重复收藏返回成功）
     */
    @PostMapping("/favorites")
    public Result<Void> insert(@RequestBody Favorite favorite) {
        Long currentUserId = AuthUtil.getCurrentUserId();
        if (currentUserId == null) {
            return Result.error(401, "未登录");
        }
        // 归属强制取 token，防止替他人收藏
        favorite.setUserId(currentUserId);
        // 幂等查询
        QueryWrapper<Favorite> qw = new QueryWrapper<>();
        qw.eq("user_id", favorite.getUserId())
          .eq("vocab_id", favorite.getVocabId());
        if (favoriteMapper.selectCount(qw) > 0) {
            return Result.success("已收藏");
        }

        try {
            favoriteMapper.insert(favorite);
            return Result.success("收藏成功");
        } catch (DuplicateKeyException e) {
            return Result.success("已收藏");
        }
    }

    /**
     * 取消收藏（按 vocabId + userId）
     */
    @DeleteMapping("/favorites/by-vocab")
    public Result<Void> deleteByVocab(@RequestParam(required = false) Long userId, @RequestParam Long vocabId) {
        Long currentUserId = AuthUtil.getCurrentUserId();
        if (currentUserId == null) {
            return Result.error(401, "未登录");
        }
        QueryWrapper<Favorite> qw = new QueryWrapper<>();
        qw.eq("user_id", currentUserId).eq("vocab_id", vocabId);
        int rows = favoriteMapper.delete(qw);
        if (rows > 0) {
            return Result.success("取消收藏成功");
        }
        return Result.error("收藏不存在");
    }

    @DeleteMapping("/favorites/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        Favorite favorite = favoriteMapper.selectById(id);
        if (favorite == null) {
            return Result.error(404, "收藏不存在");
        }
        // 只能删除自己的收藏
        Long currentUserId = AuthUtil.getCurrentUserId();
        if (currentUserId == null || !currentUserId.equals(favorite.getUserId())) {
            return Result.error(403, "无权删除他人收藏");
        }
        int rows = favoriteMapper.deleteById(id);
        if (rows > 0) {
            return Result.success("取消收藏成功");
        }
        return Result.error("取消收藏失败");
    }

    /**
     * 批量收藏
     */
    @PostMapping("/favorites/batch")
    public Result<Void> batchInsert(@RequestBody List<Favorite> list) {
        Long currentUserId = AuthUtil.getCurrentUserId();
        if (currentUserId == null) {
            return Result.error(401, "未登录");
        }
        for (Favorite f : list) {
            // 归属强制取 token
            f.setUserId(currentUserId);
            QueryWrapper<Favorite> qw = new QueryWrapper<>();
            qw.eq("user_id", f.getUserId()).eq("vocab_id", f.getVocabId());
            if (favoriteMapper.selectCount(qw) == 0) {
                try {
                    favoriteMapper.insert(f);
                } catch (DuplicateKeyException e) {
                    log.debug("重复收藏 userId={}, vocabId={}", f.getUserId(), f.getVocabId());
                }
            }
        }
        return Result.success("批量收藏完成");
    }
}
