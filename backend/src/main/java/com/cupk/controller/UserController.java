package com.cupk.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cupk.common.Result;
import com.cupk.mapper.LoginLogMapper;
import com.cupk.mapper.UserMapper;
import com.cupk.pojo.LoginLog;
import com.cupk.pojo.User;
import com.cupk.utils.PasswordUtil;
import com.cupk.util.JwtUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/user")
@RequiredArgsConstructor
public class UserController {

    private static final Logger log = LoggerFactory.getLogger(UserController.class);
    private final UserMapper userMapper;

    private final JdbcTemplate jdbcTemplate;

    private final LoginLogMapper loginLogMapper;

    private final JwtUtil jwtUtil;

    @GetMapping("/users")
    public Result<Page<User>> selectPages(@RequestParam(defaultValue = "") String username,
                                          @RequestParam(defaultValue = "") String nickname,
                                          @RequestParam(defaultValue = "1") Integer pageNo,
                                          @RequestParam(defaultValue = "5") Integer pageSize) {
        Page<User> page = new Page<>(pageNo, pageSize);
        QueryWrapper<User> queryWrapper = new QueryWrapper<>();
        if (!username.isEmpty()) {
            queryWrapper.like("username", username);
        }
        if (!nickname.isEmpty()) {
            queryWrapper.like("nickname", nickname);
        }
        queryWrapper.orderByDesc("create_time");
        userMapper.selectPage(page, queryWrapper);
        // 为每个用户查询角色
        for (User u : page.getRecords()) {
            List<String> roleCodes = jdbcTemplate.query(
                "SELECT r.code FROM role r JOIN user_role ur ON r.id = ur.role_id WHERE ur.user_id = ?",
                (rs, rowNum) -> rs.getString("code"),
                u.getId()
            );
            u.setRoles(roleCodes);
        }
        return Result.success(page);
    }

    @GetMapping("/users/{id}")
    public Result<User> selectById(@PathVariable Long id) {
        User user = userMapper.selectById(id);
        if (user == null) {
            return Result.error(404, "用户不存在");
        }
        return Result.success(user);
    }

    @PostMapping("/users")
    public Result<User> insert(@RequestBody User user) {
        user.setId(null);
        int rows = userMapper.insert(user);
        if (rows > 0) {
            return Result.success(user);
        }
        return Result.error("新增失败");
    }

    @PutMapping("/users")
    public Result<Void> update(@RequestBody User user) {
        int rows = userMapper.updateById(user);
        if (rows > 0) {
            return Result.success("修改成功");
        }
        return Result.error("修改失败");
    }

    @PutMapping("/preferences")
    public Result<Void> updatePreferences(@RequestParam Long userId,
                                          @RequestParam(required = false) String langCode,
                                          @RequestParam(required = false) String level) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            return Result.error(404, "用户不存在");
        }
        if (langCode != null && !langCode.isEmpty()) {
            user.setCurrentLangCode(langCode);
        }
        if (level != null && !level.isEmpty()) {
            user.setCurrentLevel(level);
        }
        userMapper.updateById(user);
        return Result.success("设置已保存");
    }

    @DeleteMapping("/users/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        jdbcTemplate.update("DELETE FROM favorites WHERE user_id = ?", id);
        jdbcTemplate.update("DELETE FROM user_progress WHERE user_id = ?", id);
        jdbcTemplate.update("DELETE FROM ai_chat_history WHERE user_id = ?", id);
        jdbcTemplate.update("DELETE FROM writing_history WHERE user_id = ?", id);
        jdbcTemplate.update("DELETE FROM reading_history WHERE user_id = ?", id);
        jdbcTemplate.update("DELETE FROM user_role WHERE user_id = ?", id);
        jdbcTemplate.update("DELETE FROM user_profile WHERE user_id = ?", id);
        jdbcTemplate.update("DELETE FROM login_log WHERE user_id = ?", id);
        jdbcTemplate.update("DELETE FROM operation_log WHERE user_id = ?", id);
        jdbcTemplate.update("DELETE FROM inspection_log WHERE user_id = ?", id);
        jdbcTemplate.update("DELETE FROM user WHERE id = ?", id);
        return Result.success("删除成功");
    }

    @PostMapping("/login")
    public Result<Map<String, Object>> login(@RequestBody Map<String, String> params) {
        String username = params.get("username");
        String password = params.get("password");
        if (username == null || username.isEmpty() || password == null || password.isEmpty()) {
            return Result.error(400, "用户名和密码不能为空");
        }
        QueryWrapper<User> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("username", username);
        User user = userMapper.selectOne(queryWrapper);
        if (user == null) {
            return Result.error(401, "用户名或密码错误");
        }
        if (!PasswordUtil.matches(password, user.getPassword())) {
            return Result.error(401, "用户名或密码错误");
        }
        List<String> roleCodes = jdbcTemplate.query(
            "SELECT r.code FROM role r JOIN user_role ur ON r.id = ur.role_id WHERE ur.user_id = ?",
            (rs, rowNum) -> rs.getString("code"),
            user.getId()
        );
        user.setRoles(roleCodes);
        user.setPassword(null);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("user", user);
        result.put("token", jwtUtil.generateToken(user.getId()));
        recordLoginLog(user.getId(), username, true);
        return Result.success(result);
    }

    @PostMapping("/register")
    public Result<Map<String, Object>> register(@RequestBody User user) {
        if (user.getUsername() == null || user.getUsername().isEmpty()) {
            return Result.error(400, "用户名不能为空");
        }
        if (user.getPassword() == null || user.getPassword().isEmpty()) {
            return Result.error(400, "密码不能为空");
        }
        QueryWrapper<User> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("username", user.getUsername());
        User exist = userMapper.selectOne(queryWrapper);
        if (exist != null) {
            return Result.error(400, "用户名已存在");
        }
        user.setId(null);
        user.setPassword(PasswordUtil.encode(user.getPassword()));
        if (user.getNickname() == null || user.getNickname().isEmpty()) {
            user.setNickname(user.getUsername());
        }
        int rows = userMapper.insert(user);
        if (rows > 0) {
            jdbcTemplate.update("INSERT INTO user_role (user_id, role_id) VALUES (?, 1)", user.getId());
            List<String> roleCodes = jdbcTemplate.query(
                "SELECT r.code FROM role r JOIN user_role ur ON r.id = ur.role_id WHERE ur.user_id = ?",
                (rs, rowNum) -> rs.getString("code"),
                user.getId()
            );
            user.setRoles(roleCodes);
            user.setPassword(null);
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("user", user);
            result.put("token", jwtUtil.generateToken(user.getId()));
            recordLoginLog(user.getId(), user.getUsername(), true);
            return Result.success(result);
        }
        return Result.error("注册失败");
    }

    private void recordLoginLog(Long userId, String username, boolean success) {
        try {
            LoginLog entry = new LoginLog();
            entry.setUserId(userId);
            entry.setUsername(username);
            entry.setSuccess(success ? 1 : 0);
            entry.setLoginAt(LocalDateTime.now());
            loginLogMapper.insert(entry);
        } catch (Exception e) {
            log.error("记录登录日志失败: userId={}, username={}", userId, username, e);
        }
    }
}
