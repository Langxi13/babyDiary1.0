# Baby Diary

Baby Diary 是一个面向伴侣和家庭的私有日记应用，提供富文本日记、图片、时间轴、日历、纪念日、相册、草稿以及 AI 周报/月报等功能。项目采用 Vue 3 + Vite 前端和 Spring Boot + MyBatis 后端，并支持安装为 PWA。

## 功能概览

- 邀请码注册、JWT 登录和个人资料管理
- 富文本日记、心情、标签、草稿和多图排序
- 日记列表、详情、搜索、时间轴和日历
- 纪念日封面、系统相册、自建相册和收藏相册
- OpenAI 兼容接口配置、模型列表、AI 周报和月报
- 图片压缩、缩略图、分页加载和 ZIP 导出
- 桌面端与移动端响应式界面、PWA 安装和安卓分享目标
- Redis 缓存、Flyway 迁移、Actuator 健康检查

完整功能与接口说明见 [document/系统功能文档.md](document/系统功能文档.md) 和 [document/API接口文档.md](document/API接口文档.md)。

## 技术要求

- JDK 17
- Maven 3.9+
- Node.js 22 LTS 和 npm
- MySQL 8.0+
- Redis 7+
- Docker Compose，可选但推荐用于本地基础设施

## 本地运行

1. 准备环境变量：

```bash
cp .env.example .env
```

编辑 `.env`，至少替换 `DB_PASSWORD`、`MYSQL_ROOT_PASSWORD`、`INVITATION_CODE`、`JWT_SECRET` 和 `AI_CONFIG_ENCRYPTION_KEY`。可使用 `openssl rand -base64 48` 生成随机密钥。

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

访问 `http://localhost:5173`。后端 API 默认运行在 `http://localhost:10002`，Flyway 会自动创建和升级数据库结构。

## 验证

```bash
npm --prefix frontend ci
scripts/verify.sh
```

验证脚本会运行 Shell 脚本自测、后端测试、前端测试和生产构建。部署前还建议执行：

```bash
npm --prefix frontend audit --audit-level=moderate
```

## 生产部署

生产配置模板位于 `config/application-prod.yml`，所有数据库密码、邀请码、JWT 密钥、AI 加密密钥和站点地址都必须通过服务器私有环境文件注入。部署示例见 [document/部署文档.md](document/部署文档.md)。

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
