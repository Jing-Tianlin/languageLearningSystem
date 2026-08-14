package com.cupk.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cupk.common.RegisterRequest;
import com.cupk.common.Result;
import com.cupk.config.AuthCacheService;
import com.cupk.config.LoginAttemptService;
import com.cupk.mapper.LoginLogMapper;
import com.cupk.mapper.UserMapper;
import com.cupk.pojo.LoginLog;
import com.cupk.pojo.User;
import com.cupk.utils.PasswordUtil;
import com.cupk.util.AuthUtil;
import com.cupk.util.JwtUtil;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/user")
@RequiredArgsConstructor
public class UserController {

    private static final Logger log = LoggerFactory.getLogger(UserController.class);

    /** 注册密码长度限制（6-64 字符） */
    private static final int PASSWORD_MIN_LENGTH = 6;
    private static final int PASSWORD_MAX_LENGTH = 64;

    private final UserMapper userMapper;

    private final JdbcTemplate jdbcTemplate;

    private final LoginLogMapper loginLogMapper;

    private final JwtUtil jwtUtil;

    private final AuthCacheService authCacheService;

    private final LoginAttemptService loginAttemptService;

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
        // 批量查询角色，避免 N+1
        Map<Long, List<String>> rolesMap = loadRolesBatch(page.getRecords().stream().map(User::getId).toList());
        for (User u : page.getRecords()) {
            u.setRoles(rolesMap.getOrDefault(u.getId(), List.of()));
        }
        return Result.success(page);
    }

    @GetMapping("/users/{id}")
    public Result<User> selectById(@PathVariable Long id) {
        // 只能查询自己的信息
        Long currentUserId = AuthUtil.getCurrentUserId();
        if (currentUserId == null || !currentUserId.equals(id)) {
            return Result.error(403, "无权访问其他用户信息");
        }
        User user = userMapper.selectById(id);
        if (user == null) {
            return Result.error(404, "用户不存在");
        }
        user.setPassword(null);
        return Result.success(user);
    }

    @PostMapping("/users")
    public Result<User> insert(@RequestBody User user) {
        // 创建用户属于管理操作
        if (!AuthUtil.hasRole("ROLE_ADMIN")) {
            return Result.error(403, "无管理员权限");
        }
        user.setId(null);
        int rows = userMapper.insert(user);
        if (rows > 0) {
            return Result.success(user);
        }
        return Result.error("新增失败");
    }

    @PutMapping("/users")
    public Result<Void> update(@RequestBody User user) {
        if (user.getId() == null) {
            return Result.error(400, "用户ID不能为空");
        }
        // 只能修改自己的信息
        Long currentUserId = AuthUtil.getCurrentUserId();
        if (currentUserId == null || !currentUserId.equals(user.getId())) {
            return Result.error(403, "无权修改其他用户信息");
        }
        // 防止通过该接口篡改密码，密码变更必须走专门的修改密码流程
        user.setPassword(null);
        int rows = userMapper.updateById(user);
        if (rows > 0) {
            return Result.success("修改成功");
        }
        return Result.error("修改失败");
    }

    @PutMapping("/preferences")
    public Result<Void> updatePreferences(@RequestParam(required = false) Long userId,
                                          @RequestParam(required = false) String langCode,
                                          @RequestParam(required = false) String level) {
        // 只能修改自己的偏好设置，userId 强制取 token
        Long currentUserId = AuthUtil.getCurrentUserId();
        if (currentUserId == null) {
            return Result.error(401, "未登录");
        }
        User user = userMapper.selectById(currentUserId);
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
    @org.springframework.transaction.annotation.Transactional(rollbackFor = Exception.class)
    public Result<Void> delete(@PathVariable Long id) {
        // 用户只能注销自己的账号
        Long currentUserId = AuthUtil.getCurrentUserId();
        if (currentUserId == null || !currentUserId.equals(id)) {
            return Result.error(403, "无权删除其他用户账号");
        }
        jdbcTemplate.update("DELETE FROM favorites WHERE user_id = ?", id);
        jdbcTemplate.update("DELETE FROM user_progress WHERE user_id = ?", id);
        jdbcTemplate.update("DELETE FROM ai_chat_history WHERE user_id = ?", id);
        jdbcTemplate.update("DELETE FROM writing_history WHERE user_id = ?", id);
        jdbcTemplate.update("DELETE FROM writing_submissions WHERE user_id = ?", id);
        jdbcTemplate.update("DELETE FROM reading_history WHERE user_id = ?", id);
        jdbcTemplate.update("DELETE FROM user_reading_records WHERE user_id = ?", id);
        jdbcTemplate.update("DELETE FROM practice_records WHERE user_id = ?", id);
        jdbcTemplate.update("DELETE FROM user_role WHERE user_id = ?", id);
        jdbcTemplate.update("DELETE FROM user_profile WHERE user_id = ?", id);
        jdbcTemplate.update("DELETE FROM login_log WHERE user_id = ?", id);
        jdbcTemplate.update("DELETE FROM operation_log WHERE user_id = ?", id);
        jdbcTemplate.update("DELETE FROM inspection_log WHERE user_id = ?", id);
        jdbcTemplate.update("DELETE FROM user WHERE id = ?", id);
        return Result.success("删除成功");
    }

    @PostMapping("/login")
    public Result<Map<String, Object>> login(@RequestBody Map<String, String> params, HttpServletRequest request) {
        String username = params.get("username");
        String password = params.get("password");
        if (username == null || username.isEmpty() || password == null || password.isEmpty()) {
            return Result.error(400, "用户名和密码不能为空");
        }

        String attemptKey = username.trim().toLowerCase();
        if (loginAttemptService.isLocked(attemptKey)) {
            long remainSec = loginAttemptService.remainingLockSeconds(attemptKey);
            return Result.error(429, "登录失败次数过多，请 " + Math.max(1, (remainSec + 59) / 60) + " 分钟后重试");
        }
        String ip = request.getRemoteAddr();

        QueryWrapper<User> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("username", username);
        User user = userMapper.selectOne(queryWrapper);
        if (user == null) {
            loginAttemptService.recordFailure(attemptKey);
            recordLoginLog(null, username, false, ip, "用户名不存在");
            return Result.error(401, "用户名或密码错误");
        }
        if (!PasswordUtil.matches(password, user.getPassword())) {
            loginAttemptService.recordFailure(attemptKey);
            recordLoginLog(user.getId(), username, false, ip, "密码错误");
            return Result.error(401, "用户名或密码错误");
        }

        // 登录成功清零失败计数
        loginAttemptService.recordSuccess(attemptKey);

        // 旧格式（盐$SHA256）密码登录成功后自动升级为 BCrypt
        if (PasswordUtil.needsRehash(user.getPassword())) {
            try {
                String upgraded = PasswordUtil.encode(password);
                jdbcTemplate.update("UPDATE user SET password = ? WHERE id = ?", upgraded, user.getId());
                log.info("用户密码已自动升级为 BCrypt: userId={}", user.getId());
            } catch (Exception e) {
                log.error("密码自动升级失败: userId={}", user.getId(), e);
            }
        }
        // 登录成功后刷新鉴权缓存（角色/状态变更能立即生效）
        authCacheService.evict(user.getId());
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
        recordLoginLog(user.getId(), username, true, ip, null);
        return Result.success(result);
    }

    @PostMapping("/register")
    public Result<Map<String, Object>> register(@RequestBody RegisterRequest req) {
        String username = req.username();
        String password = req.password();
        if (username == null || username.isEmpty()) {
            return Result.error(400, "用户名不能为空");
        }
        if (password == null || password.isEmpty()) {
            return Result.error(400, "密码不能为空");
        }
        if (password.length() < PASSWORD_MIN_LENGTH || password.length() > PASSWORD_MAX_LENGTH) {
            return Result.error(400, "密码长度需为 " + PASSWORD_MIN_LENGTH + "-" + PASSWORD_MAX_LENGTH + " 个字符");
        }
        QueryWrapper<User> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("username", username);
        User exist = userMapper.selectOne(queryWrapper);
        if (exist != null) {
            return Result.error(400, "用户名已存在");
        }

        User user = new User();
        user.setUsername(username);
        user.setPassword(PasswordUtil.encode(password));
        user.setNickname((req.nickname() == null || req.nickname().isEmpty()) ? username : req.nickname());
        user.setEmail(emptyToNull(req.email()));
        user.setPhone(emptyToNull(req.phone()));
        user.setStatus(1);
        user.setLastPasswordChangeAt(LocalDateTime.now());

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
            recordLoginLog(user.getId(), username, true);
            return Result.success(result);
        }
        return Result.error("注册失败");
    }

    /** 批量加载多个用户的角色，避免 N+1 查询 */
    private Map<Long, List<String>> loadRolesBatch(List<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return Map.of();
        }
        String inClause = String.join(",", Collections.nCopies(userIds.size(), "?"));
        Map<Long, List<String>> map = new HashMap<>();
        jdbcTemplate.query(
            "SELECT ur.user_id, r.code FROM user_role ur JOIN role r ON ur.role_id = r.id WHERE ur.user_id IN (" + inClause + ")",
            rs -> {
                Long uid = rs.getLong("user_id");
                map.computeIfAbsent(uid, k -> new ArrayList<>()).add(rs.getString("code"));
            },
            userIds.toArray()
        );
        return map;
    }

    private String emptyToNull(String s) {
        return (s == null || s.isEmpty()) ? null : s;
    }

    private void recordLoginLog(Long userId, String username, boolean success) {
        recordLoginLog(userId, username, success, null, null);
    }

    private void recordLoginLog(Long userId, String username, boolean success, String ipAddress, String failReason) {
        try {
            LoginLog entry = new LoginLog();
            entry.setUserId(userId);
            entry.setUsername(username);
            entry.setSuccess(success ? 1 : 0);
            entry.setIpAddress(ipAddress);
            entry.setFailReason(failReason);
            entry.setLoginAt(LocalDateTime.now());
            loginLogMapper.insert(entry);
        } catch (Exception e) {
            log.error("记录登录日志失败: userId={}, username={}", userId, username, e);
        }
    }
}
