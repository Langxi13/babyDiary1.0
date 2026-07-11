# API接口文档

## 目录

- [概述](#概述)
- [认证方式](#认证方式)
- [统一响应格式](#统一响应格式)
- [错误码说明](#错误码说明)
- [接口列表](#接口列表)
  - [认证管理接口](#认证管理接口)
  - [日记管理接口](#日记管理接口)
  - [图片读取与生命周期](#图片读取与生命周期)
  - [体验增强接口](#体验增强接口)
- [V2空间协作与安全接口](#v2空间协作与安全接口)

---

## 概述

本文档描述了Baby-Diary系统的所有RESTful API接口，包括接口说明、请求参数、响应格式、错误码等详细信息。

**基础信息：**
- 旧版接口基地址：`http://localhost:10002/api`
- V2 接口基地址：`http://localhost:10002/api/v2`
- 数据格式：JSON
- 字符编码：UTF-8

---

## 认证方式

### JWT Token认证

除登录、注册、刷新、账户找回、公开分享和签名媒体读取接口外，其余接口都需要在请求头中携带 JWT Token。

**请求头格式：**
```
Authorization: Bearer {token}
```

**Token获取方式：**
当前前端通过 `/api/v2/auth/login` 获取默认15分钟的访问令牌，同时接收默认30天的 HttpOnly 刷新 Cookie。访问令牌过期后调用 `/api/v2/auth/refresh` 轮换刷新会话。旧版 `/api/auth/login` 仍返回30天 JWT，只用于兼容。

锁定日记相关接口还可能要求：

```text
X-Step-Up-Token: {通过 /api/v2/auth/step-up 获取的短期令牌}
```

---

## 统一响应格式

所有接口返回统一的响应格式：

### 成功响应

```json
{
  "code": 200,
  "message": "操作成功",
  "data": {}
}
```

### 失败响应

```json
{
  "code": 400,
  "message": "错误描述",
  "data": null
}
```

### 字段说明

| 字段 | 类型 | 说明 |
|------|------|------|
| code | Integer | 响应状态码，200表示成功，其他表示失败 |
| message | String | 响应描述信息 |
| data | Object | 响应数据，失败时为null |

---

## 错误码说明

### HTTP状态码

| 状态码 | 说明 |
|--------|------|
| 200 | 请求成功 |
| 400 | 请求参数错误 |
| 401 | 未授权，请先登录 |
| 403 | 无权限访问 |
| 404 | 资源不存在 |
| 405 | 请求方法不允许 |
| 409 | 日记版本冲突 |
| 423 | 锁定日记需要二次验证 |
| 429 | 请求过于频繁 |
| 500 | 服务器内部错误 |

### 业务错误码

| 错误码 | 说明 |
|--------|------|
| 1001 | 用户不存在 |
| 1002 | 用户名已存在 |
| 1003 | 密码不匹配 |
| 1004 | 邀请码无效 |
| 1005 | 登录失败，用户名或密码错误 |
| 1006 | Token已过期 |
| 1007 | Token无效 |
| 1008 | 邮箱已被使用 |
| 1009 | 找回凭证无效或已过期 |
| 1501-1505 | 空间、成员或邀请错误 |
| 2001 | 日记不存在 |
| 2002 | 创建日记失败 |
| 2003 | 更新日记失败 |
| 2004 | 删除日记失败 |
| 2005 | 日记版本冲突 |
| 2006 | 日记需要重新验证后访问 |
| 3001 | 文件上传失败 |
| 3002 | 文件类型不支持 |
| 3003 | 文件大小超出限制 |
| 3004 | 文件不存在 |

---

## 接口列表

### 认证管理接口

#### 1. 用户登录

**接口说明：** 通过用户名和密码登录，返回JWT Token

**请求信息：**
- 方法：POST
- 路径：`/api/auth/login`
- 认证：不需要

**请求参数：**
```json
{
  "username": "string",
  "password": "string"
}
```

**响应示例：**
```json
{
  "code": 200,
  "message": "登录成功",
  "data": {
    "token": "example-jwt-token",
    "expiresIn": 2592000000,
    "userInfo": {
      "userId": 1,
      "username": "user1",
      "avatarPath": "/images/avatar.jpg"
    }
  }
}
```

---

#### 2. 用户注册

**接口说明：** 通过用户名、密码和邀请码注册新用户

**请求信息：**
- 方法：POST
- 路径：`/api/auth/register`
- 认证：不需要

**请求参数：**
```json
{
  "username": "string",
  "password": "string",
  "confirmPassword": "string",
  "invitationCode": "string"
}
```

**响应示例：**
```json
{
  "code": 200,
  "message": "注册成功",
  "data": null
}
```

---

#### 3. 用户登出

**接口说明：** 用户登出，清除认证信息

**请求信息：**
- 方法：POST
- 路径：`/api/auth/logout`
- 认证：需要

**响应示例：**
```json
{
  "code": 200,
  "message": "登出成功",
  "data": null
}
```

---

#### 4. 获取当前用户信息

**接口说明：** 获取当前登录用户的详细信息

**请求信息：**
- 方法：GET
- 路径：`/api/auth/info`
- 认证：需要

**响应示例：**
```json
{
  "code": 200,
  "message": "操作成功",
  "data": {
    "userId": 1,
    "username": "user1",
    "avatarPath": "/images/avatar.jpg"
  }
}
```

---

### 日记管理接口

#### 1. 获取日记列表

**接口说明：** 分页获取日记列表，支持日期范围、关键字、标签和心情搜索。前端列表默认使用 `summary=true` 获取轻量摘要，详情页仍使用单篇接口获取完整正文。

**请求信息：**
- 方法：GET
- 路径：`/api/diaries`
- 认证：需要

**请求参数：**

| 参数名 | 类型 | 必填 | 说明 |
|--------|------|------|------|
| startDate | String | 否 | 开始日期，格式yyyy-MM-dd，默认2022-01-01 |
| endDate | String | 否 | 结束日期，格式yyyy-MM-dd，默认当天 |
| keyword | String | 否 | 搜索关键字，最多200个字符 |
| tagId | Integer | 否 | 标签ID |
| moodKey | String | 否 | 心情标识 |
| page | Integer | 否 | 页码，从0开始，默认0 |
| size | Integer | 否 | 每页大小，默认5，最大100 |
| summary | Boolean | 否 | 是否返回轻量摘要，默认false；为true时正文只返回预览片段 |

**响应示例：**
```json
{
  "code": 200,
  "message": "操作成功",
  "data": {
    "content": [
      {
        "diaryId": 1,
        "userId": 1,
        "title": "第一次周末旅行",
        "date": "2024-01-01",
        "content": "今天一起去了江边散步...",
        "imagePathList": [
          "diary_1_4f7f89d2a45f4e3199138e8784af8821.jpg",
          "diary_1_7b6e34de91c84cb68cd77b6dc01a6320.jpg"
        ],
        "createdAt": "2024-01-01T12:00:00"
      }
    ],
    "pageNumber": 0,
    "pageSize": 5,
    "totalElements": 10
  }
}
```

---

#### 2. 获取日记详情

**接口说明：** 根据ID获取日记详细信息

**请求信息：**
- 方法：GET
- 路径：`/api/diaries/{diaryId}`
- 认证：需要

**路径参数：**

| 参数名 | 类型 | 必填 | 说明 |
|--------|------|------|------|
| diaryId | Integer | 是 | 日记ID |

**响应示例：**
```json
{
  "code": 200,
  "message": "操作成功",
  "data": {
    "diaryId": 1,
    "userId": 1,
    "title": "第一次周末旅行",
    "date": "2024-01-01",
    "content": "今天一起去了江边散步...",
    "imagePathList": [
      "diary_1_4f7f89d2a45f4e3199138e8784af8821.jpg"
    ],
    "createdAt": "2024-01-01T12:00:00"
  }
}
```

---

#### 3. 创建日记

**接口说明：** 创建新日记，支持多图片上传

**请求信息：**
- 方法：POST
- 路径：`/api/diaries`
- 认证：需要
- Content-Type：multipart/form-data

**请求参数：**

| 参数名 | 类型 | 必填 | 说明 |
|--------|------|------|------|
| title | String | 是 | 日记标题 |
| content | String | 是 | 日记内容 |
| date | String | 是 | 日记日期，格式yyyy-MM-dd |
| contentFormat | String | 否 | `plain` 或 `html`，默认 `plain` |
| moodKey | String | 否 | 心情标识，最多32个字符 |
| tagIds | String | 否 | 逗号分隔的标签ID，最多50个 |
| imageFiles | MultipartFile[] | 否 | 图片文件数组 |

旧版日记图片接口保持单张10MB限制；该限制在读取图片到内存前执行。图片、音频和视频的大文件上传请使用 V2 富媒体接口。

**响应示例：**
```json
{
  "code": 200,
  "message": "创建成功",
  "data": {
    "diaryId": 1,
    "userId": 1,
    "title": "第一次周末旅行",
    "date": "2024-01-01",
    "content": "今天一起去了江边散步...",
    "imagePathList": [
      "diary_1_4f7f89d2a45f4e3199138e8784af8821.jpg"
    ],
    "createdAt": "2024-01-01T12:00:00"
  }
}
```

**图片压缩说明：**
- 仅支持 JPEG、PNG、GIF、WebP，后端同时校验文件签名
- 单张最大10MB，单篇日记最多50张图片
- 超过300KB的图片会自动压缩
- 最大尺寸限制为1920px
- 压缩质量为0.85

---

#### 4. 更新日记（PUT方式）

**接口说明：** 更新指定日记，支持更新图片

**请求信息：**
- 方法：PUT
- 路径：`/api/diaries/{diaryId}`
- 认证：需要
- Content-Type：multipart/form-data

**路径参数：**

| 参数名 | 类型 | 必填 | 说明 |
|--------|------|------|------|
| diaryId | Integer | 是 | 日记ID |

**请求参数：**

| 参数名 | 类型 | 必填 | 说明 |
|--------|------|------|------|
| title | String | 是 | 日记标题 |
| content | String | 是 | 日记内容 |
| date | String | 是 | 日记日期，格式yyyy-MM-dd |
| contentFormat | String | 否 | `plain` 或 `html`，默认 `plain` |
| moodKey | String | 否 | 心情标识，最多32个字符 |
| tagIds | String | 否 | 逗号分隔的标签ID，最多50个 |
| imageFiles | MultipartFile[] | 否 | 图片文件数组 |
| retainedImagePaths | String[] | 否 | 需要保留的旧图片文件名，可重复提交 |
| imageOrder | String[] | 否 | 图片排序，可重复提交；旧图片使用 `existing:<filename>`，新图片使用 `new:<index>` |
| clearImages | Boolean | 否 | 是否清除原有图片，默认false |

**图片排序说明：**
- 提交 `retainedImagePaths` 时，它是旧图片保留白名单，未提交的旧图片会在更新时删除；完全省略该字段时会保留现有图片，兼容只追加新图的客户端。
- `imageOrder` 用于持久化图片展示顺序，支持旧图片和本次新上传图片混排。
- `existing:<filename>` 表示保留的旧图片文件名，例如 `existing:diary_1_4f7f89d2a45f4e3199138e8784af8821.jpg`。
- `new:<index>` 表示本次请求中 `imageFiles` 的有效新图片序号，从 `0` 开始，例如 `new:0`。
- `imageOrder` 缺失、重复或包含无效项时，后端会忽略无效项，并把未覆盖的保留旧图和新图追加到末尾，兼容旧客户端。

**响应示例：**
```json
{
  "code": 200,
  "message": "更新成功",
  "data": {
    "diaryId": 1,
    "userId": 1,
    "title": "第一次周末旅行（更新）",
    "date": "2024-01-01",
    "content": "今天一起去了江边散步...（已更新）",
    "imagePathList": [
      "diary_1_7b6e34de91c84cb68cd77b6dc01a6320.jpg"
    ],
    "createdAt": "2024-01-01T12:00:00"
  }
}
```

---

#### 5. 更新日记（POST方式）

**接口说明：** 使用POST方式更新日记，支持multipart/form-data

**请求信息：**
- 方法：POST
- 路径：`/api/diaries/{diaryId}/update`
- 认证：需要
- Content-Type：multipart/form-data

其他参数与[更新日记（PUT方式）](#4-更新日记put方式)相同。

---

#### 6. 删除日记

**接口说明：** 删除指定日记及其关联图片记录，并尽量清理服务器图片文件。单个历史图片文件清理失败不会阻断日记删除，后端会记录日志。

**请求信息：**
- 方法：DELETE
- 路径：`/api/diaries/{diaryId}`
- 认证：需要

**路径参数：**

| 参数名 | 类型 | 必填 | 说明 |
|--------|------|------|------|
| diaryId | Integer | 是 | 日记ID |

**响应示例：**
```json
{
  "code": 200,
  "message": "删除成功",
  "data": null
}
```

---

#### 7. 导出图片

**接口说明：** 导出指定日期范围内的图片为ZIP文件

**请求信息：**
- 方法：GET
- 路径：`/api/diaries/export`
- 认证：需要

**请求参数：**

| 参数名 | 类型 | 必填 | 说明 |
|--------|------|------|------|
| startDate | String | 是 | 开始日期，格式yyyy-MM-dd |
| endDate | String | 是 | 结束日期，格式yyyy-MM-dd |

**响应：**
返回ZIP文件流，Content-Disposition为attachment。服务端导出临时文件会在响应流关闭后自动删除。

---

### 图片读取与生命周期

图片不提供独立上传或删除 API。日记、头像和纪念日封面上传统一使用随机服务端文件名；删除或替换由对应业务事务管理。原图和缩略图的静态读取方式见下文“图片读取”。

---

### 体验增强接口

以下接口均需要 JWT 认证。

#### 标签

- `GET /api/tags`：获取当前用户标签列表
- `POST /api/tags`：创建标签，请求体：`{"name":"约会","color":"#ff7a90"}`；名称最多32个字符，颜色使用6位或8位十六进制格式

#### 日记筛选和聚合

- `GET /api/diaries?tagId=&moodKey=`：日记列表支持标签和心情筛选
- `GET /api/diaries?summary=true`：列表轻量模式，只返回正文预览片段，适合首页和列表页
- `GET /api/diaries/timeline?year=&month=&tagId=&moodKey=`：按月份返回时间轴
- `GET /api/diaries/calendar?year=2026&month=6`：返回指定月份每天日记数量

创建和更新日记的 `multipart/form-data` 新增字段：

| 字段 | 类型 | 说明 |
|------|------|------|
| contentFormat | String | `plain` 或 `html` |
| moodKey | String | 固定心情标识 |
| tagIds | String | 逗号分隔的标签ID，如 `1,2,3` |

#### 纪念日

- `GET /api/anniversaries`
- `POST /api/anniversaries/cover`：上传纪念日封面图片，`multipart/form-data` 字段名为 `coverFile`
- `POST /api/anniversaries`
- `PUT /api/anniversaries/{anniversaryId}`
- `DELETE /api/anniversaries/{anniversaryId}`

请求体：

```json
{
  "title": "第一次旅行",
  "date": "2025-07-13",
  "description": "夏天的旅行",
  "coverImagePath": "optional.jpg",
  "sort": 0
}
```

封面上传响应：

```json
{
  "coverImagePath": "anniversary_3_44f4575d0d964498907ca13d1dc31c4e.jpg"
}
```

#### 相册和收藏

- `GET /api/photos?startDate=&endDate=&tagId=&moodKey=&favoriteOnly=false`
- `GET /api/photos/page?startDate=&endDate=&tagId=&moodKey=&favoriteOnly=false&page=0&size=24`：分页读取照片，前端首页和大图集优先使用该接口；每页最多100张。
- `POST /api/photos/{imageId}/favorite`
- `DELETE /api/photos/{imageId}/favorite`
- `GET /api/albums/groups`
- `GET /api/albums/system/all/photos`
- `GET /api/albums/system/favorites/photos`
- `GET /api/albums/system/year/{year}/photos`
- `GET /api/albums/system/all/photos/page?page=0&size=24`
- `GET /api/albums/system/favorites/photos/page?page=0&size=24`
- `GET /api/albums/system/year/{year}/photos/page?page=0&size=24`
- `GET /api/albums/{albumId}/photos/page?page=0&size=24`：分页读取手动或 AI 相册照片，并校验相册和照片属于当前用户。

分页照片接口统一返回 `PageResult<PhotoVO>`，包含 `content`、`pageNumber`、`pageSize`、`totalElements`、`totalPages`、`first` 和 `last`。未带 `/page` 的照片接口保留用于兼容已有调用，新页面应优先使用分页接口。

#### 草稿

- `GET /api/diary-drafts`
- `GET /api/diary-drafts/{draftKey}`
- `PUT /api/diary-drafts`
- `DELETE /api/diary-drafts/{draftId}`
- `DELETE /api/diary-drafts/key/{draftKey}`

草稿请求体：

```json
{
  "draftKey": "create",
  "diaryId": null,
  "title": "草稿标题",
  "date": "2026-06-08",
  "content": "<p>内容</p>",
  "contentFormat": "html",
  "moodKey": "happy",
  "tagIds": [1, 2]
}
```

#### AI 报告

- `GET /api/ai/config`：读取 AI 配置，API Key 只返回脱敏状态。
- `PUT /api/ai/config`：保存 AI 配置，请求体如下；`apiKey` 为空时保留旧 Key。
- `POST /api/ai/config/test`：使用当前配置发起一次短连接测试。
- `GET /api/ai/models`：使用已保存的 Base URL 和 API Key 通过后端加载模型 ID 列表。
- `POST /api/ai/reports/generate`：生成并保存周报/月报。
- `GET /api/ai/reports?type=&page=&size=`：分页查看历史报告，每页最多100条。
- `GET /api/ai/reports/{reportId}`：查看报告详情。
- `DELETE /api/ai/reports/{reportId}`：删除自己的报告。

AI 配置请求体：

```json
{
  "enabled": true,
  "baseUrl": "https://api.openai.com/v1",
  "model": "gpt-4o-mini",
  "apiKey": "sk-...",
  "timeoutSeconds": 30
}
```

报告生成请求体：

```json
{
  "type": "WEEKLY",
  "period": "2026-W27"
}
```

`type` 支持 `WEEKLY`、`MONTHLY` 和 `ANNUAL`；周报周期格式为 `yyyy-Www`，月报为 `yyyy-MM`，年报为 `yyyy`。AI 输入只包含允许参与总结的日记日期、标题、文字内容、心情和标签，不发送图片内容，锁定日记不会进入 AI 输入。

AI `baseUrl` 只接受 HTTP/HTTPS 地址，必须包含主机，且不能带账号信息、查询参数或片段；`timeoutSeconds` 范围为5到120秒。AI 下游错误不会把响应正文或客户端内部异常详情返回给浏览器。

前端对模型列表、连接测试和报告生成使用“配置超时时间 + 10秒传输余量”的请求超时，避免后端允许等待120秒但浏览器固定30秒提前报网络错误。历史报告页面默认每次读取10份并支持继续加载。

AI 相册提案编辑后，确认阶段会重新校验全部照片属于当前用户；单次提案最多确认1000张去重照片。

图片不再提供独立的 `/api/images/upload`、`/api/images/{filename}` 删除接口。图片随日记创建/更新接口上传和删除，后端负责压缩、写入 `diary_image` 记录，并在删除日记或移除图片时清理文件。

**图片读取：**
- 方法：GET
- 路径：`/images/{filename}`
- 缩略图路径：`/images/thumbs/480/{filename}`
- 认证：不需要
- 服务方式：Nginx 直接读取 `DIARY_FILE_PATH` 指定的图片目录
- 权限要求：`data` 和 `data/images` 目录需允许 Nginx 组 `www-data` 进入读取；生产环境由 `scripts/ensure-image-permissions.sh` 维护权限
- 返回结构：日记、相册和图片接口仍只返回原始 `imagePath` 文件名；前端根据文件名自行拼接缩略图路径，图片预览和导出仍使用原图。

**编辑日记时保留旧图片：**

`POST /api/diaries/{diaryId}/update` 和 `PUT /api/diaries/{diaryId}` 支持在 `multipart/form-data` 中重复提交 `retainedImagePaths` 字段。提交该字段时，未出现在白名单中的旧图片会被删除；省略该字段时保留现有图片。如需清空全部旧图，提交 `clearImages=true`。编辑页同时提交 `imageOrder` 字段来保存最终图片顺序，格式为 `existing:<filename>` 或 `new:<index>`。

---

## V2空间协作与安全接口

V2 接口仍使用统一 `Result<T>` 响应；文件下载和签名媒体读取直接返回二进制内容。空间、日记、媒体、搜索、AI 和导入导出接口都会校验当前用户的空间成员关系。

### V2认证与账户

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/v2/auth/login` | 登录，返回访问令牌并设置旋转刷新 Cookie |
| POST | `/api/v2/auth/refresh` | 使用 Cookie 轮换刷新会话并获取新访问令牌 |
| POST | `/api/v2/auth/logout` | 撤销当前刷新会话并清除 Cookie |
| GET | `/api/v2/auth/sessions` | 查看有效设备会话 |
| DELETE | `/api/v2/auth/sessions/{sessionId}` | 撤销指定设备会话 |
| DELETE | `/api/v2/auth/sessions` | 撤销全部设备会话 |
| POST | `/api/v2/auth/step-up` | 使用当前密码获取日记锁短期令牌 |
| PUT | `/api/v2/auth/email` | 保存邮箱并发送验证邮件 |
| POST | `/api/v2/auth/email/confirm` | 使用邮件 Token 验证邮箱，无需登录 |
| POST | `/api/v2/auth/password/reset-request` | 请求密码重置邮件，始终返回相同结果 |
| POST | `/api/v2/auth/password/reset` | 使用邮件 Token 重置密码 |
| POST | `/api/v2/auth/recovery-codes` | 校验当前密码并重新生成8个一次性恢复码 |
| POST | `/api/v2/auth/password/recover` | 使用用户名、恢复码和新密码找回账户 |

刷新 Cookie 名为 `baby_diary_refresh`，路径为 `/api/v2/auth`，生产环境使用 `Secure`、`HttpOnly`、`SameSite=Lax`。密码修改、邮件找回或恢复码找回会撤销全部刷新会话。

### 空间与成员

| 方法 | 路径 | 说明 |
|------|------|------|
| GET/POST | `/api/v2/spaces` | 列出空间或创建共同空间 |
| PUT | `/api/v2/spaces/{spaceId}` | 所有者修改空间名称 |
| GET | `/api/v2/spaces/{spaceId}/members` | 查看成员 |
| POST | `/api/v2/spaces/{spaceId}/invitations` | 创建限时邀请 |
| POST | `/api/v2/spaces/invitations/{token}/accept` | 接受邀请 |
| PUT | `/api/v2/spaces/{spaceId}/members/{userId}/role?role=OWNER|MEMBER` | 修改成员角色 |
| DELETE | `/api/v2/spaces/{spaceId}/members/{userId}` | 移除成员 |
| GET/POST | `/api/v2/spaces/{spaceId}/tags` | 列出或创建空间标签 |

### V2日记、历史与互动

| 方法 | 路径 | 说明 |
|------|------|------|
| GET/POST | `/api/v2/spaces/{spaceId}/diaries` | 分页查询或创建日记 |
| GET/PUT/DELETE | `/api/v2/spaces/{spaceId}/diaries/{diaryId}` | 详情、更新或移入回收站 |
| POST | `/api/v2/spaces/{spaceId}/diaries/{diaryId}/restore` | 从回收站恢复 |
| GET | `/api/v2/spaces/{spaceId}/diaries/{diaryId}/revisions` | 查看修订历史 |
| POST | `/api/v2/spaces/{spaceId}/diaries/{diaryId}/revisions/{revisionId}/restore` | 恢复指定版本 |
| GET/POST | `/api/v2/spaces/{spaceId}/diaries/{diaryId}/comments` | 查看或新增评论 |
| PUT/DELETE | `/api/v2/spaces/{spaceId}/diaries/{diaryId}/comments/{commentId}` | 编辑或删除自己的评论 |
| GET/PUT | `/api/v2/spaces/{spaceId}/diaries/{diaryId}/reactions` | 查看或设置 Emoji 回应 |

创建/更新请求使用 `DiaryWriteDTO`，包含 `clientId`、`title`、`date`、`content`、`contentFormat`、`moodKey`、`visibility`、`locked`、`baseVersion` 和 `tagIds`。更新、删除、恢复和恢复历史版本必须带当前版本号；版本不一致返回 HTTP 409。锁定内容未完成二次验证时返回 HTTP 423，并且列表仅返回脱敏占位。

### 搜索、模板、洞察与同步

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/v2/spaces/{spaceId}/search?query=&limit=` | 中文全文搜索，最多返回受限结果 |
| GET/POST | `/api/v2/spaces/{spaceId}/templates` | 列出或创建日记模板 |
| PUT/DELETE | `/api/v2/spaces/{spaceId}/templates/{templateId}` | 更新或删除模板 |
| GET | `/api/v2/spaces/{spaceId}/insights/yearly?year=` | 年度日历、月份和心情统计 |
| GET | `/api/v2/spaces/{spaceId}/sync/pull?cursor=&limit=` | 按游标拉取增量变更 |
| POST | `/api/v2/spaces/{spaceId}/sync/push` | 批量提交幂等离线操作 |

同步操作携带唯一 `operationId`、实体类型、动作、实体 ID、基础版本和载荷。服务端返回 `APPLIED`、`CONFLICT`、`RETRYABLE` 等状态；锁定日记的 pull 结果不包含明文。

### 富媒体

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/v2/spaces/{spaceId}/media` | 上传图片、音频或视频，可绑定日记 |
| GET/PUT/DELETE | `/api/v2/spaces/{spaceId}/media/{assetId}` | 查看、修改元数据或软删除媒体 |
| GET | `/api/v2/media/public/{assetId}/{variant}?expires=&signature=` | 读取短时签名媒体，无需 JWT |

上传使用 `multipart/form-data`，字段包括 `file`、可选 `diaryId`、`caption`、`takenAt`、`locationName`、`latitude`、`longitude`。图片最大25MB，音视频最大256MB；绑定锁定日记时必须提供 `X-Step-Up-Token`。`variant` 支持 `original`、`thumbnail`、`poster`、`waveform`、`transcoded`。

### 通知、提醒、AI、分享与迁移

| 方法 | 路径 | 说明 |
|------|------|------|
| GET/PUT | `/api/v2/notifications...` | 通知列表、未读数、单条/全部已读 |
| GET/POST/DELETE | `/api/v2/notifications/push...` | 获取 VAPID 公钥、订阅或取消 Web Push |
| GET/PUT | `/api/v2/spaces/{spaceId}/reminders...` | 查询并设置 DAILY/WEEKLY 写作提醒 |
| POST/GET | `/api/v2/spaces/{spaceId}/ai/reports...` | 生成、分页和查看空间 AI 报告 |
| GET/PUT | `/api/v2/spaces/{spaceId}/ai/schedule` | 查询或由所有者设置 AI 定时任务 |
| POST/GET | `/api/v2/spaces/{spaceId}/diaries/{diaryId}/shares` | 创建或列出活动私密分享 |
| DELETE | `/api/v2/shares/{shareId}` | 撤销自己创建的分享 |
| POST | `/api/v2/public/shares/{token}/open` | 使用可选密码打开公开分享 |
| GET/POST | `/api/v2/spaces/{spaceId}/transfer/export|import` | ZIP v2 导出或导入 |
| GET | `/api/v2/spaces/{spaceId}/books?format=PDF|EPUB...` | 导出日记书 |

ZIP 导入限制文件数量、单项大小和总解压大小，并阻止目录穿越。导出、导入、日记书和锁定日记分享使用 `X-Step-Up-Token` 保护敏感内容；锁定日记中的旧版图片在导入时转存到私有 V2 媒体存储。

---

## 附录

### Swagger文档

系统已集成 Swagger/OpenAPI 文档。生产配置默认设置 `SPRINGDOC_ENABLED=false`，需要临时启用并限制到管理网络后才能访问：

- Swagger UI：`http://localhost:10002/swagger-ui.html`
- OpenAPI JSON：`http://localhost:10002/v3/api-docs`

### 测试建议

1. 使用Postman或类似工具进行接口测试
2. 先通过登录接口获取Token
3. 在后续请求的Header中添加Authorization: Bearer {token}
4. 注意图片上传接口需要使用multipart/form-data格式
