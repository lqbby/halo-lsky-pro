<div align="center">
    <img alt="logo" width="106px" src="https://github.com/ichenhe/halo-lsky-pro/assets/10266066/9dc173b0-d95e-457d-ba33-9eb2ad3e1f93">
    <h1>Halo - Lsky Pro</h1>
    <p>集成 <a href="https://www.lsky.pro/">Lsky Pro</a> 兰空图床作为 <a href="https://www.halo.run/">Halo</a> 的存储后端。</p>
    <p align="center">
        <a href="//github.com/lqbby/halo-lsky-pro/actions/workflows/ci.yaml"><img alt="GitHub Actions Workflow Status" src="https://img.shields.io/github/actions/workflow/status/lqbby/halo-lsky-pro/ci.yaml?style=flat-square&label=build" /></a>
        <a href="./LICENSE"><img alt="GitHub License" src="https://img.shields.io/github/license/ichenhe/halo-lsky-pro?style=flat-square" /></a>
    </p>
</div>

> [!NOTE]
> **这是 [ichenhe/halo-lsky-pro](https://github.com/ichenhe/halo-lsky-pro) 的 fork，在原版基础上增加了 Lsky Pro 商业版 v2 API (`/api/v2/`) 的支持。** 原版仅支持开源版 v1 API。

### 本 fork 新增功能

- 支持商业版 Lsky Pro v2 API (`/api/v2/`)，在添加存储策略时选择 API 版本即可
- v2 模式下自动使用 `storage_id` 参数名（替代 v1 的 `strategy_id`）
- 兼容 v2 API 的多种状态返回格式（`true` / `"success"`）

局限性：

- 不支持 Lsky Pro v1 旧版；同时支持开源版（v1 API: `/api/v1/`）和商业版（v2 API: `/api/v2/`），在添加存储策略时选择对应的 API 版本即可。
- 由于 Lsky Pro 限制，若启用图床端格式转换（图片压缩）将导致 Halo 中显示的附件大小不正确。
- 由于 Lsky Pro 本身的限制，只能生成一个预定义大小的缩略图，不满足 [Halo 的要求](https://github.com/halo-dev/halo/issues/8429#issuecomment-4196228369)，故本插件不支持缩略图相关功能。

## 📖 使用说明

本插件无需设置，安装后请到 Halo 后台「附件 - 存储策略」处添加策略。

### API Token

Lsky Pro 后台没有直接显示 Token 的功能，必须通过请求 API 接口获得。

**开源版（v1 API）：**

```bash
curl --location --request POST 'https://example.com/api/v1/tokens' \
--form 'email="your-email"' \
--form 'password="your-password"'
```

**商业版（v2 API）：**

商业版在后台「系统 - API Token」页面可以直接创建和管理 Token。

#### 在线 HTTP 请求工具

例如 [Getman](https://getman.cn/):

![](https://github.com/ichenhe/halo-lsky-pro/assets/10266066/94b54967-5198-4555-abf6-da9651a6bba1)


### 相册 ID

> [!NOTE]
>
> 开源版兰空图床不支持此参数，设置后无效。

此处可以指定上传图片的归属相册，留空则不指定（即不属于任何相册）。

如果 Lsky Pro 后台不显示相册 ID。打开浏览的开发者工具 (`F12`)，切换到网络 (Network) 标签页，然后再点击 「我的图片 - 相册 - 选中一个相册」，此时可以看到多了一个请求，名称形如 `images?page=1&album_id=1`，从这就可以得到相册 ID 啦。

当然也可以通过 API 获取，如果你有 curl 的话那么执行：

```bash
# 开源版 v1 API
curl https://yourdomain.com/api/v1/albums -H 'Authorization: Bearer {your-api-token}'

# 商业版 v2 API
curl https://yourdomain.com/api/v2/albums -H 'Authorization: Bearer {your-api-token}'
```

### 实例 ID

Halo 的设计非常灵活，允许安装一个插件后基于不同参数（例如不同 Lsky Pro 服务器）创建多个存储策略，故本插件需要一种方式判断某个图片（附件）与哪一个 Lsky Pro 实例关联，从而正确删除图片。「实例 ID」就是做这个用的，具体来说：

- 每个附件都会在上传时记录当前的实例 ID，并且永远不会改变。
- 即使重新安装插件，或更改图床地址，或执行其他任何操作，只要实例 ID 与附件记录的匹配就会自动关联。

实例 ID 可以是任意字符串。更改实例 ID 将导致之前上传的附件失去关联。

> [!TIP]
>
> **推荐一开始就手动设置实例 ID。**
>
> 默认生成的 ID 与 Lsky Pro 地址关联（忽略协议）。这意味着地址更换将导致之前上传的附件失去关联，从 Halo 删除时无法同步删除 Lsky Pro 中的文件。

## 建议/反馈

- **本 fork 相关问题**：请到本仓库 [issues](https://github.com/lqbby/halo-lsky-pro/issues) 反馈
- **原版插件问题**：请到上游仓库 [ichenhe/halo-lsky-pro](https://github.com/ichenhe/halo-lsky-pro) 反馈

求助请描述清楚问题，尽量附上你的配置，错误日志，故障截图等。
