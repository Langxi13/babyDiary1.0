# Baby Diary

Baby Diary 是一个面向个人、伴侣和家庭的私有日记应用。项目采用 Vue 3 + Vite 前端和 Spring Boot + MyBatis 后端，支持响应式 Web、可安装 PWA 与 Capacitor 原生客户端，并围绕长期记录、共同回忆、隐私保护和数据可迁移性设计。

## 功能概览

- 个人空间和共同空间、邀请加入、成员角色与空间设置
- 日记创建、详情、版本历史、回收站、可见范围、日记锁和乐观锁冲突处理
- 评论、Emoji 回应、站内通知、Web Push 和日记提醒
- 富文本、心情、标签、草稿、模板、全文搜索、时间轴、日历和年度洞察
- 纪念日、系统相册、自建相册、收藏相册和 AI 智能相册
- OpenAI 兼容接口、模型列表、AI 周报/月报/年报和定时生成
- 本地或 S3 兼容对象存储、图片/音频/视频、缩略图、转码、波形和 OCR
- 离线编辑队列、增量同步、冲突提示、PWA 安装和移动端壳界面
- Android 原生相册、拍照、HTTPS 私有服务器切换和原生刷新会话
- 设备会话、短期访问令牌、30 天刷新会话、跨账号前端缓存隔离、邮箱验证、密码找回和恢复码
- 管理员专属邀请码查看、复制和随机轮换，AES-GCM 加密存储并要求密码二次验证
- 私密限时分享、ZIP v2 导入导出、PDF/EPUB 日记书导出
- Redis 缓存、Flyway V1-V14 迁移、真实 Actuator 健康检查和部署前治理脚本

完整功能、接口与质量门禁见 [document/系统功能文档.md](document/系统功能文档.md)、[document/API接口文档.md](document/API接口文档.md) 和 [document/测试与发布验收方案.md](document/测试与发布验收方案.md)。

## 技术要求

- JDK 17
- Maven 3.9+
- Node.js 22 LTS 和 npm
- MySQL 8.0+，Compose 默认使用 MySQL 8.4 LTS
- Redis 7+
- Docker Compose，可选但推荐用于本地基础设施
- Android 构建可选：JDK 21、Android SDK Platform 36 和 Build Tools 36
- iOS 构建可选：macOS、Xcode 26 和 Apple Developer 签名环境

## 本地运行

1. 准备环境变量：

```bash
cp .env.example .env
```

编辑 `.env`，至少替换 `DB_PASSWORD`、`MYSQL_ROOT_PASSWORD`、`INVITATION_CODE`、`INVITATION_CODE_ENCRYPTION_KEY`、`JWT_SECRET` 和 `AI_CONFIG_ENCRYPTION_KEY`。`INVITATION_CODE` 只在空数据库首次启动时导入；两个加密密钥和 JWT 密钥可使用 `openssl rand -base64 48` 生成。邮件、Web Push、S3 和媒体处理工具均为可选配置，变量说明已写在模板中。

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

## Android 客户端

Android 客户端复用同一套 Vue 页面，首次启动时由用户输入自己的 Baby Diary HTTPS 根地址；仓库和 APK 都不内置生产域名、账号或密钥。登录、刷新和退出通过 Capacitor 原生 HTTP 与 HttpOnly Cookie 桥接，普通业务请求继续使用带 Bearer Token 的统一 Axios 层。日记图片、头像、纪念日封面和共同空间图片可直接在应用内调用系统相册或相机，不需要先从系统相册分享给应用。

```bash
npm --prefix frontend ci
scripts/build-android.sh
```

脚本串行执行前端构建、`cap sync android`、Android lint、单元测试和 Debug APK 构建，并固定单 Gradle worker、无常驻 daemon，降低小型服务器上的资源峰值。产物位于 `frontend/android/app/build/outputs/apk/debug/app-debug.apk`。Android 使用 JDK 21，后端仍使用 JDK 17；不要修改系统默认 Java 来迁就其中一端。

默认客户端只接受 HTTPS。仅本机/模拟器调试时可执行 `VITE_NATIVE_ALLOW_HTTP=true scripts/build-android.sh`，此开关只允许 localhost、`10.0.2.2` 和私有局域网地址；Release Manifest 始终禁止明文流量。

项目使用固定的 Android Release 证书。私钥和密码只保存在服务器权限为 `600` 的私有文件、受校验备份及 GitHub Actions Secrets 中；仓库仅保存 `config/android-release-cert.sha256` 公钥指纹。首次初始化与同步命令如下，已经初始化后重复执行不会替换密钥：

```bash
sudo scripts/ensure-android-signing.sh
scripts/sync-android-signing-secrets.sh
scripts/build-android-release.sh
```

正式版本由 `config/android-release-version.properties` 跟踪，每次发布先提交递增的 `VERSION_CODE` 和新的 `VERSION_NAME`，不在工作流界面临时填写。`.github/workflows/android-release.yml` 会校验证书指纹和版本信息，生成具名 APK、AAB 与 `SHA256SUMS`，并发布不可覆盖的 GitHub Beta Release。Debug 包使用临时调试证书，首次切换到正式签名 Beta 时需要卸载 Debug 包一次；此后只要版本号递增即可覆盖升级。分支项目必须生成自己的密钥并更新公开证书指纹，不能复用本项目私钥。

iOS 依赖已锁定，但平台工程和真机构建必须在 Mac/Xcode 环境完成，当前 Linux 服务器不伪造 iOS 验收结果。

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

`security-scan.sh` 会先获取远端公开分支、标签、Notes 和 PR refs，检查当前非忽略文件、提交/标签元数据与全部可达 Git 历史中的未批准域名、邮箱、IP、主机路径、个人标识和敏感文件名，再执行依赖、配置和凭据扫描。图片、文档、归档和音视频等不可可靠文本扫描的资产必须经过人工检查，并记录在 `config/public-asset-allowlist.sha256`。允许公开的示例及依赖主机集中维护在 `config/privacy-host-allowlist.txt`，不得将生产地址加入该列表。

Android 原生静态检查可单独运行 `scripts/android-native.test.sh`。`scripts/verify-android-artifact.sh` 还会检查签名证书、版本、SDK、明文流量和包内服务器配置。完整 Android 构建纳入 CI；相册、相机、Cookie 持久化、返回键和签名版覆盖升级仍必须在真实设备上验收，桌面构建成功不能替代真机结论。

打包预发布、ZAP、k6、iPhone/Android PWA 真机矩阵和生产冒烟步骤见 [测试与发布验收方案](document/测试与发布验收方案.md)。自动化环境只使用合成数据和 Mock AI，不复制真实用户资料。开源发布基线与审查证据见 [开源隐私审查记录](document/开源隐私审查记录.md)。

## 生产部署

生产配置模板位于 `config/application-prod.yml`，数据库密码、JWT 密钥、邀请码加密密钥、AI 加密密钥和站点地址都必须通过服务器私有环境文件注入。邀请码初始化后以 AES-GCM 密文保存在数据库中，只有系统管理员完成密码二次验证后才能查看或刷新。生产环境默认关闭 Swagger/OpenAPI，并要求 Web 与原生 CORS 使用明确来源。部署脚本会安装 Nginx 安全头、原生媒体资源策略与后端健康代理片段、启用 systemd `PrivateTmp`，并在停止后端前完成 Nginx 配置校验；健康检查必须验证原生兼容接口并读取 Actuator JSON，确认顶层状态为 `UP`。部署示例见 [document/部署文档.md](document/部署文档.md)。

`DIARY_FILE_PATH` 只保存旧版兼容图片，`DIARY_OBJECT_PATH` 保存 V2 富媒体，两者必须是不同目录。V2 富媒体通过短时签名 URL 访问；旧版 `/images/**` 为兼容现有客户端仍可公开读取，因此文件名不可视为访问控制，建议新功能统一使用 V2 媒体接口。

项目不会跟踪以下私有内容：

- `.env`、`.env.*`、`*.env` 和本地覆盖配置
- `data/images/` 和 `data/objects/` 中的用户媒体
- `backups/`、`logs/` 和构建产物
- `.private/` 中的本机运维记录

提交前请确认测试数据、截图、日志和文档中不包含真实用户资料、域名、服务器地址或密钥。

## 安全

不要把生产环境文件、数据库 dump、图片数据或 API Key 提交到仓库。安全问题请按 [SECURITY.md](SECURITY.md) 使用 GitHub 私密漏洞报告渠道提交。

## 参与贡献

开发流程和隐私检查清单见 [CONTRIBUTING.md](CONTRIBUTING.md)。

## 许可证

本项目使用 [MIT License](LICENSE)。
