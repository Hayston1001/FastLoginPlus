# 登录流程与身份信任

本文说明即将发布的 1.0.0 实现. 管理员操作见[登录方式](accounts/login.md), [绑定](accounts/binding.md), [部署指南](overview/requirements.md). 历史调用顺序或类名不能代替这里的账号决策.

## 1. 选择并验证连接

经典 Java 入口由 `JoinManagement` 根据权威 FLP 记录路由. 已同意的名字, 或有正版 UUID 观察且没有生效同名离线回退的名字, 请求新的正版握手. 已知 cracked 名字进入密码路径. 新名字在 `autoRegister`, `nameChangeCheck`, `offline-whitelist` 需要时查询 Mojang.

未知名字查询返回 `rowId=-1` 的占位档案, `loadProfile(name)==null` 代表读取失败, 不是新玩家. 未完成的名字查询使用 `auth-server-unreachable` 拒绝, 不作为明确离线名字. 按名字锁保护适用的读改写窗口, 不是跨进程全局事务.

独立服 ProtocolLib 拦截执行在线握手, 用会话服务验证挑战. 代理由原生在线握手建立身份. 名字查询成功绝不等于会话已验证. `premiumUuid` 在验证后选择转发/交付 UUID, 不决定是否验证.

连接限制在独立服入口或代理 pre-login 执行. 命中即阻断, 没有 `anti-bot.action`/Ignore 路径. Floodgate 走独立的已验证 Bedrock 路径, 不因名字像 Java 就取得 Java 身份.

## 2. 跨代理边界传递事实

代理负责验证, 网络准入, 策略声明和 FLP 记录, 执行代理侧持久化/转发. 后端负责本地账号检查, 最终认证插件动作决策和执行. 两端都有决策和执行.

- Velocity modern forwarding 通过 forwarding secret 认证档案, 信任对象是整个代理进程, 不是敌对代理里的某个单独可信插件.
- Legacy forwarding 不签名. 当前身份声明需要验签并关联连接, FLP 才能授予正版准入. 原始 v4 UUID 或 UUID 属性只是候选值, 外形不是认证.
- 后端信任列表检查 FLP 代理 ID. 经典后端向代理发 toggle/delete/success 时, 开启检查会回传后端允许代理集合. 这不为每个后端插件提供独立身份, 也不能抵御受信任进程被控制.
- 声明要检查来源, 档案/名字/UUID 关联, 时效和适用重复规则. 缺失, 冲突或不可读身份不能变成许可.

`fastloginplus:force` 保留为经典账号动作通道. 转发身份/策略事实让后端能在进入游戏后的消息到达前作账号决策. 代理后端不注册 ProtocolLib 拦截. FLP 为自身声明签名也不能替代正确转发和网络隔离.

## 3. 决策本地账号动作

经典已声明事实由 `LoginMethodDecider` 表达. 自动登录开启时:

| 本地账号 | 已验证 Java 连接 | 未验证连接 |
| --- | --- | --- |
| 不存在 | 允许时自动注册/登录, 否则引导注册 | 正常注册 |
| 密码 | 有同意才免密, 无同意保持密码 | 同意冲突则拒绝, 否则保持密码 |
| 正版归属 | 允许匹配已验证持有人的恢复路径 | 拒绝 |
| 读取失败 | 保持认证锁 | 保持认证锁 |

具体 AuthMe 前置路径还检查 UUID 一致性, 损坏和安全占位处理. 通用独立服钩子保留平台/会话强制动作路径, 不能凭该表断言所有插件都有相同前置检查. `autoLogin` 关闭时不提供 FLP 免密动作, 独立于策略的所有权拒绝仍保护正版归属账号.

代理声明策略是权威, 后端同名设置不能覆盖. 经典旧端兼容路径在没有声明块且不被严格要求拒绝时, 可以参考本地策略. 这是升级边界, 不是伪造缺失身份的理由.

AuthMe configuration/pre-login 集成可以在对话框阻塞之前执行. 进入游戏后的 `ForceLoginTask` 不是唯一路径, 任务发送也不证明认证成功. 确认动作结果或独立确认已经认证后才能报告成功.

## 4. 只持久化对应结果

`UUID` 是正版身份观察, `Premium` 是同意/已确认正版结果, `SameNameCracked` 是按配置记录的失败观察. 普通代理验证观察不能在后端决定之前授予账号同意. 经典确认结果可以回执代理, 显式授权的名单命令是另一同意来源.

代理经典后端会话没有可写的本地 FLP 档案, 但仍读写本地认证账号. 成功恢复正版归属账号可以协调它与 FLP 同意的差异, 不要再沿用"所有回执丢失都永久需要管理员或离线客户端"的旧结论.

离线记账不能顺便清除正版观察/同意. 显式 cracked/delete 有各自授权语义. 同名回退在启用时按名字持久化, 故障不自动等于明确失败, 见[名单规则与代理归因边界](accounts/lists.md).

## 5. Fabric 与 Bedrock 差异

Fabric 接收 modern forwarding 的 `IdentityClaim` 事实, 验证真实档案并读取 EasyAuth. 已绑定账号必须匹配已验证 UUID, 密码账号需同意才转换, 转换保留密码. 未验证或不一致的绑定访问拒绝, 支持的 SQLite 路径不把 SQL 读取失败当开放账号.

自助绑定在密码认证后发送共享 toggle 请求. 代理确认, 同意持久化, 后续手动重连并验证构成当前链路. 没有 transfer, cookie 或十分钟本地绑定会话. `kick-toggle` 只决定踢出或保留当前连接, 不自动重连.

Bedrock 声明单独签名. 经典 AuthMe pre-login 按代理策略创建/登录受支持账号. Fabric 只支持新鲜签名的未 linked 身份访问已有完整匹配 EasyAuth 账号, 不注册新/linked 账号或转换 Java 正版. 进程内防重放不代表跨重启保存.

## 审查与测试边界

覆盖断线, 重连, 延迟/过期回调, 重复消息, 策略改变, 账号/SQL 错误, 重载/关闭和持久化失败. 分开核对本地与代理结果, 不存在跨数据库事务. 单元测试, 编译, 启动, 真实客户端与故障注入分别记录证据.

源码入口: [core 路由/决策](https://github.com/Hayston1001/FastLoginPlus/tree/main/core/src/main/java/com/github/games647/fastlogin/core/shared), [后端集成](https://github.com/Hayston1001/FastLoginPlus/tree/main/bukkit/src/main/java/com/github/games647/fastlogin/bukkit), [protocol](https://github.com/Hayston1001/FastLoginPlus/tree/main/protocol), [Fabric](https://github.com/Hayston1001/FastLoginPlus/tree/main/fabric/mc26_2).
