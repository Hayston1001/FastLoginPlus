# Velocity 与 Fabric/EasyAuth

这是 **Minecraft 26.2 服务端**集成, Java 客户端不需要安装 FLP Mod. 后端需要 FabricProxy-Lite 的 Velocity modern forwarding 和 EasyAuth. 它不是 Fabric 独立服认证方案, 不支持 BungeeCord 转发.

## 前置条件

- Fabric 后端使用 Java 25, Minecraft 26.2, Fabric Loader >=0.19.3 及兼容的 Fabric API.
- FabricProxy-Lite 和 EasyAuth. EasyAuth 参考版本是 **3.4.4**, 允许加载 3.4.x 不等于每个构建都具有相同行为.
- 受支持的代理/EasyAuth 存储组合使用本地 SQLite. 其他 EasyAuth 存储会告警且尚未验证, 尤其不能保证它们能正确区分账号不存在与读取失败.
- 备份 EasyAuth, FLP 记录和世界/玩家数据. 启用绑定前阅读 [UUID 影响](../accounts/uuid.md).

## 1. 安装插件和 Mod

停止代理和后端, 然后放入以下文件:

| 安装位置 | 文件 |
| --- | --- |
| Velocity `plugins/` | FLP 的 **Velocity** 版本 |
| Fabric `mods/` | FLP 的 **Fabric 26.2** 版本, Fabric API, FabricProxy-Lite 和 EasyAuth |

不要在 Fabric 安装 Bukkit/Folia 版本, 也不要同时安装与 FabricProxy-Lite 冲突的 FabricProxy.

## 2. 配置转发

在代理 `velocity.toml` 设置:

```toml
online-mode = false
player-info-forwarding-mode = "modern"
```

在 Fabric 后端的 `server.properties` 设置 `online-mode=false`. 然后启动代理和后端一次以生成配置, 再停止两端. 在后端 `config/FabricProxy-Lite.toml` 中, 将 `secret` 设置为代理的 `forwarding.secret` 文件内容. 不要公开这个密钥.

## 3. 配置 EasyAuth

在后端修改 EasyAuth 3.4.4 的 `config/EasyAuth/main.conf`:

| 选项 | 必要值 |
| --- | --- |
| `premium-auto-login` | `false` |
| `offline-by-default` | `false` |
| `floodgate-auto-login` | `false` |
| `session-timeout` | `-1` |
| `enable-global-password` | 建议 `false`; 若启用, `single-use-global-password` 必须为 `true` |

在 `config/EasyAuth/extended.conf` 中设置:

| 选项 | 必要值 |
| --- | --- |
| `skip-all-auth-checks` | `false` |
| `allow-all-packets` | `false` |

修改 EasyAuth 已生成的配置, 不要把这些选项添加到 FLP 的 YAML 文件.

## 4. 配置 FLP

1. 复制代理 `plugins/fastloginplus/proxyId.txt` 中的 UUID.
2. 在后端 `config/fastloginplus-fabric.yml` 中, 将 `allowed-proxy-ids` 设置为该 UUID. 可以同时设置 `language: zh` 或 `language: en`.
3. 在代理的 `plugins/fastloginplus/config.yml` 中配置 Java/Bedrock 登录策略. 不要把代理策略添加到 Fabric 的配置文件.

代理 ID 与转发密钥是不同的值, 不能互换. 修改配置后需要重启.

## 5. 启动并检查

用防火墙禁止公网直连后端. 启动代理和后端, 检查启动日志, 再使用测试账号检查正版和密码登录.

## 预期行为

通过验证的正版玩家能否免密登录, 取决于代理策略和 EasyAuth 账号状态. 未绑定的密码账号继续使用密码. 玩家可以先登录, 执行 `/flp premium`, 按提示确认, 然后手动重连. 转换后原密码仍会保留.

`premiumUuid: false` 可以保留离线连接 UUID, 同时用经过验证的 Mojang UUID 标识 EasyAuth 正版持有人. 这不迁移玩家数据. 改名跟随必须匹配已验证的账号身份, 不能覆盖其他名字已有的账号.

Bedrock 范围更窄: 未 linked 且具有签名 Floodgate 声明的连接, 可以登录身份匹配且有已存凭据的**已有完整** EasyAuth 账号. 这条路径不创建 Bedrock 账号, 不支持 linked 登录或转换为 Java 正版账号. 详见 [Bedrock 集成](../integrations/bedrock.md).

修改后端 FLP 设置需要重启. 不支持 `/flp reload` 或热重载. Fabric 控制台执行需要转发给代理的命令时, 后端必须有在线玩家; 空服时命令会失败.

平台配置参考: [Velocity 转发](https://docs.papermc.io/velocity/player-information-forwarding/), [FabricProxy-Lite](https://github.com/OKTW-Network/FabricProxy-Lite), [EasyAuth 3.4.4 配置源码](https://github.com/NikitaCartes/EasyAuth/tree/3.4.4/src/main/java/xyz/nikitacartes/easyauth/config). 不要用 EasyAuth 4.x 指南代替这个版本的策略/API 检查.
