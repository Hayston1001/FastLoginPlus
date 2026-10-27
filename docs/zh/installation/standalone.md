# Spigot/Paper 独立服

本页只适用于玩家直接连接的服务器. 位于代理后面时, 应使用 [Velocity](velocity.md) 或 [BungeeCord](bungeecord.md) 指南.

## 安装前

- 检查 [Java 与依赖要求](../overview/requirements.md).
- 选择一个[受支持的认证插件](../integrations/auth-plugins.md). AuthMe 另有[集成指南](../integrations/authme.md).
- 获取兼容目标 Minecraft/服务端版本的 ProtocolLib. ProtocolSupport 是另一条旧版兼容路径, 不代表覆盖所有新版.
- 备份世界, 玩家数据, 认证数据库和已有 FLP 数据. 首次登录前确定是否保留现有 UUID.

## 安装步骤

### 1. 安装插件

停止服务器, 将以下插件放入 `plugins/`:

| 插件 | 选择 |
| --- | --- |
| FastLoginPlus | **Bukkit** 版本, 不使用 `-plain.jar` |
| 认证插件 | 与服务端兼容的版本, 例如 AuthMe |
| ProtocolLib | 与 Minecraft 和服务端版本兼容的版本 |

### 2. 配置服务器

在 `server.properties` 中设置 `online-mode=false`. 这允许玩家使用不同的登录方式, 但未验证玩家仍需通过认证插件登录.

确认玩家直接连接此服务器后, 在 `spigot.yml` 中设置 `settings.bungeecord: false`. 如果使用 Paper, 也要在 Paper 配置中关闭 Velocity 转发.

### 3. 配置 FLP

1. 启动服务器一次, 检查插件是否正常加载, 然后停止服务器.
2. 打开生成的 `plugins/fastloginplus/config.yml`. 文件夹名称以实际生成为准.
3. 检查 `premiumUuid`. 默认值为 `true`; 已有离线服可能需要 `false` 来保留现有连接 UUID. 修改前先阅读 [UUID 与玩家数据](../accounts/uuid.md).
4. 根据需要调整 `autoRegister`, `autoLogin`, `auto-register-unknown` 和 `offline-whitelist`. 各选项的用途见 [Bukkit 配置](../configuration/bukkit.md).
5. 将 `language` 设置为 `zh` 或 `en`, 然后重启服务器.

## 验证

确认启动日志没有插件加载错误, 再用测试账号检查正版登录和已有密码账号登录. 已有密码账号未同意绑定时仍需输入密码. 如果出现登录对话框问题, 参阅[故障排查](../operations/troubleshooting.md).

开放公网之前, 保留密码恢复途径和管理员账号.
