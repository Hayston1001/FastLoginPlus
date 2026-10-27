# 反机器人与 Mojang 网络请求

## 连接限制

独立服在本机配置连接限制; 代理网络在代理上配置, 不会在代理后端重复执行. 这不能替代网络层攻击防护.

| 选项 | 默认值 | 单位/用途 |
| --- | --- | --- |
| `anti-bot.enabled` | `true` | 启用 FLP 连接检查 |
| `anti-bot.connections` | `600` | 全局窗口连接数 |
| `anti-bot.expire` | `10` | 全局窗口, 分钟 |
| `anti-bot.per-ip-connections` | `20` | 每 IP 长窗口连接数 |
| `anti-bot.per-ip-expire` | `5` | 每 IP 长窗口, 分钟 |
| `anti-bot.burst-limit` | `10` | 每 IP 短窗口连接数 |
| `anti-bot.burst-window` | `10` | 突发窗口, 秒 |
| `anti-bot.ban-duration` | `5` | 临时 IP 封禁, 分钟 |
| `anti-bot.trusted-ips` | 空 | 明确配置的地址跳过 FLP 连接检查 |

达到限制时, FLP 会拒绝连接并显示 `kick-antibot` 消息. 旧版的 `anti-bot.action` 选项已经移除, 不要添加回配置.

无效限制/时长会被诊断并规范化, 应使用合理正值而不是依赖回退. IP 转发错误可能让所有玩家共享代理地址并触发限制. 提高阈值或把代理 IP 整体加入信任之前, 应先检查转发.

## Mojang 请求

| 选项 | 默认值 | 用途 |
| --- | --- | --- |
| `mojang-retry-count` | `3` | 总尝试次数, 不是额外重试三次 |
| `mojang-retry-delay` | `500` | 基础等待毫秒数, 指数退避上限 10 秒 |
| `mojang-request-limit` | `600` | 每 10 分钟直连名字请求数, 限制到 `0..600` |
| `ip-addresses` | 空 | 请求使用的本地出口地址, 不能伪造任意公网 IP |
| `proxies` | 空 | HTTP 请求代理, 如 `host:port`, 不是 Minecraft 后端路由 |

FLP 独立服重试覆盖名字查询及其会话验证. 代理上只覆盖 FLP 名字查询, **不控制**代理软件原生会话握手. 限流错误不会像临时 I/O 错误一样盲目重试. 直连请求数为 `0` 时必须配置 HTTP 代理, 否则重置为 600.

后端的重试设置不会控制代理请求. 部分 Paper 兼容路径仍可能使用后端的 `ip-addresses`, `mojang-request-limit` 和 `proxies` 设置.

查询不可达或限流不代表玩家是 cracked. 检查 DNS, TLS, 出站规则和服务可用性. 关闭账号保护或反复重连可能加重限流.
