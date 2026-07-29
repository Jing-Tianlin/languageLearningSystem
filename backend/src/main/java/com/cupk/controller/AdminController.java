package com.cupk.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cupk.common.Result;
import com.cupk.mapper.*;
import com.cupk.pojo.*;
import com.cupk.utils.PasswordUtil;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.*;

@RestController
@RequestMapping("/admin")
public class AdminController {

    private static final Logger log = LoggerFactory.getLogger(AdminController.class);

    @Autowired
    private UserMapper userMapper;
    @Autowired
    private RoleMapper roleMapper;
    @Autowired
    private OperationLogMapper operationLogMapper;
    @Autowired
    private VocabularyMapper vocabularyMapper;
    @Autowired
    private PermissionMapper permissionMapper;
    @Autowired
    private CourseMapper courseMapper;
    @Autowired
    private LanguageMapper languageMapper;
    @Autowired
    private UserProfileMapper userProfileMapper;
    @Autowired
    private LoginLogMapper loginLogMapper;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private HttpServletRequest request;
    @Autowired
    private com.cupk.service.VocabularyRepairService vocabularyRepairService;

    // ==================== 系统统计仪表盘 ====================

    @GetMapping("/stats/dashboard")
    public Result<Map<String, Object>> getDashboardStats() {
        Map<String, Object> stats = new LinkedHashMap<>();

        Long totalUsers = userMapper.selectCount(null);
        stats.put("totalUsers", totalUsers != null ? totalUsers : 0);

        Long todayUsers = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM user WHERE DATE(create_time) = CURDATE()", Long.class);
        stats.put("todayNewUsers", todayUsers != null ? todayUsers : 0);

        Long totalVocab = vocabularyMapper.selectCount(null);
        stats.put("totalVocabulary", totalVocab != null ? totalVocab : 0);

        List<Map<String, Object>> vocabByLang = jdbcTemplate.queryForList(
            "SELECT lang_code, COUNT(*) as count FROM vocabulary WHERE is_deleted = 0 GROUP BY lang_code");
        stats.put("vocabularyByLang", vocabByLang);

        Long totalLogs = operationLogMapper.selectCount(null);
        stats.put("totalOperationLogs", totalLogs != null ? totalLogs : 0);

        Long activeUsers = jdbcTemplate.queryForObject(
            "SELECT COUNT(DISTINCT user_id) FROM user_progress WHERE update_time > DATE_SUB(NOW(), INTERVAL 7 DAY)", Long.class);
        stats.put("activeUsers7d", activeUsers != null ? activeUsers : 0);

        List<Map<String, Object>> userLangDistribution = jdbcTemplate.queryForList(
            "SELECT current_lang_code as lang, COUNT(*) as count FROM user WHERE is_deleted = 0 GROUP BY current_lang_code");
        stats.put("userLangDistribution", userLangDistribution);

        List<Map<String, Object>> recentUsers = jdbcTemplate.queryForList(
            "SELECT id, username, nickname, create_time FROM user WHERE is_deleted = 0 ORDER BY create_time DESC LIMIT 5");
        stats.put("recentUsers", recentUsers);

        return Result.success(stats);
    }

    // ==================== 用户管理 ====================

    @GetMapping("/users")
    public Result<Page<User>> getUsers(
            @RequestParam(defaultValue = "") String keyword,
            @RequestParam(defaultValue = "1") Integer pageNo,
            @RequestParam(defaultValue = "20") Integer pageSize) {
        Page<User> page = new Page<>(pageNo, pageSize);
        QueryWrapper<User> queryWrapper = new QueryWrapper<>();
        if (!keyword.isEmpty()) {
            queryWrapper.and(w -> w.like("username", keyword).or().like("nickname", keyword).or().like("email", keyword));
        }
        queryWrapper.orderByDesc("create_time");
        userMapper.selectPage(page, queryWrapper);

        for (User u : page.getRecords()) {
            List<String> roleCodes = jdbcTemplate.query(
                "SELECT r.code FROM role r JOIN user_role ur ON r.id = ur.role_id WHERE ur.user_id = ?",
                (rs, rowNum) -> rs.getString("code"),
                u.getId()
            );
            u.setRoles(roleCodes);
            u.setPassword(null);
        }
        return Result.success(page);
    }

    @GetMapping("/users/{id}")
    public Result<User> getUserDetail(@PathVariable Long id) {
        User user = userMapper.selectById(id);
        if (user == null) {
            return Result.error(404, "用户不存在");
        }
        List<String> roleCodes = jdbcTemplate.query(
            "SELECT r.code FROM role r JOIN user_role ur ON r.id = ur.role_id WHERE ur.user_id = ?",
            (rs, rowNum) -> rs.getString("code"),
            id
        );
        user.setRoles(roleCodes);
        user.setPassword(null);
        return Result.success(user);
    }

    @PostMapping("/users")
    public Result<User> createUser(@RequestBody User user) {
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
        userMapper.insert(user);

        jdbcTemplate.update("INSERT INTO user_role (user_id, role_id) VALUES (?, 1)", user.getId());

        user.setPassword(null);
        log.info("管理员创建用户: {}", user.getUsername());
        recordLog("user_create", "创建用户: " + user.getUsername());
        return Result.success(user);
    }

    @PutMapping("/users")
    public Result<Void> updateUser(@RequestBody User user) {
        if (user.getId() == null) {
            return Result.error(400, "用户ID不能为空");
        }
        User exist = userMapper.selectById(user.getId());
        if (exist == null) {
            return Result.error(404, "用户不存在");
        }
        user.setPassword(null);
        userMapper.updateById(user);
        log.info("管理员更新用户: userId={}", user.getId());
        recordLog("user_update", "更新用户: " + user.getId());
        return Result.success("更新成功");
    }

    @PostMapping("/users/{id}/reset-password")
    public Result<Void> resetPassword(@PathVariable Long id, @RequestBody Map<String, String> body) {
        String newPassword = body.get("password");
        if (newPassword == null || newPassword.isEmpty()) {
            return Result.error(400, "新密码不能为空");
        }
        if (newPassword.length() < 6) {
            return Result.error(400, "密码长度不能少于6位");
        }
        User user = userMapper.selectById(id);
        if (user == null) {
            return Result.error(404, "用户不存在");
        }
        user.setPassword(PasswordUtil.encode(newPassword));
        userMapper.updateById(user);
        log.info("管理员重置密码: userId={}", id);
        recordLog("user_reset_password", "重置用户密码: " + user.getUsername());
        return Result.success("密码重置成功");
    }

    @PostMapping("/users/{id}/toggle-status")
    public Result<Void> toggleUserStatus(@PathVariable Long id) {
        User user = userMapper.selectById(id);
        if (user == null) {
            return Result.error(404, "用户不存在");
        }
        Integer currentStatus = user.getStatus();
        int newStatus = (currentStatus != null && currentStatus == 1) ? 0 : 1;
        user.setStatus(newStatus);
        userMapper.updateById(user);
        String action = newStatus == 1 ? "启用" : "禁用";
        log.info("管理员{}用户: userId={}", action, id);
        recordLog("user_status_change", action + "用户: " + user.getUsername());
        return Result.success(action + "成功");
    }

    @DeleteMapping("/users/{id}")
    public Result<Void> deleteUser(@PathVariable Long id) {
        User user = userMapper.selectById(id);
        if (user == null) {
            return Result.error(404, "用户不存在");
        }

        List<String> roles = jdbcTemplate.query(
            "SELECT r.code FROM role r JOIN user_role ur ON r.id = ur.role_id WHERE ur.user_id = ?",
            (rs, rowNum) -> rs.getString("code"),
            id
        );
        if (roles.contains("ROLE_ADMIN")) {
            Long adminCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM user_role ur JOIN role r ON ur.role_id = r.id WHERE r.code = 'ROLE_ADMIN'", Long.class);
            if (adminCount != null && adminCount <= 1) {
                return Result.error(400, "至少需要保留一个管理员账号");
            }
        }

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
        userMapper.deleteById(id);

        log.info("管理员删除用户: userId={}, username={}", id, user.getUsername());
        recordLog("user_delete", "删除用户: " + user.getUsername());
        return Result.success("删除成功");
    }

    // ==================== 角色管理 ====================

    @GetMapping("/roles")
    public Result<List<Role>> getRoles() {
        return Result.success(roleMapper.selectList(null));
    }

    @PostMapping("/roles")
    public Result<Role> createRole(@RequestBody Role role) {
        if (role.getCode() == null || role.getCode().isEmpty()) {
            return Result.error(400, "角色编码不能为空");
        }
        if (role.getName() == null || role.getName().isEmpty()) {
            return Result.error(400, "角色名称不能为空");
        }
        QueryWrapper<Role> q = new QueryWrapper<>();
        q.eq("code", role.getCode());
        if (roleMapper.selectCount(q) != null && roleMapper.selectCount(q) > 0) {
            return Result.error(400, "角色编码已存在");
        }
        role.setId(null);
        roleMapper.insert(role);
        recordLog("role_create", "创建角色: " + role.getName());
        return Result.success(role);
    }

    @PutMapping("/roles")
    public Result<Void> updateRole(@RequestBody Role role) {
        if (role.getId() == null) {
            return Result.error(400, "角色ID不能为空");
        }
        Role exist = roleMapper.selectById(role.getId());
        if (exist == null) {
            return Result.error(404, "角色不存在");
        }
        roleMapper.updateById(role);
        recordLog("role_update", "更新角色: " + role.getName());
        return Result.success("更新成功");
    }

    @DeleteMapping("/roles/{id}")
    public Result<Void> deleteRole(@PathVariable Long id) {
        Role role = roleMapper.selectById(id);
        if (role == null) {
            return Result.error(404, "角色不存在");
        }
        if ("ROLE_ADMIN".equals(role.getCode()) || "ROLE_USER".equals(role.getCode())) {
            return Result.error(400, "系统内置角色不能删除");
        }
        Long userCount = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM user_role WHERE role_id = ?", Long.class, id);
        if (userCount != null && userCount > 0) {
            return Result.error(400, "该角色下还有用户，无法删除");
        }
        roleMapper.deleteById(id);
        recordLog("role_delete", "删除角色: " + role.getName());
        return Result.success("删除成功");
    }

    @GetMapping("/user-roles/{userId}")
    public Result<List<Role>> getUserRoles(@PathVariable Long userId) {
        return Result.success(roleMapper.findByUserId(userId));
    }

    @PostMapping("/user-roles")
    public Result<Void> assignRole(@RequestBody Map<String, Object> body) {
        Long userId = Long.valueOf(body.get("userId").toString());
        Long roleId = Long.valueOf(body.get("roleId").toString());

        boolean hasRole = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM user_role WHERE user_id = ? AND role_id = ?",
            Long.class, userId, roleId) != null &&
            jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM user_role WHERE user_id = ? AND role_id = ?",
                Long.class, userId, roleId) > 0;

        if (hasRole) {
            jdbcTemplate.update("DELETE FROM user_role WHERE user_id = ? AND role_id = ?", userId, roleId);
            recordLog("role_remove", "移除用户角色: userId=" + userId + ", roleId=" + roleId);
        } else {
            jdbcTemplate.update("INSERT IGNORE INTO user_role (user_id, role_id) VALUES (?, ?)", userId, roleId);
            recordLog("role_assign", "分配用户角色: userId=" + userId + ", roleId=" + roleId);
        }
        return Result.success("操作成功");
    }

    // ==================== 操作日志 ====================

    @GetMapping("/logs")
    public Result<Page<OperationLog>> getLogs(
            @RequestParam(defaultValue = "") String keyword,
            @RequestParam(defaultValue = "") String module,
            @RequestParam(defaultValue = "1") Integer pageNo,
            @RequestParam(defaultValue = "20") Integer pageSize) {
        Page<OperationLog> page = new Page<>(pageNo, pageSize);
        QueryWrapper<OperationLog> q = new QueryWrapper<>();
        if (!keyword.isEmpty()) {
            q.and(w -> w.like("action", keyword).or().like("detail", keyword));
        }
        if (!module.isEmpty()) {
            q.eq("module", module);
        }
        q.orderByDesc("created_at");
        operationLogMapper.selectPage(page, q);
        return Result.success(page);
    }

    // ==================== 权限管理 ====================

    @GetMapping("/permissions")
    public Result<List<Permission>> getPermissions() {
        return Result.success(permissionMapper.selectList(null));
    }

    @GetMapping("/permissions/by-role/{roleId}")
    public Result<List<Permission>> getPermissionsByRole(@PathVariable Long roleId) {
        return Result.success(permissionMapper.findByRoleId(roleId));
    }

    @PostMapping("/role-permissions")
    public Result<Void> assignPermission(@RequestBody Map<String, Object> body) {
        Long roleId = Long.valueOf(body.get("roleId").toString());
        Long permissionId = Long.valueOf(body.get("permissionId").toString());

        boolean has = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM role_permission WHERE role_id = ? AND permission_id = ?",
            Long.class, roleId, permissionId) > 0;

        if (has) {
            jdbcTemplate.update("DELETE FROM role_permission WHERE role_id = ? AND permission_id = ?", roleId, permissionId);
            recordLog("permission_remove", "移除权限: roleId=" + roleId + ", permId=" + permissionId);
        } else {
            jdbcTemplate.update("INSERT INTO role_permission (role_id, permission_id) VALUES (?, ?)", roleId, permissionId);
            recordLog("permission_assign", "分配权限: roleId=" + roleId + ", permId=" + permissionId);
        }
        return Result.success("操作成功");
    }

    // ==================== 课程管理 ====================

    @GetMapping("/courses")
    public Result<Page<Course>> getCourses(
            @RequestParam(defaultValue = "") String keyword,
            @RequestParam(defaultValue = "1") Integer pageNo,
            @RequestParam(defaultValue = "20") Integer pageSize) {
        Page<Course> page = new Page<>(pageNo, pageSize);
        QueryWrapper<Course> q = new QueryWrapper<>();
        if (!keyword.isEmpty()) {
            q.and(w -> w.like("title", keyword).or().like("description", keyword));
        }
        q.orderByDesc("create_time");
        courseMapper.selectPage(page, q);
        return Result.success(page);
    }

    @PostMapping("/courses")
    public Result<Course> createCourse(@RequestBody Course course) {
        if (course.getTitle() == null || course.getTitle().isEmpty()) {
            return Result.error(400, "课程标题不能为空");
        }
        course.setId(null);
        courseMapper.insert(course);
        recordLog("course_create", "创建课程: " + course.getTitle());
        return Result.success(course);
    }

    @PutMapping("/courses")
    public Result<Void> updateCourse(@RequestBody Course course) {
        if (course.getId() == null) return Result.error(400, "课程ID不能为空");
        courseMapper.updateById(course);
        recordLog("course_update", "更新课程: id=" + course.getId());
        return Result.success("更新成功");
    }

    @DeleteMapping("/courses/{id}")
    public Result<Void> deleteCourse(@PathVariable Long id) {
        courseMapper.deleteById(id);
        recordLog("course_delete", "删除课程: id=" + id);
        return Result.success("删除成功");
    }

    // ==================== 语言管理 ====================

    @GetMapping("/languages")
    public Result<List<Language>> getLanguages() {
        return Result.success(languageMapper.selectList(null));
    }

    @PostMapping("/languages")
    public Result<Language> createLanguage(@RequestBody Language lang) {
        if (lang.getCode() == null || lang.getCode().isEmpty()) {
            return Result.error(400, "语言代码不能为空");
        }
        lang.setId(null);
        languageMapper.insert(lang);
        recordLog("language_create", "创建语言: " + lang.getNameCn());
        return Result.success(lang);
    }

    @DeleteMapping("/languages/{id}")
    public Result<Void> deleteLanguage(@PathVariable Long id) {
        languageMapper.deleteById(id);
        recordLog("language_delete", "删除语言: id=" + id);
        return Result.success("删除成功");
    }

    // ==================== 用户扩展资料 ====================

    @GetMapping("/user-profiles/{userId}")
    public Result<UserProfile> getUserProfile(@PathVariable Long userId) {
        UserProfile profile = userProfileMapper.selectById(userId);
        if (profile == null) {
            profile = new UserProfile();
            profile.setUserId(userId);
        }
        return Result.success(profile);
    }

    // ==================== 登录日志 ====================

    @GetMapping("/login-logs")
    public Result<Page<LoginLog>> getLoginLogs(
            @RequestParam(defaultValue = "") String username,
            @RequestParam(defaultValue = "1") Integer pageNo,
            @RequestParam(defaultValue = "20") Integer pageSize) {
        Page<LoginLog> page = new Page<>(pageNo, pageSize);
        QueryWrapper<LoginLog> q = new QueryWrapper<>();
        if (!username.isEmpty()) {
            q.like("username", username);
        }
        q.orderByDesc("login_at");
        loginLogMapper.selectPage(page, q);
        return Result.success(page);
    }

    // ==================== 词汇修复 ====================

    /**
     * 批量修复乱码词汇（调用 AI 重新生成音标/释义/例句/翻译）
     * POST /admin/repair-vocabulary?langCode=en&limit=500
     */
    @PostMapping("/repair-vocabulary")
    public Result<Map<String, Integer>> repairVocabulary(
            @RequestParam(defaultValue = "en") String langCode,
            @RequestParam(defaultValue = "500") int limit) {
        log.info("管理员开始修复乱码词汇: langCode={}, limit={}", langCode, limit);
        Map<String, Integer> stats = vocabularyRepairService.repairByLanguage(langCode, limit);
        recordLog("vocab_repair", "修复词汇乱码: " + langCode + ", 修复" + stats.get("fixed") + "条");
        return Result.success(stats);
    }

    private void recordLog(String action, String detail) {
        try {
            OperationLog oplog = new OperationLog();
            Object userIdAttr = request.getAttribute("currentUserId");
            if (userIdAttr != null) {
                oplog.setUserId(Long.valueOf(userIdAttr.toString()));
            }
            oplog.setAction(action);
            oplog.setDetail(detail);
            oplog.setModule("admin");
            oplog.setIpAddress(request.getRemoteAddr());
            oplog.setCreatedAt(LocalDateTime.now());
            operationLogMapper.insert(oplog);
        } catch (Exception e) {
            log.error("记录操作日志失败: action={}, detail={}", action, detail, e);
        }
    }
}
