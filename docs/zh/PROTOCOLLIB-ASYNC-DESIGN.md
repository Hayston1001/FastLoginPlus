# ProtocolLib 异步监听器 —— 设计决策与残余风险

[English→](../en/PROTOCOLLIB-ASYNC-DESIGN.md)

FastLoginPlus 用 ProtocolLib 拦截 Minecraft 登录流程的数据包(`START`、`ENCRYPTION_BEGIN`).
本文档记录**为什么把该监听器注册为异步处理器**, 哪些补偿措施让这件事是安全的, 还剩下什么风险, 以及何时应当重新评估这个决策.

## 决策

该数据包监听器继续以 `.optionAsync()` + `getAsynchronousManager().registerAsyncHandler(...)` 注册.

理由:

- 完整改成同步监听器(取消 + `runAsync`, 并移除异步标记相关机制)需要在多个 ProtocolLib 版本上做真机集成测试. FastLoginPlus 是针对 ProtocolLib **5.3.0** 构建的(provided 作用域), 而生产服务器可能运行更新的 ProtocolLib 版本, 其内部注册语义并不相同. 所需的回归矩阵(Spigot/Paper × ProtocolLib 5.3–5.x × 各 MC 版本)与收益不成比例 —— 何况下面的补偿措施已经覆盖了主要的竞态.
- 上游那篇建议改为同步处理的复盘, 前提是异步取消路径会与 Paper 的原版处理器竞争. FastLoginPlus 已经把敏感操作串行化到事件循环上(见下), 这消除了该竞态的*已知*表现.

## 补偿措施(已落地)

1. **伪造 START / enableEncryption 窗口在事件循环上串行化.**
   把连接切换到在线模式验证的那个响应, 是在持有该数据包事件处理锁的情况下发出的(与上游一致的 `synchronized (packetEvent.getAsyncMarker().getProcessingLock())`), 因此原版状态机不可能观察到"只切换了一半"的状态.
2. **30 分钟异步标记超时.** ProtocolLib 的异步标记清理是有上限的, 卡住的标记不会永久堵住一个连接.
3. **取消 + 信号纪律.** 数据包事件的取消与 session 记账按固定顺序进行; session 以连接的远端地址(Velocity)或地址(Bukkit)为键, 并用原子 check-and-add 保护.
4. **ENCRYPTION_BEGIN 可解析性的启动自检.** 注册时插件会检查 ProtocolLib 是否仍能静态解析 `PacketType.Login.Client.ENCRYPTION_BEGIN`. 当该映射缺失时(较新 ProtocolLib/MC 组合上的已知失效模式, 例如 Paper 1.21.11 + ProtocolLib 5.5.0 未注册 `ServerboundKeyPacket`), 会输出一条醒目的启动告警.

## 残余风险

在**未**拦截 `ENCRYPTION_BEGIN` 的 ProtocolLib 版本上(映射缺失, 且运行时覆盖回退也救不回来), FastLoginPlus 的会话验证会静默地永不执行: 登录会按普通的离线/盗版登录进行. 症状: 缺少 `Verifying session for ...` 日志行, 正版玩家反而被 AuthMe 要求注册/输密码, 皮肤转发缺失, 没有正版的踢出/确认行为.

**运维指引** —— 当启动自检告警出现时:

1. 如果你依赖正版自动登录, 不要忽略这条告警; 在这台服务器上正版检测不会生效.
2. 把 ProtocolLib 固定/升级到能为你的 Minecraft 版本解析 `ServerboundKeyPacket`/`ENCRYPTION_BEGIN` 的版本(用 `/protocol log` 或一次测试登录、看是否出现 `Verifying session` 日志行来确认).
3. 如果你的服务器版本目前还没有兼容的 ProtocolLib, 请带着服务器与 ProtocolLib 版本到 FastLoginPlus 的 issue tracker 反馈, 以便扩展运行时覆盖表.
4. 作为临时方案, 让后端按强制离线的方式运行: 玩家继续使用 `/flp premium` 管理的离线 session, 或者为该后端关闭正版验证以绕开 FLP.

## 重新评估的触发条件

当以下任一条成立时, 重新审视这个决策(并考虑改回同步注册):

- 上游 ProtocolLib 为登录流程提供了受支持的**同步拦截保证**(有文档的 API, 而不是内部实现);
- 该启动自检告警在**真实环境**中以可观的比例出现(即残余风险不再是理论上的);
- 出现新的原版状态机竞态报告, 而现有补偿措施覆盖不了.
