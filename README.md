# Baby Diary

Baby Diary 是一个面向个人、伴侣和家庭的私有日记应用。项目采用 Vue 3 + Vite 前端和 Spring Boot + MyBatis 后端，支持响应式 Web 与可安装 PWA，并围绕长期记录、共同回忆、隐私保护和数据可迁移性设计。

## 功能概览

- 个人空间和共同空间、邀请加入、成员角色与空间设置
- 日记创建、详情、版本历史、回收站、可见范围、日记锁和乐观锁冲突处理
- 评论、Emoji 回应、站内通知、Web Push 和日记提醒
- 富文本、心情、标签、草稿、模板、全文搜索、时间轴、日历和年度洞察
- 纪念日、系统相册、自建相册、收藏相册和 AI 智能相册
- OpenAI 兼容接口、模型列表、AI 周报/月报/年报和定时生成
- 本地或 S3 兼容对象存储、图片/音频/视频、缩略图、转码、波形和 OCR
- 离线编辑队列、增量同步、冲突提示、PWA 安装和移动端壳界面
- 设备会话、短期访问令牌、30 天刷新会话、跨账号前端缓存隔离、邮箱验证、密码找回和恢复码
- 私密限时分享、ZIP v2 导入导出、PDF/EPUB 日记书导出
- Redis 缓存、Flyway V1-V13 迁移、真实 Actuator 健康检查和部署前治理脚本

完整功能、接口与质量门禁见 [document/系统功能文档.md](document/系统功能文档.md)、[document/API接口文档.md](document/API接口文档.md) 和 [document/测试与发布验收方案.md](document/测试与发布验收方案.md)。

## 技术要求

- JDK 17
- Maven 3.9+
- Node.js 22 LTS 和 npm
- MySQL 8.0+，Compose 默认使用 MySQL 8.4 LTS
- Redis 7+
- Docker Compose，可选但推荐用于本地基础设施

## 本地运行

1. 准备环境变量：

```bash
cp .env.example .env
```

编辑 `.env`，至少替换 `DB_PASSWORD`、`MYSQL_ROOT_PASSWORD`、`INVITATION_CODE`、`JWT_SECRET` 和 `AI_CONFIG_ENCRYPTION_KEY`。可使用 `openssl rand -base64 48` 生成随机密钥。邮件、Web Push、S3 和媒体处理工具均为可选配置，变量说明已写在模板中。

2. 启动 MySQL 和 Redis：

```bash
docker compose up -d mysql redis
```

3. 启动后端：

```bash
set -a
. ./.env
set +a
mvn -f backend/pom.xml spring-boot:run
```

4. 在另一个终端启动前端：

```bash
npm --prefix frontend ci
npm --prefix frontend run dev
```

访问 `http://localhost:5173`。后端 API 默认运行在 `http://localhost:10002`，Flyway 会自动创建和升级数据库结构。JDBC URL 必须保留 `connectionTimeZone=%2B08:00&forceConnectionTimeZoneToSession=true`，以固定 MySQL 会话为东八区并避免依赖命名时区表。

## 验证

```bash
npm --prefix frontend ci
scripts/verify.sh
```

验证脚本会运行 Shell 脚本自测、MySQL 8.4 后端集成测试、覆盖率门禁、前端测试和生产构建。发布候选还必须执行三浏览器 E2E 和供应链扫描；CI 中的第三方 GitHub Actions 固定到经过核验的完整提交 SHA，避免可变标签带来的供应链风险：

```bash
scripts/verify-e2e.sh
scripts/security-scan.sh
```

打包预发布、ZAP、k6、iPhone/Android PWA 真机矩阵和生产冒烟步骤见 [测试与发布验收方案](document/测试与发布验收方案.md)。自动化环境只使用合成数据和 Mock AI，不复制真实用户资料。

## 生产部署

生产配置模板位于 `config/application-prod.yml`，所有数据库密码、邀请码、JWT 密钥、AI 加密密钥和站点地址都必须通过服务器私有环境文件注入。生产环境默认关闭 Swagger/OpenAPI，并要求 CORS 使用明确来源。部署脚本会安装 Nginx 安全头与后端健康代理片段、启用 systemd `PrivateTmp`，并在停止后端前完成 Nginx 配置校验；健康检查必须读取 Actuator JSON 且确认顶层状态为 `UP`。部署示例见 [document/部署文档.md](document/部署文档.md)。

`DIARY_FILE_PATH` 只保存旧版兼容图片，`DIARY_OBJECT_PATH` 保存 V2 富媒体，两者必须是不同目录。V2 富媒体通过短时签名 URL 访问；旧版 `/images/**` 为兼容现有客户端仍可公开读取，因此文件名不可视为访问控制，建议新功能统一使用 V2 媒体接口。

项目不会跟踪以下私有内容：

- `.env`、`*.env` 和本地覆盖配置
- `data/images/` 中的用户图片
- `backups/`、`logs/` 和构建产物
- `.private/` 中的本机运维记录

提交前请确认测试数据、截图、日志和文档中不包含真实用户资料、域名、服务器地址或密钥。

## 安全

不要把生产环境文件、数据库 dump、图片数据或 API Key 提交到仓库。安全问题请按 [SECURITY.md](SECURITY.md) 使用 GitHub 私密漏洞报告渠道提交。

## 参与贡献

开发流程和隐私检查清单见 [CONTRIBUTING.md](CONTRIBUTING.md)。

## 许可证

本项目使用 [MIT License](LICENSE)。
