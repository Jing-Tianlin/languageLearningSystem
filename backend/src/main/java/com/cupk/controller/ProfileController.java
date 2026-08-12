package com.cupk.controller;

import com.cupk.common.Result;
import com.cupk.pojo.UserProfile;
import com.cupk.mapper.UserProfileMapper;
import com.cupk.util.AuthUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * ProfileController — 用户扩展资料接口（一对一）
 */
@RestController
@RequestMapping("/profile")
@RequiredArgsConstructor
public class ProfileController {

    private static final Logger log = LoggerFactory.getLogger(ProfileController.class);

    private final UserProfileMapper userProfileMapper;

    @GetMapping("/{userId}")
    public Result<UserProfile> getProfile(@PathVariable Long userId) {
        // 只能查看自己的扩展资料，userId 强制取 token
        Long currentUserId = AuthUtil.getCurrentUserId();
        if (currentUserId == null || !currentUserId.equals(userId)) {
            return Result.error(403, "无权访问其他用户资料");
        }
        UserProfile p = userProfileMapper.selectById(userId);
        if (p == null) return Result.error(404, "未设置扩展资料");
        return Result.success(p);
    }

    @PostMapping
    public Result<Void> saveOrUpdate(@RequestBody Map<String, Object> body) {
        // userId 强制取 token，忽略前端传入，防止越权修改他人资料
        Long currentUserId = AuthUtil.getCurrentUserId();
        if (currentUserId == null) return Result.error(401, "未登录");
        UserProfile p = userProfileMapper.selectById(currentUserId);
        if (p == null) {
            p = new UserProfile();
            p.setUserId(currentUserId);
        }
        if (body.containsKey("bio")) p.setBio((String) body.get("bio"));
        if (body.containsKey("education")) p.setEducation((String) body.get("education"));
        if (body.containsKey("occupation")) p.setOccupation((String) body.get("occupation"));
        if (body.containsKey("totalQuizCount")) {
            Object val = body.get("totalQuizCount");
            if (val != null) p.setTotalQuizCount(Integer.valueOf(val.toString()));
        }
        if (body.containsKey("totalQuizCorrect")) {
            Object val = body.get("totalQuizCorrect");
            if (val != null) p.setTotalQuizCorrect(Integer.valueOf(val.toString()));
        }
        if (p.getId() == null) userProfileMapper.insert(p);
        else userProfileMapper.updateById(p);
        return Result.success("保存成功");
    }
}
