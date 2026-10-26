# Spigot/Paper 独立服

本页只适用于玩家直接连接的服务器. 位于代理后面时, 应使用 [Velocity](velocity.md) 或 [BungeeCord](bungeecord.md) 指南.

## 安装前

- 检查 [Java 与依赖要求](../overview/requirements.md).
- 选择一个[受支持的认证插件](../integrations/auth-plugins.md). AuthMe 另有[集成指南](../integrations/authme.md).
- 获取兼容目标 Minecraft/服务端版本的 ProtocolLib. ProtocolSupport 是另一条旧版兼容路径, 不代表覆盖所有新版.
- 备份世界, 玩家数据, 认证数据库和已有 FLP 数据. 首次登录前确定是否保留现有 UUID.

## 安装步骤

1. 停服. 把可安装的 **Bukkit** FLP JAR, 认证插件和数据包库放入 `plugins/`. 不使用 `-plain.jar`, Folia, 代理或 Fabric 产物.
2. 在 `server.properties` 设置 `online-mode=false`. 这允许混合登录方式, 但未经验证的玩家仍必须接受密码认证.
3. 确认真的是独立服后, 关闭转发: `spigot.yml` 中 `settings.bungeecord: false`, Paper 中关闭 Velocity 支持.
4. 启动一次生成文件, 检查启动错误, 再停服编辑. 后端 FLP 文件位于 `plugins/fastloginplus/`; 应以实际生成的目录为准, 不要另建大小写不同的第二个目录.
5. 检查 `config.yml`, 尤其是 `premiumUuid`, `autoRegister`, `autoLogin`, `auto-register-unknown`, `offline-whitelist`. `premiumUuid` 默认是 `true`, 已有离线服可能需要 `false` 来保留连接 UUID. 先阅读 [UUID 风险](../accounts/uuid.md).
6. 选择 `language: en` 或 `language: zh`. 修改后完整重启服务器.

## 验证

确认 FLP 找到了认证插件, 数据包路径注册成功, 没有加密响应映射告警. 测试首次加入的正版账号, 已有密码账号, 重连和未验证连接. 已有密码或账号归属可能合理地阻止免密登录; 有皮肤或使用正版名字都不能证明认证成功.

Paper 上 AuthMe 6 的 pre-join 处理可能早于进入游戏后的插件动作. 不要反复修改 `autoRegister` 试图解决对话框阻塞, 应按[排障指南](../operations/troubleshooting.md)检查.

开放公网玩家之前完成[验证清单](verification.md), 保留密码恢复途径和管理员账号.
