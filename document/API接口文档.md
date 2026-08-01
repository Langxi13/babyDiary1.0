# V3 API 接口文档

本文档描述当前运行时的 `/api/v3` 接口。V3 是当前唯一业务 API，使用 UUID 公共标识、空间边界和统一媒体资产模型。旧版 `/api`、`/api/v2` 以及旧图片路径不属于当前运行时；数据不变量和发布约束参阅 [统一架构运行手册](统一架构运行手册.md)。

## 基本约定

- 生产 API 通过 HTTPS 暴露，后端服务只绑定回环地址。
- JSON 请求使用 `Content-Type: application/json`；上传使用 `multipart/form-data`。
- 访问令牌放在 `Authorization: Bearer <token>`，默认有效期 15 分钟。
- 登录和刷新同时使用 HttpOnly、Secure、SameSite=Lax Cookie `baby_diary_refresh`，默认会话有效期 30 天。
- 修改密码、退出和刷新令牌轮换会使相应旧会话失效。
- 涉及锁定日记、导出、私密分享和管理员邀请码的接口，可要求 `X-Step-Up-Token`。
- 所有时间戳使用 ISO-8601；日期字段使用 `YYYY-MM-DD`，由客户端按用户时区展示。
- `204 No Content` 接口没有响应体。日记使用 `items/nextCursor/totalElements` 游标分页；时间轴批量读取可传 `includeTotal=false` 跳过重复计数。相册和 AI 报告使用 `content/pageNumber/pageSize/totalElements/totalPages` 服务端页码分页。
- 资源主标识统一为 `id`；日记使用 `diaryDate`、`contentHtml`、`mood`，媒体使用 `id` 和命名的 `representations`。关系请求中的 `diaryId`、`tagId`、`mediaIds` 等字段仅表示外键关系，不是资源响应别名。

## 响应与错误

成功响应直接返回资源对象、数组或分页对象。错误响应结构如下：

```json
{
  "type": "urn:baby-diary:problem:diary-not-found",
  "title": "Not Found",
  "status": 404,
  "detail": "日记不存在或无权访问",
  "code": "DIARY_NOT_FOUND",
  "traceId": "trace-id"
}
```

常见状态码：

| 状态码 | 含义 |
| --- | --- |
| `400` | 参数格式、校验或请求体错误 |
| `401` | 缺少、过期或无效访问令牌 |
| `403` | 当前账户或空间成员无权操作 |
| `404` | 资源不存在，或为避免泄露而隐藏资源存在性 |
| `405` | HTTP 方法不受该接口支持 |
| `406` | 无法生成客户端要求的响应媒体类型 |
| `409` | 唯一键、版本或幂等操作冲突 |
| `413` | 上传或导入超过体积限制 |
| `415` | 请求媒体类型不受支持 |
| `423` | 需要二次验证或资源已锁定 |
| `429` | 触发登录、刷新、恢复或分享限流 |
| `500` | 服务端错误；不会向客户端返回下游密钥或堆栈 |

## 客户端与认证

### 客户端启动检查

`GET /api/v3/client/bootstrap`

无需登录。返回 API 版本、会话策略、服务器版本、上传策略和经过校验的 Android 更新信息。上传策略当前声明 25 MB、8000 万像素、单篇50张及 JPEG/PNG/GIF/WebP/HEIC/HEIF 类型。原生客户端只接受 HTTPS 根地址，开发模拟器可使用受限的本地 HTTP 调试开关。

### 注册与登录

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| `POST` | `/api/v3/auth/register` | 使用邀请码注册；首个账户自动为 `ADMIN` |
| `POST` | `/api/v3/auth/login` | 登录并创建刷新会话 |
| `POST` | `/api/v3/auth/refresh` | 轮换刷新 Cookie，返回新的访问令牌 |
| `POST` | `/api/v3/auth/logout` | 撤销当前刷新会话并清除 Cookie |
| `POST` | `/api/v3/auth/step-up` | 使用当前密码取得短期二次验证令牌 |
| `GET` | `/api/v3/auth/sessions` | 查看当前账户有效设备会话 |
| `DELETE` | `/api/v3/auth/sessions/{sessionId}` | 撤销指定会话 |

登录和刷新成功体固定为 `{ token, expiresAt, userInfo }`，`userInfo` 使用账户 `id` 和 `role`，不重复返回 `accessToken`、顶层账户字段或旧角色别名。

密码与邮箱恢复：

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| `GET` | `/api/v3/account/profile` | 当前账户资料 |
| `PUT` | `/api/v3/account/profile` | 更新昵称、邮箱、时区等资料 |
| `PUT` | `/api/v3/account/avatar` | 绑定已上传的媒体资产为头像 |
| `DELETE` | `/api/v3/account/avatar` | 删除头像关系 |
| `POST` | `/api/v3/account/password` | 修改密码并撤销其他会话 |
| `PUT` | `/api/v3/account/email` | 保存邮箱并发送验证邮件 |
| `POST` | `/api/v3/auth/email/confirm` | 确认邮箱 Token |
| `POST` | `/api/v3/auth/password/reset-request` | 请求密码重置邮件，响应不泄露账户是否存在 |
| `POST` | `/api/v3/auth/password/reset` | 使用邮件 Token 重置密码 |
| `POST` | `/api/v3/auth/recovery-codes` | 重新生成一次性恢复码 |
| `POST` | `/api/v3/auth/password/recover` | 使用用户名、恢复码和新密码恢复账户 |

### 管理员邀请码

仅 `ADMIN` 可调用，且每次查看或刷新都必须携带有效 `X-Step-Up-Token`：

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| `POST` | `/api/v3/admin/invitation-code/view` | 查看当前邀请码，响应 `no-store` |
| `POST` | `/api/v3/admin/invitation-code/rotate` | 随机刷新邀请码并返回新值 |

邀请码只以 `v1:` AES-GCM 密文存储，明文不写入日志、缓存或持久化前端状态。

## 空间、日记与协作

### 空间与成员

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| `GET` / `POST` | `/api/v3/spaces` | 列出空间或创建共同空间 |
| `PUT` | `/api/v3/spaces/{spaceId}` | 所有者修改空间名称 |
| `GET` | `/api/v3/spaces/{spaceId}/members` | 查看成员 |
| `POST` | `/api/v3/spaces/{spaceId}/invitations` | 创建限时空间邀请 |
| `POST` | `/api/v3/invitations/{token}/accept` | 接受空间邀请 |
| `PUT` | `/api/v3/spaces/{spaceId}/members/{accountId}/role` | 修改成员角色 |
| `DELETE` | `/api/v3/spaces/{spaceId}/members/{accountId}` | 移除成员 |
| `GET` / `POST` | `/api/v3/spaces/{spaceId}/tags` | 列出或创建标签 |

### 日记

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| `GET` / `POST` | `/api/v3/spaces/{spaceId}/diaries` | 游标分页查询或创建日记 |
| `GET` | `/api/v3/spaces/{spaceId}/diaries/{diaryId}` | 获取完整日记详情 |
| `PUT` | `/api/v3/spaces/{spaceId}/diaries/{diaryId}` | 按 `If-Match` 版本更新日记 |
| `DELETE` | `/api/v3/spaces/{spaceId}/diaries/{diaryId}` | 移入回收站 |
| `POST` | `/api/v3/spaces/{spaceId}/diaries/{diaryId}/restore` | 从回收站恢复 |
| `DELETE` | `/api/v3/spaces/{spaceId}/diaries/{diaryId}/permanent` | 永久删除回收站日记，要求 `If-Match`；锁定日记还要求 step-up |
| `GET` | `/api/v3/spaces/{spaceId}/diaries/{diaryId}/revisions` | 查看修订历史 |
| `POST` | `/api/v3/spaces/{spaceId}/diaries/{diaryId}/revisions/{revisionId}/restore` | 恢复指定版本 |
| `GET` | `/api/v3/spaces/{spaceId}/diaries/calendar?month=YYYY-MM` | 月历粗略摘要 |
| `GET` | `/api/v3/spaces/{spaceId}/diaries/timeline` | 时间轴月份聚合 |
| `GET` | `/api/v3/spaces/{spaceId}/diaries/timeline/weeks` | 时间轴周聚合 |
| `GET` | `/api/v3/spaces/{spaceId}/search?query=&limit=` | 空间全文搜索 |
| `GET` | `/api/v3/spaces/{spaceId}/insights/yearly?year=` | 年度洞察和心情统计 |

创建或更新日记的媒体顺序由请求中的 `mediaIds` 决定；服务端在一次事务中完整替换关系，移除已有媒体关系不会延迟到下一次编辑。回收站默认保留30天，永久删除只移除日记及其关系，媒体资产仍保留在媒体库，避免误删仍被其他内容引用的文件。

修订响应使用 UUID `id`，并返回编辑者 UUID `editorId`、`editorName`、版本和创建时间；恢复路径不接受数据库 `revision_id` 自增值。

### 互动、草稿与模板

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| `GET` / `POST` | `/api/v3/spaces/{spaceId}/diaries/{diaryId}/comments` | 查看或新增评论 |
| `PUT` / `DELETE` | `/api/v3/spaces/{spaceId}/diaries/{diaryId}/comments/{commentId}` | 编辑或删除自己的评论 |
| `GET` / `PUT` | `/api/v3/spaces/{spaceId}/diaries/{diaryId}/reactions` | 查看或设置 Emoji 回应 |
| `GET` / `PUT` / `DELETE` | `/api/v3/spaces/{spaceId}/drafts/{draftKey}` | 草稿读写和删除 |
| `GET` / `POST` | `/api/v3/spaces/{spaceId}/templates` | 模板列表和创建 |
| `PUT` / `DELETE` | `/api/v3/spaces/{spaceId}/templates/{templateId}` | 模板更新和删除 |

## 媒体、相册与纪念日

### 统一媒体

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| `POST` | `/api/v3/spaces/{spaceId}/media` | 上传图片、音频或视频；可带 UUID `Idempotency-Key` |
| `GET` | `/api/v3/spaces/{spaceId}/media` | 分页查询空间媒体 |
| `GET` | `/api/v3/spaces/{spaceId}/media/{assetId}` | 查看媒体资产元数据；锁定媒体需要 step-up |
| `GET` | `/api/v3/spaces/{spaceId}/media/{assetId}/variants/{variant}` | 获取受保护派生资源 |
| `PUT` | `/api/v3/spaces/{spaceId}/media/{assetId}` | 更新媒体元数据 |
| `POST` | `/api/v3/spaces/{spaceId}/media/{assetId}/transfer` | 资产所有者将媒体归属转移给同空间有效成员 |
| `DELETE` | `/api/v3/spaces/{spaceId}/media/{assetId}` | 删除媒体资产 |
| `GET` | `/api/v3/public/media/{spaceId}/{assetId}/{variant}` | 使用短时签名 URL 读取媒体 |

媒体变体由 `variant + profile` 共同确定。资产、日记、公开分享、相册、封面、头像、评论头像和成员头像统一使用命名的 `representations`：`original`、`thumbnail`、`preview`、`poster`、`waveform`、`transcoded`。图片固定使用 `ORIGINAL/source`、`THUMBNAIL/compact` 和 `PREVIEW/screen`；派生文件因尺寸、动画或节省不足而未保存时，对应字段为 `null`，客户端按 compact、screen、source 的顺序回退。每个 representation 返回实际 `variantType`、`profile`、短时 `url` 和 `expiresAt`；完整媒体响应还返回 MIME、大小和技术元数据，紧凑嵌入响应的这些技术字段可为 `null`。不再返回 `contentUrl`、`thumbnailUrl` 或 `assetId` 响应别名。同一资产只要被任意锁定日记引用，就按受保护媒体处理；未 step-up 时还会隐藏文件名、说明、拍摄时间、MIME、大小和尺寸，且不能设为头像。

公开媒体 URL 的查询参数为 `profile`、`ticket`、`expires` 和 `signature`，其中 profile 与 HMAC 保护的访问上下文都纳入签名，客户端不得修改。旧的无上下文签名不再接受。内容读取支持 `GET`、`HEAD`、单段 `Range`、`ETag/If-None-Match`；无效 Range 返回 `416`。锁定或分享上下文使用 `Cache-Control: no-store`。

上传先写入临时文件，再校验真实文件头和声明 MIME；JPEG、PNG、GIF、WebP、HEIC、HEIF 图片上限 25 MB/8000 万像素，音视频上限 256 MB。原始文件以未经转码的字节进入不可变 `ORIGINAL/source`。首次幂等上传返回 `201`；同一空间、账户和 UUID 键的已完成重放返回 `200` 及同一媒体 ID；上传失败或媒体进入删除生命周期时释放该键。图片派生任务串行生成最长边 800 的 compact 和最长边 2048 的 screen，派生图剥离 EXIF/GPS/XMP、规范到 sRGB 且不放大；JPEG 照片使用自适应 WebP 与 SSIM 门槛，PNG/透明图使用无损 WebP，节省不足 10% 时不保存该派生。GIF/动画 WebP 只生成静态 compact，screen 回退原始动画。海报、音视频转码和波形同样由 `MEDIA_PROCESS` 后台任务生成。删除不会同步删除对象，而是标记 `DELETE_PENDING` 并排入幂等 `STORAGE_GC`；引用存在时返回 `MEDIA_IN_USE`。

### 相册与收藏

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| `GET` | `/api/v3/spaces/{spaceId}/album-groups` | 相册分组首页，只返回相册卡片和封面 |
| `POST` / `PUT` / `DELETE` | `/api/v3/spaces/{spaceId}/album-groups[/{groupId}]` | 管理相册分组 |
| `GET` | `/api/v3/spaces/{spaceId}/albums/system/{key}?page=&size=` | 查看所有图片、收藏或 `year:YYYY` 年份相册详情，服务端分页 |
| `GET` | `/api/v3/spaces/{spaceId}/albums/{albumId}?page=&size=` | 查看自建相册详情和分页媒体 |
| `POST` / `PUT` / `DELETE` | `/api/v3/spaces/{spaceId}/albums[/{albumId}]` | 管理自建相册 |
| `POST` | `/api/v3/spaces/{spaceId}/albums/{albumId}/media` | 加入媒体到相册 |
| `DELETE` | `/api/v3/spaces/{spaceId}/albums/{albumId}/media/{assetId}` | 从相册移除媒体 |
| `PUT` / `DELETE` | `/api/v3/spaces/{spaceId}/media/{assetId}/favorite` | 设置或取消收藏 |
| `POST` / `GET` / `PUT` / `DELETE` | `/api/v3/spaces/{spaceId}/ai-album-proposals[/{proposalId}]` | AI 相册提案及确认 |
| `GET` / `POST` / `PUT` / `DELETE` | `/api/v3/spaces/{spaceId}/anniversaries[/{anniversaryId}]` | 纪念日及封面资产 |

相册目录、计数、年份封面和分页查询在普通访问下统一排除受保护媒体；带有效 step-up 的查询直接读取数据库且不写入 Redis。AI 相册提案在读取、更新和确认时都会重新校验日记可见性、锁定状态及媒体关系，保存提案后发生的权限或状态变化不会成为旁路。

### 导入导出

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| `GET` | `/api/v3/spaces/{spaceId}/transfer/export` | 导出 V3 便携 ZIP 归档 |
| `POST` | `/api/v3/spaces/{spaceId}/transfer/import` | 校验并导入 V3 ZIP 归档 |
| `GET` | `/api/v3/spaces/{spaceId}/transfer/media?startDate=&endDate=` | 导出日期范围内可见日记的原图 ZIP |
| `GET` | `/api/v3/spaces/{spaceId}/books?format=PDF|EPUB` | 导出 PDF 或 EPUB 日记书 |

ZIP 导出按同一确定性顺序读取可用原图，因此 `ORIGINAL/source` 与 `ORIGINAL/default` 都会进入归档。便携归档最多包含 9,999 个媒体文件和一个清单；ZIP 导入会拒绝路径穿越、重复路径、未知版本、超大条目、超大总量和媒体校验失败，临时文件在完成后清理。便携归档和原图 ZIP 都按资产的全局锁定关系重新要求 step-up，不以当前导出日记是否锁定作为唯一判断。

## AI、通知与同步

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| `GET` / `POST` | `/api/v3/admin/ai` | 管理员读取脱敏配置或保存 OpenAI 兼容配置 |
| `POST` | `/api/v3/admin/ai/test` | 测试当前 AI 连接 |
| `GET` | `/api/v3/admin/ai/models` | 加载模型列表 |
| `GET` / `POST` / `DELETE` | `/api/v3/spaces/{spaceId}/ai-reports[/{reportId}]` | 按 `type/page/size` 分页，或生成、查看、删除周报/月报/年报 |
| `GET` / `PUT` | `/api/v3/spaces/{spaceId}/ai/schedule` | 查询或设置 AI 定时任务 |
| `GET` / `PUT` | `/api/v3/notifications`、`/api/v3/notifications/{id}/read` | 通知和已读状态 |
| `GET` / `POST` / `DELETE` | `/api/v3/notifications/push/*` | Web Push 公钥、订阅和取消订阅 |
| `GET` / `PUT` | `/api/v3/spaces/{spaceId}/reminders[/{type}]` | 写作提醒 |
| `GET` / `POST` | `/api/v3/spaces/{spaceId}/sync/pull|push` | 离线增量同步 |
| `POST` / `GET` / `DELETE` | `/api/v3/spaces/{spaceId}/diaries/{diaryId}/shares`、`/api/v3/shares/{shareId}` | 私密分享 |
| `POST` | `/api/v3/public/shares/{token}/open` | 打开公开分享 |

AI 报告从第三方观察视角生成，可使用“你”或“你们”，不会以模型第一人称冒充用户；锁定日记不会进入 AI 输入，API Key 永不回显。创建公开分享时会拒绝任何受全局锁定关系保护的媒体，打开已存在分享时还会实时复验日记和媒体状态，因此日后加锁不会使旧分享继续暴露内容。

`sync/pull` 返回 `changes`、`nextCursor`、`hasMore`、`resetRequired` 和 `baselineCursor`。每条 change 使用资源 UUID `entityId` 和操作者 UUID `actorId`，不返回账户或实体内部自增键。私人日记的可见性与所有者在写入变更时固化，日记永久删除后也不会向其他空间成员泄露变更。服务端清理超过保留期的增量日志后，过旧游标会收到 `resetRequired=true`；客户端必须清理该账户的离线读缓存，以 `baselineCursor` 重建游标，但不得丢弃仍待上传的离线操作。

## OpenAPI 与调试

生产默认关闭 Swagger/OpenAPI。开发环境可通过 `SPRINGDOC_ENABLED=true` 启用 `/v3/api-docs` 和 `/swagger-ui.html`。自动化测试必须使用合成数据和回环 AI Mock，不得连接真实 AI、邮件、对象存储或生产数据库。
