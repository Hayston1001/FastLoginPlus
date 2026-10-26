# EasyAuth

安装见 [Fabric 指南](../installation/fabric.md). 本集成面向 Minecraft 26.2 和 EasyAuth 3.4.4, 接受 3.4.x 补丁范围并输出版本诊断. 不提供 Bukkit/Folia EasyAuth 支持.

## 职责划分

Velocity 验证 Java/Floodgate 身份, 实施网络策略并持有 FLP 同意/名单记录. FabricProxy-Lite 认证 modern forwarding. FLP Fabric 对照真实档案检查身份声明, 读取 EasyAuth 账号, 决策并执行本地账号访问.

按安装指南关闭 EasyAuth 独立的正版/Floodgate 自动登录, 会话复用和认证绕过. FLP 在操作时重新检查已加载的 EasyAuth 策略, 不重新读取 EasyAuth 配置文件, 也不提供热重载.

## 账号状态

- **开放:** 没有账号或受支持的未认领记录, 按声明策略为已验证 Java 身份注册.
- **密码:** 离线账号身份一致且有非空凭据, 持有人认证前保持锁定; 转换正版需要已记录同意.
- **绑定:** 已有正版归属及对应已验证 UUID, 不允许其他 UUID 或未验证连接使用绑定.
- **冲突/不可用:** 身份字段不一致或检查失败, 应拒绝或保留认证锁, 不能猜测新持有人.

密码账号转换正版时保留密码, 解绑可以恢复. 原本无密码的账号需要显式释放到注册, 见[绑定说明](../accounts/binding.md). `premiumUuid: false` 改变连接 UUID, 不改变集成保存的 Mojang 所有权 UUID.

## 存储与失败边界

受支持路径使用本地 SQLite, 直接读取账号并区分 SQL 错误与记录不存在. 其他 EasyAuth 存储尚未验证且会告警, 因为其 API 可能在错误时返回 null. 能加载 JAR 或首次登录成功, 都不能证明其他存储安全.

本地账号更新检查保存结果, 失败时恢复内存字段. 已保存绑定但本次登录无法完成时, 可能需要重连. 代理请求与本地账号写入不是跨服原子事务, 出错后应检查两份持久化存储, 不能只看聊天反馈.

改名跟随限于已验证身份和一致且无冲突的账号. 不承诺背包迁移, 任意存储转换, linked Bedrock 绑定或多后端迁移. Bedrock 还有更窄的[已有账号边界](bedrock.md).
