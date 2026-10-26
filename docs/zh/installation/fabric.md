# Velocity 与 Fabric/EasyAuth

这是 **Minecraft 26.2 服务端**集成, Java 客户端不需要安装 FLP Mod. 后端需要 FabricProxy-Lite 的 Velocity modern forwarding 和 EasyAuth. 它不是 Fabric 独立服认证方案, 不支持 BungeeCord 转发.

## 前置条件

- Fabric 后端使用 Java 25, Minecraft 26.2, Fabric Loader >=0.19.3 及兼容的 Fabric API.
- FabricProxy-Lite 和 EasyAuth. EasyAuth 参考版本是 **3.4.4**, 允许加载 3.4.x 不等于每个构建都具有相同行为.
- 受支持的代理/EasyAuth 存储组合使用本地 SQLite. 其他 EasyAuth 存储会告警且尚未验证, 尤其不能保证它们能正确区分账号不存在与读取失败.
- 备份 EasyAuth, FLP 记录和世界/玩家数据. 启用绑定前阅读 [UUID 影响](../accounts/uuid.md).

## 安装步骤

1. 停止两端. 代理 `plugins/` 安装 Velocity FLP. 后端 `mods/` 安装 FLP **Fabric 26.2** JAR, Fabric API, FabricProxy-Lite 和 EasyAuth. 不安装 Bukkit/Folia FLP, 也不同时安装冲突的 FabricProxy.
2. Velocity 设置 `online-mode = false`, `player-info-forwarding-mode = "modern"`. 后端 `server.properties` 设置 `online-mode=false`. `config/FabricProxy-Lite.toml` 中的 `secret` 与 Velocity forwarding secret 一致.
3. 启动一次生成文件后停服. EasyAuth 3.4.4 的下列主要选项位于 `config/EasyAuth/main.conf`, 两个 Extended 选项位于 `config/EasyAuth/extended.conf`. 它们使用 HOCON, 不是 FLP YAML. 检查生成配置并设置:

   | EasyAuth 选项 | 必要值 |
   | --- | --- |
   | `premium-auto-login` | `false` |
   | `offline-by-default` | `false` |
   | `floodgate-auto-login` | `false` |
   | `session-timeout` | `-1` |
   | `enable-global-password` | 建议 `false`; 若启用, `single-use-global-password` 必须为 `true` |
   | Extended `skip-all-auth-checks` | `false` |
   | Extended `allow-all-packets` | `false` |

   这些属于 EasyAuth, 不要添加到 FLP 的 YAML 中. FLP 检查已加载的策略, 不实现 EasyAuth 热重载.
4. 编辑 `config/fastloginplus-fabric.yml`, 设置 `language`, 可选的 `debug`, 并将 `allowed-proxy-ids` 填为 Velocity `plugins/fastloginplus/proxyId.txt` 中的 UUID. 支持逗号分隔字符串, 也接受 YAML 块列表或行内列表并在启动时规范化. 不要填入 forwarding secret.
5. Java/Bedrock 策略在**代理** FLP `config.yml` 配置. Fabric YAML 只有后端本地设置, 不是第二套策略. 当前代理实现不需要额外 Fabric 模式开关.
6. 禁止公网直连后端, 重启两端, 检查组件, 策略和存储告警, 完成[验证清单](verification.md).

## 预期行为

可信且通过验证的 Java 连接, 只有在策略与 EasyAuth 账号都允许时才能免密登录. 未同意绑定的密码账号继续使用密码. 持有人先登录该账号, 执行 `/flp premium`, 按提示完成代理确认, 待同意落库后手动重连. 转换后原密码保留但不参与正版免密登录. 当前流程没有 transfer, cookie 交换或十分钟的本地绑定窗口.

`premiumUuid: false` 可以保留离线连接 UUID, 同时用经过验证的 Mojang UUID 标识 EasyAuth 正版持有人. 这不迁移玩家数据. 改名跟随必须匹配已验证的账号身份, 不能覆盖其他名字已有的账号.

Bedrock 范围更窄: 未 linked 且具有签名 Floodgate 声明的连接, 可以登录身份匹配且有已存凭据的**已有完整** EasyAuth 账号. 这条路径不创建 Bedrock 账号, 不支持 linked 登录或转换为 Java 正版账号. 详见 [Bedrock 集成](../integrations/bedrock.md).

修改后端 FLP 设置需要重启. 不支持 `/flp reload` 或热重载. Fabric 控制台命令需要在线玩家承载消息, 没有经典后端的持久化转发队列.

平台配置参考: [Velocity 转发](https://docs.papermc.io/velocity/player-information-forwarding/), [FabricProxy-Lite](https://github.com/OKTW-Network/FabricProxy-Lite), [EasyAuth 3.4.4 配置源码](https://github.com/NikitaCartes/EasyAuth/tree/3.4.4/src/main/java/xyz/nikitacartes/easyauth/config). 不要用 EasyAuth 4.x 指南代替这个版本的策略/API 检查.
