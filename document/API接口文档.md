# V3 API 接口文档

本文档描述当前运行时的 `/api/v3` 接口。V3 是当前唯一业务 API，使用 UUID 公共标识、空间边界和统一媒体资产模型。旧版 `/api`、`/api/v2` 以及旧图片路径不属于当前运行时；历史数据迁移请参阅 [V3 统一架构与迁移运行手册](统一架构运行手册.md)。

## 基本约定

- 生产 API 通过 HTTPS 暴露，后端服务只绑定回环地址。
- JSON 请求使用 `Content-Type: application/json`；上传使用 `multipart/form-data`。
- 访问令牌放在 `Authorization: Bearer <token>`，默认有效期 15 分钟。
- 登录和刷新同时使用 HttpOnly、Secure、SameSite=Lax Cookie `baby_diary_refresh`，默认会话有效期 30 天。
- 修改密码、退出和刷新令牌轮换会使相应旧会话失效。
- 涉及锁定日记、导出、私密分享和管理员邀请码的接口，可要求 `X-Step-Up-Token`。
- 所有时间戳使用 ISO-8601；日期字段使用 `YYYY-MM-DD`，由客户端按用户时区展示。
- `204 No Content` 接口没有响应体。分页接口使用 `items`、`nextCursor` 和 `totalElements`。
- 资源主标识统一为 `id`；日记使用 `diaryDate`、`contentHtml`、`mood`，媒体使用 `id` 和命名的 `representations`。关系请求中的 `diaryId`、`tagId`、`mediaIds` 等字段仅表示外键关系，不是资源响应别名。

## 响应与错误

成功响应直接返回资源对象、数组或分页对象。错误响应结构如下：

```json
{
  "code": "DIARY_NOT_FOUND",
  "message": "日记不存在或无权访问",
  "requestId": "request-id"
}
```

常见状态码：

| 状态码 | 含义 |
| --- | --- |
| `400` | 参数格式、校验或请求体错误 |
| `401` | 缺少、过期或无效访问令牌 |
| `403` | 当前账户或空间成员无权操作 |
| `404` | 资源不存在，或为避免泄露而隐藏资源存在性 |
| `409` | 唯一键、版本或幂等操作冲突 |
| `413` | 上传或导入超过体积限制 |
| `423` | 需要二次验证或资源已锁定 |
| `429` | 触发登录、刷新、恢复或分享限流 |
| `500` | 服务端错误；不会向客户端返回下游密钥或堆栈 |

## 客户端与认证

### 客户端启动检查

`GET /api/v3/client/bootstrap`

无需登录。返回 API 版本、会话策略、原生来源要求和经过校验的 Android 更新信息。原生客户端只接受 HTTPS 根地址，开发模拟器可使用受限的本地 HTTP 调试开关。

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
| `DELETE` | `/api/v3/auth/sessions` | 撤销当前账户全部会话 |

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
| `GET` | `/api/v3/spaces/{spaceId}/diaries/{diaryId}/revisions` | 查看修订历史 |
| `POST` | `/api/v3/spaces/{spaceId}/diaries/{diaryId}/revisions/{revisionId}/restore` | 恢复指定版本 |
| `GET` | `/api/v3/spaces/{spaceId}/diaries/calendar?month=YYYY-MM` | 月历粗略摘要 |
| `GET` | `/api/v3/spaces/{spaceId}/diaries/timeline` | 时间轴月份聚合 |
| `GET` | `/api/v3/spaces/{spaceId}/diaries/timeline/weeks` | 时间轴周聚合 |
| `GET` | `/api/v3/spaces/{spaceId}/search?query=&limit=` | 空间全文搜索 |
| `GET` | `/api/v3/spaces/{spaceId}/insights/yearly?year=` | 年度洞察和心情统计 |

创建或更新日记的媒体顺序由请求中的 `mediaIds` 决定；服务端在一次事务中更新关系，删除旧媒体关系不会延迟到下一次编辑。

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
| `POST` | `/api/v3/spaces/{spaceId}/media` | 上传图片、音频或视频 |
| `GET` | `/api/v3/spaces/{spaceId}/media` | 分页查询空间媒体 |
| `GET` | `/api/v3/spaces/{spaceId}/media/{assetId}` | 查看媒体资产元数据；锁定媒体需要 step-up |
| `GET` | `/api/v3/spaces/{spaceId}/media/{assetId}/variants/{variant}` | 获取受保护派生资源 |
| `PUT` | `/api/v3/spaces/{spaceId}/media/{assetId}` | 更新媒体元数据 |
| `DELETE` | `/api/v3/spaces/{spaceId}/media/{assetId}` | 删除媒体资产 |
| `GET` | `/api/v3/public/media/{spaceId}/{assetId}/{variant}` | 使用短时签名 URL 读取媒体 |

媒体变体由 `variant + profile` 共同确定。资产、相册、封面、头像等媒体响应使用命名的 `representations`：`original`、`thumbnail`、`poster`、`waveform`、`transcoded`。每个 representation 返回实际 `variantType`、`profile`、短时 `url`、`expiresAt`、MIME、大小和技术元数据；缺少的派生 representation 为 `null`。日记媒体为了保持列表响应紧凑，返回原图 `contentUrl` 和缩略图 `thumbnailUrl`，但 URL 同样绑定实际 profile。锁定媒体仍可在列表中显示不含 URL 的技术占位，直接详情和内容读取需要 `X-Step-Up-Token`。

公开媒体 URL 的查询参数为 `profile`、`ticket`、`expires` 和 `signature`，其中 profile 与 HMAC 保护的访问上下文都纳入签名，客户端不得修改。旧的无上下文签名不再接受。内容读取支持 `GET`、`HEAD`、单段 `Range`、`ETag/If-None-Match`；无效 Range 返回 `416`。锁定或分享上下文使用 `Cache-Control: no-store`。

上传先写入临时文件，再校验真实文件头和声明 MIME；图片上限 25 MB/8000 万像素，音视频上限 256 MB。原始文件进入 `ORIGINAL/source`，缩略图、海报、转码和波形由 `MEDIA_PROCESS` 后台任务生成。删除不会同步删除对象，而是标记 `DELETE_PENDING` 并排入幂等 `STORAGE_GC`；引用存在时返回 `MEDIA_IN_USE`。

### 相册与收藏

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| `GET` | `/api/v3/spaces/{spaceId}/album-groups` | 相册分组首页，只返回相册卡片和封面 |
| `POST` / `PUT` / `DELETE` | `/api/v3/spaces/{spaceId}/album-groups[/{groupId}]` | 管理相册分组 |
| `GET` | `/api/v3/spaces/{spaceId}/albums/system/{key}?page=&size=` | 查看所有图片或收藏相册详情，服务端分页 |
| `GET` | `/api/v3/spaces/{spaceId}/albums/{albumId}?page=&size=` | 查看自建相册详情和分页媒体 |
| `POST` / `PUT` / `DELETE` | `/api/v3/spaces/{spaceId}/albums[/{albumId}]` | 管理自建相册 |
| `POST` | `/api/v3/spaces/{spaceId}/albums/{albumId}/media` | 加入媒体到相册 |
| `DELETE` | `/api/v3/spaces/{spaceId}/albums/{albumId}/media/{assetId}` | 从相册移除媒体 |
| `PUT` / `DELETE` | `/api/v3/spaces/{spaceId}/media/{assetId}/favorite` | 设置或取消收藏 |
| `POST` / `GET` / `PUT` / `DELETE` | `/api/v3/spaces/{spaceId}/ai-album-proposals[/{proposalId}]` | AI 相册提案及确认 |
| `GET` / `POST` / `PUT` / `DELETE` | `/api/v3/spaces/{spaceId}/anniversaries[/{anniversaryId}]` | 纪念日及封面资产 |

### 导入导出

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| `GET` | `/api/v3/spaces/{spaceId}/transfer/export` | 导出 V3 便携 ZIP 归档 |
| `POST` | `/api/v3/spaces/{spaceId}/transfer/import` | 校验并导入 V3 ZIP 归档 |
| `GET` | `/api/v3/spaces/{spaceId}/books?format=PDF|EPUB` | 导出 PDF 或 EPUB 日记书 |

ZIP 导出按同一确定性顺序读取可用原图，因此迁移保留的 `ORIGINAL/source` 与新上传的 `ORIGINAL/default` 都会进入归档。ZIP 导入会拒绝路径穿越、重复路径、未知版本、超大条目、超大总量和媒体校验失败；临时文件在完成后清理。

## AI、通知与同步

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| `GET` / `POST` | `/api/v3/admin/ai` | 管理员读取脱敏配置或保存 OpenAI 兼容配置 |
| `POST` | `/api/v3/admin/ai/test` | 测试当前 AI 连接 |
| `GET` | `/api/v3/admin/ai/models` | 加载模型列表 |
| `GET` / `POST` / `DELETE` | `/api/v3/spaces/{spaceId}/ai-reports[/{reportId}]` | 生成、查看、删除周报/月报/年报 |
| `GET` / `PUT` | `/api/v3/spaces/{spaceId}/ai/schedule` | 查询或设置 AI 定时任务 |
| `GET` / `PUT` | `/api/v3/notifications`、`/api/v3/notifications/{id}/read` | 通知和已读状态 |
| `GET` / `POST` / `DELETE` | `/api/v3/notifications/push/*` | Web Push 公钥、订阅和取消订阅 |
| `GET` / `PUT` | `/api/v3/spaces/{spaceId}/reminders[/{type}]` | 写作提醒 |
| `GET` / `POST` | `/api/v3/spaces/{spaceId}/sync/pull|push` | 离线增量同步 |
| `POST` / `GET` / `DELETE` | `/api/v3/spaces/{spaceId}/diaries/{diaryId}/shares`、`/api/v3/shares/{shareId}` | 私密分享 |
| `POST` | `/api/v3/public/shares/{token}/open` | 打开公开分享 |

AI 报告从第三方观察视角生成，可使用“你”或“你们”，不会以模型第一人称冒充用户；锁定日记不会进入 AI 输入，API Key 永不回显。

## OpenAPI 与调试

生产默认关闭 Swagger/OpenAPI。开发环境可通过 `SPRINGDOC_ENABLED=true` 启用 `/v3/api-docs` 和 `/swagger-ui.html`。自动化测试必须使用合成数据和回环 AI Mock，不得连接真实 AI、邮件、对象存储或生产数据库。
