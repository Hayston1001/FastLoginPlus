# EasyAuth

安装见 [Fabric 指南](../installation/fabric.md). 本集成面向 Minecraft 26.2 和 EasyAuth 3.4.4, 接受 3.4.x 补丁范围并输出版本诊断. 不提供 Bukkit/Folia EasyAuth 支持.

## 配置与登录

在 Velocity 上设置 FLP 的登录策略, 在 Fabric 后端安装 EasyAuth 并配置本地账号. 按[安装指南](../installation/fabric.md)设置 EasyAuth, 避免它自己的自动登录和认证绕过与 FLP 冲突. 修改配置后重启服务器.

## 账号状态

- 没有账号的正版玩家: 服务器策略允许时, 可以自动注册和登录.
- 已有密码账号: 持有人需要先登录并同意绑定, 之后才能使用正版免密登录.
- 已绑定正版账号: 只有通过验证的账号持有人可以使用绑定登录.
- 账号信息冲突或无法读取: 登录可能被拒绝, 请检查日志和数据库.

密码账号转换正版时保留密码, 解绑可以恢复. 原本无密码的账号需要显式释放到注册, 见[绑定说明](../accounts/binding.md). `premiumUuid: false` 改变连接 UUID, 不改变集成保存的 Mojang 所有权 UUID.

## 存储与故障处理

目前支持 EasyAuth 的本地 SQLite 存储. 其他 EasyAuth 存储尚未验证, 使用时会出现告警.

绑定后无法登录时, 可以先尝试重新连接. 如果仍然失败, 检查代理和后端日志以及两端的数据, 不要仅凭聊天提示删除账号.

改名跟随限于已验证身份和一致且无冲突的账号. 不承诺背包迁移, 任意存储转换, linked Bedrock 绑定或多后端迁移. Bedrock 还有更窄的[已有账号边界](bedrock.md).
