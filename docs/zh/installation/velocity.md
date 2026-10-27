# Velocity 与 Spigot/Paper/Folia

本页介绍 Velocity 连接 Spigot, Paper 或 Folia 服务器的安装方法. 如果后端是 Fabric, 请使用 [Fabric 指南](fabric.md).

## 安装前准备

- 确认各端满足 [运行要求](../overview/requirements.md), 并选择兼容的[认证插件](../integrations/auth-plugins.md).
- 备份代理和后端的数据, 包括 FLP 和认证插件的数据库. 安装前停止所有相关服务器.
- Paper 和 Folia 推荐使用 modern forwarding. Spigot 不支持这种方式, 请看下方的 legacy 配置.

## Paper 和 Folia: modern forwarding

### 1. 安装插件

| 安装位置 | 放入 `plugins/` 的插件 |
| --- | --- |
| Velocity 代理 | FLP 的 **Velocity** 版本 |
| 每个 Paper 后端 | FLP 的 **Bukkit** 版本和认证插件 |
| 每个 Folia 后端 | FLP 的 **Folia** 版本和支持 Folia 的认证插件 |

这个部署方式下 FLP 不需要 ProtocolLib. 不要使用 `-plain.jar`.

### 2. 配置 Velocity

编辑代理的 `velocity.toml`:

```toml
online-mode = false
player-info-forwarding-mode = "modern"
```

FLP 会按需验证正版玩家, 不需要把代理整体设置为在线模式.

### 3. 配置每个后端

在 `server.properties` 中设置:

```properties
online-mode=false
```

在 `spigot.yml` 中设置:

```yaml
settings:
  bungeecord: false
```

在 `config/paper-global.yml` 中找到 `proxies.velocity`, 设置以下值. 将 `secret` 替换为代理的 `forwarding.secret` 文件内容:

```yaml
proxies:
  velocity:
    enabled: true
    online-mode: false
    secret: "填写 forwarding.secret 的内容"
```

请修改现有字段, 不要重复添加 `proxies`. 旧版 Paper 可能在 `paper.yml` 配置这些选项, 请以对应版本的 Paper 文档为准.

### 4. 允许后端信任代理

1. 启动代理和后端一次, 等待 FLP 生成文件, 然后停止服务器.
2. 打开代理的 `plugins/fastloginplus/proxyId.txt`, 复制其中的 UUID.
3. 将 UUID 写入每个后端的 `plugins/fastloginplus/allowed-proxies.txt`, 每行一个 UUID.

代理 ID 不是 forwarding secret, 两者不能互换. 请妥善保管, 不要公开.

### 5. 配置策略并启动

1. 在代理的 `plugins/fastloginplus/config.yml` 设置 Java/Bedrock 登录策略和 FLP 数据库.
2. 在后端配置认证插件和后端语言. 后端同名选项不会覆盖代理负责的策略.
3. 用防火墙限制后端端口, 只允许受信任的代理连接.
4. 启动代理和后端, 检查插件是否正常加载. 使用测试账号检查正版和密码登录.

已有密码账号未同意绑定时, 玩家仍可能需要输入密码, 这不一定是安装故障. 账号命令在后端执行, 不在 Velocity 控制台执行.

## Spigot: legacy forwarding

Spigot 不支持 modern forwarding. 安装插件并配置代理信任列表的方法与上面相同, 但转发配置不同:

1. 在代理 `velocity.toml` 设置 `online-mode = false` 和 `player-info-forwarding-mode = "legacy"`.
2. 在每个后端 `server.properties` 设置 `online-mode=false`, 在 `spigot.yml` 设置 `settings.bungeecord: true`.
3. 如果使用 Paper 的 legacy 模式, 关闭 Paper 的 Velocity modern forwarding, 不要同时启用两种转发方式.
4. 限制后端端口只接受受信任代理的连接, 再重启并检查登录.

Legacy 转发本身不能验证转发来源, 因此必须隔离后端. 有条件时优先使用 Paper/Folia 的 modern forwarding. 详见 [Velocity 转发指南](https://docs.papermc.io/velocity/player-information-forwarding/)和[网络安全指南](https://docs.papermc.io/velocity/security/).
