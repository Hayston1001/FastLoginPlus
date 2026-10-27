# 文件, 默认值与生效范围

## 需要编辑的文件

| 部署/文件 | 用途 |
| --- | --- |
| Bukkit/Folia/BungeeCord/Velocity `plugins/fastloginplus/config.yml` | 本地设置, 自动选择代理或后端模板 |
| Bukkit/Folia/BungeeCord/Velocity `plugins/fastloginplus/messages_<语言>.yml` | 玩家和管理员消息 |
| Bukkit/Folia 后端 `plugins/fastloginplus/allowed-proxies.txt` | 可信 FLP 代理 UUID, 每行一个, 重启生效 |
| Velocity `proxyId.txt` | 自动生成的 FLP 代理身份, 应保留 |
| BungeeCord 自身 `config.yml` 的 `stats` | FLP 使用的 Bungee 实例身份 |
| Fabric `config/fastloginplus-fabric.yml` | 后端语言, debug, 更新检查和 `allowed-proxy-ids` |
| Fabric `config/fastloginplus-fabric/messages_en.yml` / `messages_zh.yml` | 可选的本地消息覆盖 |
| Bukkit/Folia 后端 `plugins/fastloginplus/pending-relay.json` | 等待发送给代理的请求, 不是配置文件, 不要随意删除 |

路径相对各进程自己的工作目录. 插件目录以实际生成的为准. Forwarding secret 属于代理/服务端或 FabricProxy-Lite 配置, 不是 FLP 白名单.

## 模板刷新与重启

Bukkit/Folia 和 BungeeCord/Velocity 分别使用后端和代理配置模板. 启动时会补齐缺失选项并保留已有值; 不属于对应模板的选项可能被移除. 代理和后端的配置文件不能直接互换.

Fabric 使用独立的扁平 YAML 模板, 旧 `.properties` 说明不再适用. 不受支持的 YAML 会有诊断, 不要往该文件塞入嵌套的代理策略段.

停服编辑, 然后重启对应的代理或服务器. FLP 没有通用的 `/flp reload` 命令.

## 各设置由谁负责

| 配置组 | 独立服 | 代理部署 |
| --- | --- | --- |
| Java 路由, FLP 名单, 准入, 连接限制 | 当前服务器 | 代理 |
| `autoLogin`, `auto-register-unknown` | 当前服务器 | 代理声明, 后端决策并执行账号动作 |
| `premiumUuid`, `forwardSkin` | 当前服务器 | 代理选择转发档案 |
| 认证账号/密码与认证插件限制 | 后端 | 后端 |
| 语言, debug, 更新检查 | 各进程 | 各进程独立 |
| 可信代理 ID | 直接连接不需要 | 各后端 |

部分兼容路径有差异: `respectIpLimit` 在代理后面仍是 AuthMe 后端检查; Paper configure 路径仍可能使用后端名字查询设置. 应查逐项说明, 不能因为两份模板都有同名键就判断生效范围.

## 本地/通用默认值

| 选项 | 默认值 | 范围 |
| --- | --- | --- |
| `language` | `en` | Bukkit/Folia/BungeeCord/Velocity 支持自定义语言代码, Fabric 支持 `en`/`zh` |
| `debug` | `false` | 当前进程诊断日志 |
| `check-update` | `true` | 当前进程后台发布检查 |
| `update-check-interval` | `24` | 小时; Fabric 无效或小于 1 时回退到 24 |
| Fabric `allowed-proxy-ids` | 空字符串 | 后端可信代理 UUID, 逗号分隔或 YAML 列表 |

空信任不会启用依赖身份的动作. 无效 ID 要修正, 不能替换成"全部允许". 代理应保留 `verify-backend-messages`, 与旧端混用前检查 `require-declared-proxy-policy`.

其余选项见 [Java 策略](java.md), [Bedrock 策略](bedrock.md), [网络与反机器人](network.md), [数据库](database.md). 消息文件行为见[语言](../operations/languages.md).
