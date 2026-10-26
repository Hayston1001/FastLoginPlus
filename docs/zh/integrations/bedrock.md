# Geyser 与 Floodgate

Geyser 把 Bedrock 连接转换为 Java 协议. Floodgate 提供独立认证的 Bedrock 身份及可选 Java 关联. FLP 不替代它们, 也不根据前缀推断已完成认证.

## 选择认证模式

不要认为"只有 Geyser, 没有 Floodgate"就是"没有认证". Geyser 选择的 Java 认证模式决定是否执行 Java 登录. FLP 的纯 Geyser online 路径可以进入 Java 决策树; 非 online 路径不能只因名字匹配正版账号就获得正版登录.

按各自官方[安装指南](https://geysermc.org/wiki/geyser/setup)和 [Floodgate 文档](https://geysermc.org/wiki/floodgate/setup)部署. 它们当前的协议/Java 要求独立于 FLP 的字节码下限.

## 经典部署

按对应部署官方指南选择 Geyser/Floodgate 安装位置. 代理网络由 FLP 从代理获取身份与策略, 可以向后端转发签名 Bedrock 事实. AuthMe 支持的按名字 pre-login API 再在认证入口前准备/注册/登录账号. 这也包含适用的 Spigot pre-login 路径, 不仅是 Paper configure 事件.

可选的后端 Floodgate API 路径用于兼容, 不能静默用本地策略覆盖代理声明, 或重复代理的身份判断. 保持名字/前缀稳定, 保留信任文件, 分别测试 linked 与未 linked 情景.

自动注册与登录由 [Bedrock 策略](../configuration/bedrock.md)控制. 注册/登录失败后的清理可能需要管理员修复, 不能为了得到成功结果而重置账号或记录生成的凭据.

## Fabric 更窄的范围

当前 Bedrock 自动登录必须同时满足:

1. 来自受信任代理的新鲜签名声明, 与实际连接的名字和 Floodgate UUID 绑定.
2. **未 linked** 身份, 且代理自动登录策略允许.
3. 已有完整 EasyAuth 账号, 名字/UUID 完全一致, 有非空凭据, 属于适当的非正版账号类型, 没有 forced UUID 覆盖.
4. EasyAuth 策略检查成功, 存储可读, 账号更新成功后再完成认证.

缺失/无效/过期/重放声明会被拒绝, 未知或不可读账号状态不会授予登录. 防重放记录限于当前进程, 不承诺跨重启持久化. 已存凭据只证明注册完整, 当前连接的认证来自可信 Floodgate 声明.

这条路径**不**创建 Bedrock 新账号, 不接受 linked 账号, 不使用 EasyAuth 原生 Floodgate 绕过, 不转换为 Java 正版账号. 被拒绝的 Bedrock 玩家需要另行支持的建号/恢复流程, 不能伪造 Java UUID 或关闭检查.
