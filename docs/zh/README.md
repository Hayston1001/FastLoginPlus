# FastLoginPlus Wiki

> 本手册面向即将发布的 **1.0.0**, 该版本尚未发布. 内容说明当前实现的完整行为, 不只是 0.7.0 以来的变化. 旧版不一定具备这里介绍的功能. 开发构建可能仍使用旧版本号, 还应通过提交编号区分构建.

FastLoginPlus (FLP) 把经过验证的 Minecraft 身份与服务器的认证系统衔接起来. 它可以让通过验证的 Java 正版账号持有人和受支持的 Floodgate 玩家免输密码登录, 同时让其他玩家继续使用密码认证. 是否能创建或打开账号, 取决于服务器策略和本地账号状态.

## 请先了解这些风险

1. 正版用户名不是所有权证明. 当前连接必须通过 Mojang 验证.
2. FLP 的 premium/cracked 名单不是认证插件的账号数据库.
3. 修改交给服务器的 UUID, 可能改变玩家加载的背包, 权限和其他数据. FLP 不迁移这些数据.
4. 代理白名单不能替代防火墙和经过认证的信息转发. 不要让公网玩家直接连接后端端口.

[下载与发布](https://github.com/Hayston1001/FastLoginPlus/releases) | [更新历史](https://github.com/Hayston1001/FastLoginPlus/blob/main/CHANGELOG_zh.md) | [问题反馈](https://github.com/Hayston1001/FastLoginPlus/issues) | [源码](https://github.com/Hayston1001/FastLoginPlus)
