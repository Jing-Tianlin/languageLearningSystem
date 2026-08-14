# languageLearningSystem — 多语言学习系统

一个前后端分离的多语言学习 Web 应用（英语 / 日语 / 韩语 / 法语 / 德语），内置 AI 辅助（DeepSeek）与游戏化学习机制。

## 技术栈

| 端 | 技术 |
|---|---|
| 后端 | Spring Boot 3.5 · Java 21 · MyBatis-Plus 3.5 · MySQL · JWT · Swagger (springdoc) · Actuator |
| 前端 | Vue 3 · Vite · Pinia · Vue Router · ECharts 6 · GSAP · Sass |
| AI | DeepSeek API（i+1 例句 / 语法纠错 / 写作评分 / 流式问答 / 材料生成） |

## 目录结构

```
languageLearningSystem/
├── backend/                    # Spring Boot 后端
│   ├── src/main/java/com/cupk/
│   │   ├── controller/         # REST 接口（user/vocabulary/grammar/reading/writing/ai/admin ...）
│   │   ├── service/            # 业务逻辑（DeepSeekService 等）
│   │   ├── config/             # 鉴权拦截、限流、异常处理、时钟
│   │   ├── mapper/ pojo/       # MyBatis-Plus 数据访问层
│   │   └── util(s)/            # JWT、密码哈希等工具
│   └── src/main/resources/
│       ├── application.yaml    # 配置（密钥全部走环境变量）
│       └── languagelearningsystem.sql   # 数据库初始化 dump
└── frontend/                   # Vue 3 前端
    └── src/
        ├── views/              # 页面（16 个）
        ├── components/         # 组件（卡片/布局/游戏化/练习会话）
        ├── stores/             # Pinia 状态
        ├── api/                # 请求封装（client.js / fetchJson.js）
        └── composables/        # 组合式函数（toast/提醒/犹豫追踪）
```

## 快速开始

### 0. 环境要求

- JDK 21、Maven（或使用自带的 `mvnw`）、Node.js ≥ 22、MySQL 5.7+

### 1. 初始化数据库

```sql
CREATE DATABASE languagelearningsystem DEFAULT CHARACTER SET utf8mb4;
mysql -u root -p languagelearningsystem < backend/src/main/resources/languagelearningsystem.sql
```

### 2. 配置环境变量（后端启动必需）

| 变量 | 说明 |
|---|---|
| `DB_USERNAME` | MySQL 用户名（默认 `root`） |
| `DB_PASSWORD` | MySQL 密码（**必填**，不再提供默认值） |
| `DEEPSEEK_API_KEY` | DeepSeek API Key（AI 功能必需，**必填**） |
| `JWT_SECRET` | JWT 签名密钥，**至少 32 字节**，未配置时后端启动失败。生成示例：`openssl rand -base64 48` |

Windows PowerShell 示例：

```powershell
$env:DB_USERNAME="root"
$env:DB_PASSWORD="your-password"
$env:DEEPSEEK_API_KEY="sk-..."
$env:JWT_SECRET="$(openssl rand -base64 48)"
```

> 安全说明：所有密钥均通过环境变量注入，配置文件中不保存任何真实密钥或弱默认值。
> 如果这些密钥曾随代码提交到 Git，请立即在对应平台（数据库、DeepSeek 控制台）**轮换密钥**。

### 3. 启动后端

```powershell
cd backend
.\mvnw.cmd spring-boot:run
```

- 默认端口 `8080`
- Swagger UI：<http://localhost:8080/swagger-ui.html>
- 健康检查：<http://localhost:8080/actuator/health>

### 4. 启动前端

```powershell
cd frontend
npm install
npm run dev
```

- 默认地址：<http://localhost:5173>
- 如需指向其他后端地址，设置 `VITE_API_BASE_URL`

### 5. 演示账号

| 用户名 | 说明 |
|---|---|
| `demo` | 演示用户（含学习数据） |
| 数据库中的其他账号 | 见 SQL dump |

> 注意：`demo` 等存量账号的密码为旧格式哈希，首次登录成功后将自动升级为 BCrypt。

## 主要功能

- **词汇学习**：闪卡、四选一测验、SM-2 间隔重复智能选题、i+1 可理解输入例句、收藏
- **语法中心**：分语言课程、填空/改错练习、顽固语法点分析
- **阅读 / 写作**：分级文章 + 测验；AI 写作评分与语法纠错
- **AI 助手**：流式对话、例句生成、练习/写作题目/阅读材料生成
- **数据统计**：ECharts 仪表盘、薄弱点雷达、学习趋势、跨语言对比
- **游戏化**：每日打卡、学习提醒、犹豫追踪、热词 × 薄弱语法联动
- **管理后台**：RBAC 用户/角色/权限管理、操作日志审计、词汇数据修复

## 安全与限流

- 密码使用 **BCrypt**（强度 10）存储；登录自动升级旧哈希
- 注册 / 管理员建户使用 **DTO 白名单**，杜绝 mass-assignment（无法越权写入 status/points/role 等字段）
- JWT（HMAC-SHA256，7 天有效期）鉴权；账号禁用即时生效；**密码重置后旧 token 立即失效**（token 携带签发时间 + `last_password_change_at` 列）
- **登录防爆破**：同一用户名连续失败 5 次锁定 15 分钟（`login.attempt.*` 可配置），并记录失败日志（含 IP 与原因）
- AI 接口双重配额：每分钟 `10` 次、每日 `100` 次（`ai.quota.*` 可配置）
- **AI 结果缓存**：相同 prompt 短时间（默认 10 分钟）直接复用，降低 API 成本（`ai.cache.*` 可配置）
- AI 入参长度/数量上限校验，防止恶意请求放大 API 成本
- 虚拟线程（`spring.threads.virtual.enabled`）提升 AI 阻塞调用的并发吞吐
- CORS 仅允许本地开发域名，生产部署前需调整

## 构建与测试

```powershell
# 后端：编译 + 核心单元测试（不依赖数据库）
cd backend
.\mvnw.cmd test -Dtest="PasswordUtilTest,JwtUtilTest,AiQuotaServiceTest,LoginAttemptServiceTest,AiResultCacheTest"

# 前端：生产构建（产物在 frontend/dist）
cd frontend
npm run build
```

## Docker 部署

```powershell
# 复制 .env 模板并填写三个必填变量
$env:DB_PASSWORD="..."; $env:DEEPSEEK_API_KEY="sk-..."; $env:JWT_SECRET="$(openssl rand -base64 48)"
docker compose up --build
```

- 前端：<http://localhost:80>（Nginx 托管静态产物，SPA 路由已配置回退）
- 后端：<http://localhost:8080>，Swagger <http://localhost:8080/swagger-ui.html>
- 首次启动时 MySQL 会自动执行 `languagelearningsystem.sql` 初始化数据

## CI

`.github/workflows/ci.yml` 提供 GitHub Actions：后端编译 + 单元测试（JDK 21），前端 `npm ci && npm run build`（Node 22）。

## API 文档

启动后端后访问 <http://localhost:8080/swagger-ui.html>，或直接获取 OpenAPI 描述 <http://localhost:8080/v3/api-docs>。

## 已知限制 / 后续规划

- [ ] 登录防爆破、AI 配额、AI 缓存、鉴权缓存均为单实例内存实现，多实例/集群部署需迁移到 Redis
- [ ] 数据库 schema 变更尚未引入 Flyway/Liquibase 迁移工具（新增列通过 dump 内 ALTER 语句维护）
- [ ] 登录接口暂无图形验证码（已有失败锁定）
- [ ] 前端暂未引入 TypeScript 与单元测试
- [ ] 后端完整测试（`BackendApplicationTests`）需要可用数据库与全部环境变量
