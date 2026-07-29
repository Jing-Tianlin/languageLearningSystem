package com.cupk.controller;

import com.cupk.common.Result;
import com.cupk.pojo.UserProfile;
import com.cupk.mapper.UserProfileMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * ProfileController — 用户扩展资料接口（一对一）
 */
@RestController
@RequestMapping("/profile")
public class ProfileController {

    private static final Logger log = LoggerFactory.getLogger(ProfileController.class);

    @Autowired
    private UserProfileMapper userProfileMapper;

    @GetMapping("/{userId}")
    public Result<UserProfile> getProfile(@PathVariable Long userId) {
        UserProfile p = userProfileMapper.selectById(userId);
        if (p == null) return Result.error(404, "未设置扩展资料");
        return Result.success(p);
    }

    @PostMapping
    public Result<Void> saveOrUpdate(@RequestBody Map<String, Object> body) {
        Long userId = Long.valueOf(body.get("userId").toString());
        UserProfile p = userProfileMapper.selectById(userId);
        if (p == null) {
            p = new UserProfile();
            p.setUserId(userId);
        }
        if (body.containsKey("bio")) p.setBio((String) body.get("bio"));
        if (body.containsKey("education")) p.setEducation((String) body.get("education"));
        if (body.containsKey("occupation")) p.setOccupation((String) body.get("occupation"));
        if (body.containsKey("totalQuizCount")) p.setTotalQuizCount(Integer.valueOf(body.get("totalQuizCount").toString()));
        if (body.containsKey("totalQuizCorrect")) p.setTotalQuizCorrect(Integer.valueOf(body.get("totalQuizCorrect").toString()));
        if (p.getId() == null) userProfileMapper.insert(p);
        else userProfileMapper.updateById(p);
        return Result.success("保存成功");
    }
}
