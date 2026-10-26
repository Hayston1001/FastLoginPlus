# FastLoginPlus Wiki

> 本手册面向即将发布的 **1.0.0**, 该版本尚未发布. 内容说明当前实现的完整行为, 不只是 0.7.0 以来的变化. 旧版不一定具备这里介绍的功能. 开发构建可能仍使用旧版本号, 还应通过提交编号区分构建.

FastLoginPlus (FLP) 把经过验证的 Minecraft 身份与服务器的认证系统衔接起来. 它可以让通过验证的 Java 正版账号持有人和受支持的 Floodgate 玩家免输密码登录, 同时让其他玩家继续使用密码认证. 是否能创建或打开账号, 取决于服务器策略和本地账号状态.

## 从哪里开始

- 初次了解: [功能与边界](overview/introduction.md), [选择部署方式](overview/requirements.md).
- 准备安装: [Spigot/Paper](installation/standalone.md), [Folia](installation/folia.md), [Velocity](installation/velocity.md), [BungeeCord](installation/bungeecord.md), [Fabric/EasyAuth](installation/fabric.md).
- 管理玩家: [登录方式](accounts/login.md), [名单与准入](accounts/lists.md), [绑定与切换](accounts/binding.md), [命令](accounts/commands.md).
- 修改配置: [配置文件与生效范围](configuration/README.md). 修改后端的同名配置, 不会覆盖代理负责的网络策略.
- 遇到问题: [故障排查](operations/troubleshooting.md), [问题反馈](operations/reporting.md).
- 参与开发: [构建与测试](development/build.md), [架构](development/architecture.md).

## 请先了解这些风险

1. 正版用户名不是所有权证明. 当前连接必须通过 Mojang 验证.
2. FLP 的 premium/cracked 名单不是认证插件的账号数据库.
3. 修改交给服务器的 UUID, 可能改变玩家加载的背包, 权限和其他数据. FLP 不迁移这些数据.
4. 代理白名单不能替代防火墙和经过认证的信息转发. 不要让公网玩家直接连接后端端口.

中英文手册采用相同结构. 已发布站点使用 GitBook 的语言切换器. 在 GitHub 阅读时, 可以打开[英文手册](https://github.com/Hayston1001/FastLoginPlus/tree/main/docs/en).

[下载与发布](https://github.com/Hayston1001/FastLoginPlus/releases) | [更新历史](https://github.com/Hayston1001/FastLoginPlus/blob/main/CHANGELOG_zh.md) | [问题反馈](https://github.com/Hayston1001/FastLoginPlus/issues) | [源码](https://github.com/Hayston1001/FastLoginPlus)
