# BungeeCord 与 Spigot/Paper/Folia

本页适用于 BungeeCord + Bukkit/Paper/Folia 认证后端, 不适用于 Fabric. 兼容 Bungee 的分支也应根据实际 API 和转发实现测试.

## 安装前

检查[运行要求](../overview/requirements.md), 选择兼容的[认证插件](../integrations/auth-plugins.md), 并备份代理, 后端及两端的数据库. 停止所有相关服务器后再安装.

## 1. 安装插件

| 安装位置 | 放入 `plugins/` 的插件 |
| --- | --- |
| BungeeCord 代理 | FLP 的 **BungeeCord** 版本 |
| 每个 Spigot/Paper 后端 | FLP 的 **Bukkit** 版本和认证插件 |
| 每个 Folia 后端 | FLP 的 **Folia** 版本和支持 Folia 的认证插件 |

此部署下 FLP 不需要后端 ProtocolLib. 不要使用 `-plain.jar`.

## 2. 配置代理和后端

在 BungeeCord 的 `config.yml` 中设置:

```yaml
online_mode: false
ip_forward: true
```

FLP 会按需验证正版玩家. 在每个后端的 `server.properties` 中设置 `online-mode=false`, 在 `spigot.yml` 中设置:

```yaml
settings:
  bungeecord: true
```

使用 Paper 或 Folia 时, 还需要关闭 Velocity 转发, 并将 BungeeCord 转发的 online-mode 设置为 `false`. 这些选项的位置随 Paper 版本而异, 请查看对应版本的配置说明.

## 3. 允许后端信任代理

1. 启动代理和后端一次, 等待生成配置文件, 然后停止两端.
2. 打开 BungeeCord 自身的 `config.yml`, 复制 `stats` 对应的 UUID. 这不是 Velocity 的 `proxyId.txt`.
3. 将该 UUID 写入每个后端的 `plugins/fastloginplus/allowed-proxies.txt`, 每行一个. 保留代理的 `stats` 值, 不要随意重新生成.

## 4. 配置策略并启动

1. 在代理的 `plugins/fastloginplus/config.yml` 中设置 FLP 登录策略和数据库.
2. 在后端配置认证插件和后端语言.
3. 用防火墙限制后端端口, 只允许受信任的代理连接.
4. 启动代理和后端, 检查插件是否正常加载, 再使用测试账号检查正版和密码登录.

## 安全边界

BungeeCord 的转发方式不能单独保护整个后端. 即使设置了 FLP 代理白名单, 也必须使用防火墙阻止玩家直接连接后端. 代理 ID 应妥善保管, 不要公开.

代理 FLP 配置保持 `verify-backend-messages: true`. 为兼容旧环境而临时关闭会降低消息检查强度, 不是修复错误白名单的方法.

## 命令与存储

在后端游戏内或后端控制台执行 `/flp`, 不在 BungeeCord 控制台执行. 后端空服时, 部分请求会等待玩家上线后再发送, 请留意最终反馈.

代理数据库持有网络的 FLP premium/cracked 记录, 后端认证数据库存储密码和本地账号状态. 可选的 BungeeAuth 钩子是另一项集成, 不代表应该把 AuthMe 后端 JAR 装进代理.

参考官方 [IP 转发指南](https://www.spigotmc.org/wiki/bungeecord-ip-forwarding/)和[命令说明](../accounts/commands.md).
