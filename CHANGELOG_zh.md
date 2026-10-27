# FastLoginPlus 更新日志

[English](CHANGELOG.md) | 简体中文

## v1.0.0

![fabric](https://cdn.jsdelivr.net/npm/@intergrav/devins-badges@3/assets/cozy/supported/fabric_vector.svg)

> FLP 首个正式版本:
> - 构建体系迁移至 Gradle
> - 登录与身份决策链重新设计
> - Fabric 26.2 与 Velocity/EasyAuth 集成

### Fabric 26.2 与 EasyAuth

- **新增 Fabric 26.2 后端**: Velocity 后端可搭配 Fabric 26.2 与 EasyAuth 3.4

更多信息请见 [flp wiki](https://hayston.gitbook.io/flp-wiki/zh) 的相应部分

### 代理与后端身份

- **后端跟随代理事实**: 登录方式由代理声明的签名身份与策略事实决定; 后端本地的 `autoLogin`, `autoRegister` 或 Bedrock 策略不再覆盖这些声明
- **事实缺失时拒绝**: 身份数据缺失, 冲突, 过期或不可读时保持认证锁定, 不再用默认放行补齐
- **传统转发需要验签**: 旧格式的代理声明必须带有有效签名, 才会被接受为正版准入
- **明确区分未验证连接**: 未完成验证的连接与已验证的正版连接分开处理
- **等待提示**: 身份事实交换期间玩家会看到等待提示, 而不是无响应
- **认证插件不再重复自动登录**: FLP 接管登录决策时, 认证插件自带的代理自动登录保持关闭
- **最终结果回传**: 代理应用最终握手结果, 管理命令会报告最终结果, 不再只报告已提交

### 账号与 UUID

- **观察与同意分开**: 名字声明或失败尝试不再直接授予正版访问; 只有验证服务明确返回失败时, 才可能触发同名离线回退
- **已验证持有人仍可注册**: `autoLogin` 从 `true` 改为 `false` 时, 允许已验证的正版账号创建密码账号
- **允许保留离线 UUID**: `premiumUuid: false` 保留按名字生成的 UUID, 同时仍用已验证的 Mojang UUID 识别账号归属
- **损坏的正版 UUID 记录会保留**: 损坏记录会被报告, 不再被丢弃或静默覆盖
- **无密码账号需明确释放**: 没有密码的账号要经过二次确认才会开放注册; 归属不一致会被拒绝
- **冒名尝试不会降级已同意账号**: 已确认绑定的名字不会因为冒名者连接而被改回密码登录

### Bedrock 与 Floodgate

- **AuthMe 后端更早处理 Bedrock 登录**: 受支持的 AuthMe 6 组合可以在更早阶段完成 Bedrock 登录, 减少对话框卡住
- **Bedrock 事实带签名**: Bedrock 预登录事实由代理签名, 后端负责校验
- **Spigot 代理后端支持 Bedrock 预登录**: 在认证插件提供所需钩子时, Spigot 后端也能处理 Bedrock 预登录

### 命令

- **`/flp delete <player>`**: 删除非正版的 FLP 名单记录, 不会删除认证插件账号
- **最终反馈**: 通过代理发送的名单变更会回报最终结果; 后端排队中的请求不等于已完成
- **Fabric 命令**: Fabric 通过原版 gamemaster 权限提供相同的名单操作

### 配置与消息

- **语言文件增加版本键**: `_messages-version` 让 FLP 补充缺失键, 刷新旧默认值并保留自定义值
- **配置模板与实际行为一致**: Bukkit/Folia 与 BungeeCord/Velocity 模板会说明每个键由哪一侧读取, 代理后端不再暗示本地设置会生效
- **新增 `require-declared-proxy-policy`**: 可选的严格模式会拒绝不声明策略事实的旧代理强制消息
- **反机器人始终拦截**: 触发限额会直接拒绝连接; `anti-bot.action` 与 `Ignore` 动作已移除

### 其他修复

- **Paper configure 阶段**: 独立 Paper 服务器保留 configure 阶段登录路径, 不再丢失正版预登录处理
- **加密会话**: 加密会话失败会作为失败报告, 不再被误判为缺少正版身份
- **AuthMe 预登录**: 预登录注册清理会在账号被视为就绪前完成校验
- **调试日志**: 诊断消息使用插件自身的调试开关, 不再需要调整日志框架

### 升级说明

- **同时升级代理与后端**: 新的身份事实与传统声明验签需要两端版本匹配; 不要在新后端后面继续运行旧代理, 除非接受文档说明的拒绝行为
- **已移除的反机器人选项**: 从现有配置中删除 `anti-bot.action`
- **消息文件会自动迁移**: 保留自定义消息文件, 让启动迁移补充新键, 并检查报告的多余键
- **`require-declared-proxy-policy` 默认关闭**: 只在所有代理都升级后可启用

## v0.7.0

> 本次更新聚焦兼容性:\
> *强化对 AuthMeReloaded 6.0.0 和 6.0.1 的支持*;\
> *适配高版本 BungeeCord*;\
> *优化对 SkinsRestorer 自定义皮肤的支持*

### AuthMeReloaded 6.x

- **首次登录被对话框卡住的情况大大减少**: 没有 AuthMeReloaded 记录的玩家会在登录阶段就被注册
- **正版登录现在会写入正确的 UUID**: 写入失败会打印日志, 不再当成成功; 离线 UUID 不再被当作 Mojang 验证过的 UUID 存进库里
- **不再提示并不存在的密码**: FLP 不会再告诉玩家一个从未被保存过的自动注册密码
- **正版状态来源单一化**: FLP 保持 AuthMeReloaded 自带的正版验证关闭, 并在 `/authme reload` 之后重新接管, 把正版状态变更推给 AuthMeReloaded 的代理插件
- **拦截 AuthMeReloaded 的 `/premium` 与 `/freemium` 命令**: 提示玩家改用 FLP 相应命令, 避免数据库和玩家的状态混乱
- **在 Velocity 上**: 正版 UUID 从玩家的原始档案读取, AuthMeReloaded 的改写不再污染它

### BungeeCord

- **插件在高版本 BungeeCord 上可用**: 已验证的正版 UUID 也会随传统握手一起发送; 代理的连接类改为运行时解析

### SkinsRestorer

- **自定义皮肤不再与 FLP 竞争**: 玩家的自定义皮肤在重登后一直保留
- **`premiumUuid: false` 时 SR 兼容仍生效**: FLP 先用验证时的 UUID 查询而非连接时携带的 UUID

### 其他修复

- 代理后端上重复的代理消息不再输出警告日志
- 重写 `config.yml` 时保留文件末尾的换行
- 修正了 `mojang-request-limit` 的说明(该选项是生效的)
- 构建现在会强制校验每个模块的 Java 版本下限, 依赖升级不会再悄悄抬高所需的 Java 版本
- 构建, CI 和发布现已改用 Gradle wrapper; Maven 项目文件已移除
- ProtocolSupport 验证过的 UUID 不再被丢弃

### 升级说明

- FLP 会修改 AuthMeReloaded 的部分配置以避免兼容性问题: 把 `enablePremium` 设为 `true`; 把 AuthMeBungee/AuthMeVelocity 的 `premium.keepOfflineUuidCompatibility` 固定为 `false`
- 使用代理的用户建议同时更新前后端的 FLP 以避免出现非预期问题

### 已知问题

- 极小概率: 玩家在 AuthMeReloaded 对话框打开期间断开(后端会记录 `keepalive ... out-of-order`). 目前定位为服务端侧问题

## v0.6.0

> 本次更新聚焦稳定性修复.

### 问题修复

**v0.5.0 中发现的问题**

- **AuthMe 集成**: 盗版会话路径上的 AuthMe premium 记录清理改为 fail-closed. 当 AuthMe 记录
  带 premium 标记但 FastLogin 侧没有对应记录时(数据库重置/首次登录), 记录会被保留并告警,
  冒名者无法再借注册窗口抢占已验证的正版名字.
- **Folia**: `VerifyResponseTask` 与 bukkit 侧的事件循环调度修复同步(上游 327c14a3). 加密与
  伪 START 注入不再在首次登录时与原版处理器竞态; configure 阶段监听器不再调用
  `Bukkit.getScheduler()`(在 Folia 上必抛异常).
- **反机器人**: 清理时钟与限流器统一. 每 100 连接不再整表清空 per-IP 限流状态; 惰性清理节流为
  每秒一次; 修复条目创建竞态; 反机器人配置值带默认回退校验与警告.
- **Velocity**: `onPreLogin` 中的反机器人事件改用 `EventTask.withContinuation` 异步等待,
  不再阻塞 Netty 事件循环; 正版检查改在插件调度器上执行, 不再占用共享异步事件执行器.
- **并发**: `pendingConfirms` 改为并发集合, premium 确认门禁使用原子 check-and-add.
- **调度器生命周期**: 关闭标志在共享资源释放前停止所有平台调度器, 禁用后任务不再触碰已关闭的
  资源; Folia 自续链中继任务随插件停用而停止; 中继重试约 5 分钟后放弃.
- **存储**: HikariCP `maxLifetime` 下限钳制为 300 秒并伴随警告(模板默认改为 1800 秒, 旧值
  30 秒会导致持续的连接重建); MySQL 首次插入改为带保护的 upsert, 保留大小写变体防护;
  数据库初始化失败时会关闭已建立的连接池.
- **配置**: `config.yml` 重写改为原子操作; 模板节头不再被用户标量覆盖; YAML 歧义值保持字符串
  类型; `language` 值校验; 非法 `proxies` 条目跳过而不是使启动崩溃.
- **命令与钩子**: premium/cracked/delete 命令不再在主线程访问数据库; Passky 支持真正注册;
  认证插件钩子调用加上 5 秒超时; 重放的 ENCRYPTION_BEGIN 包被拒绝而不是双重验证; 包监听器
  可在意外反射异常后存活.
- **其他**: 离线白名单在出错时 fail-closed; bungee 登录/成功处理器对缺失会话判空; cracked 自助
  命令发送正确的消息; bukkit 更新检查间隔与文档的小时语义一致; 对已断开玩家的补踢改为静默跳过
  (例如原版"登录耗时过长"已将其踢出时); 清理死代码与失效语言键; 修正失实文档; 多处判空与竞态小修.
- **插件消息认证(后端 → 代理)**: `ch-st`, `del-st`, `succ` 三个通道现在携带发送方后端的代理白名单作为尾随字段, 代理端在校验回传集合不包含自身 ID 时丢弃消息并输出 WARN(fail-closed). 封堵最后一条无认证的插件消息通道, 此前网络内任意后端都可以伪造正版切换, 删除或成功回执来篡改代理端数据库
- 新增 `verify-backend-messages` 配置键(代理端模板): 仅在滚动升级期间临时设为 `false`. 先升级全部后端, 最后升级代理(旧后端不带回传字段会被拒绝). 单代理网络下等价于共享密钥; 多代理网络中被入侵的后端仍可回显自身白名单(信任模型边界已在代码注释与设计文档中说明)
- **存储并发**: 新增 64 条纹的名字级锁覆盖全部 load-modify-save 窗口(登录流程, 强制登录持久化, 代理端切换, 成功回执落库, premium/cracked 命令), 同一玩家的并发流程不再互相静默覆盖; 快速连续的 `/flp premium|cracked` 也不再重复落库/重复触发事件
- **SQLite upsert**: 首次保存改为 `INSERT ... ON CONFLICT(Name) DO UPDATE`, 并带字节级大小写守卫(与 MySQL 的 HEX 保护对齐), 两个并发的首次保存会收敛为一行而不是丢失记录; upsert 走更新分支且 `getGeneratedKeys()` 无行时回退按名查询回填 rowId; 保存失败通过 `saveQuietly` 上报而不是静默吞掉. 回归测试使用真实 SQLite 数据库, 含去掉锁必失败的判别用例
- **Velocity**: `EventTask.withContinuation` 的续体现在在所有路径上恰好 resume 一次. 每事件一个 CAS 守卫, 所有 resume 收敛到 `resumeOnce`; 应用反机器人决策(或触发反机器人事件)抛出异常时会被捕获并仍然恢复登录, 不再把连接挂死到读超时
- **Bukkit**: configure 阶段 premium 分支在载体玩家缺失时补上防御性中继调度(与 Folia 分支对齐). 空服期间排队的正版切换不再要等重启才能投递
- **插件消息加固**: 登录动作消息的类型字节在读取时校验. 格式错误的客户端插件消息现在快速失败为监听器可捕获的异常, 不再抛出未捕获的 `ArrayIndexOutOfBoundsException`

**排队命令(pending relay)修复**

- 修复异值双击竞态: 控制台先执行 `/flp premium X` 再执行 `/flp cracked X` 时, 中继任务可能发送任务创建时捕获的旧值. 现在中继任务通过新增的 `PendingRelayStore.removeToggle` 原子地取出队列当前值再发送, 保证最后一条命令生效(bukkit, folia 及 Paper configure 阶段的自中继路径).
- 修复 pending cracked 在目标玩家本人连入时被 Paper configure 监听器静默吞掉的问题(跳过 autoRegister 的同时不再清除队列条目): 条目保留并在任意玩家进入 PLAY 阶段后转发给代理, 代理数据库实际切换为 cracked.
- 修复 Folia 载体玩家断连竞态: 异步在线检查与 global-region 执行之间玩家退出时, 任务会先复查 `isOnline()` 再取队列条目, 载体已离开则重新链式重试; 发送本身失败时回滚入队并重试(toggle 与 delete 一致).
- 代理端(bungee + velocity)不再对"本来就是盗版"的目标玩家执行 kick: 无状态变化的切换与 already-premium 跳过行为对齐, 同时遵守 `kick-toggle: false` 配置, 并移除无操作时误导性的"已移除高级登录"踢出文案.
- 后端转发的 toggle/delete 执行结果现在通过新增的代理 → 后端插件消息(`ToggleFeedbackMessage`, 通道 `fb-st`, 携带语言键 + 用于 `allowed-proxies.txt` 校验的代理 UUID)回显到后端控制台. 管理员无需再去代理日志确认排队命令的成败.
- 重试任务堆积受限: `queueToggle`/`queueDelete` 现在返回是否新建了条目, 命令只为新条目安排重试任务. 已在队列中的条目沿用存活的重试任务, 其在发送时读取被覆盖后的最新值.
- 已知限制(设计如此, 保持不变): 排队队列是按后端隔离的(在 A 后端排队的 toggle 只在 A 有玩家上线时投递); 条目无 TTL. 管理员意图会保留直到成功投递或代理支持被关闭(`clearAll`).

### 行为变更

**/flp 帮助仅限管理员**

- 裸 `/flp` 命令现在仅限服务器管理员(OP)使用: 其他发送者会收到本地化的 `no-permission` 提示

**精简更新检查失败日志**

- 更新检查的网络失败现在以一条简短的 WARN 日志记录失败原因, 不再以 INFO 级别输出完整堆栈(堆栈移至 debug 级别)

**ProtocolLib 异步设计记录**

- 保持 ProtocolLib 登录监听器以 async 方式注册的决策已记录到 [`docs/en/PROTOCOLLIB-ASYNC-DESIGN.md`](docs/en/PROTOCOLLIB-ASYNC-DESIGN.md): 为何采用 async, 靠什么补偿, 还剩什么风险, 启动自检告警时管理员该怎么做, 以及什么情况下会重新评估这个决策
- 修正 ProtocolLib 踢出源码中的一处误导性注释(bukkit + folia)

### 提醒

使用 MySQL 数据库的用户:\
`lifetime` 推荐值为 **1800** 秒. 模板默认值已由 30 提升为 1800, 低于 300 的值会被钳制到 300 并在启动时输出警告.

## v0.5.0

### 代理离线中继队列持久化

- 代理切换/删除消息的离线中继队列现在可以跨重启存活: 消息持久化到 `pending-relay.json`(原子重写), 启动时恢复, 损坏文件会被移开而不是导致插件崩溃. 此前仅存在内存中的队列(重启即丢), 现在会在玩家重新上线后最终送达代理.

### /flp toggle 空档案保护

- `PremiumCommand`/`CrackedCommand`(bukkit+folia, 自身与其他玩家路径): `loadProfile` 返回 null(仅 SQL 异常)时改为发送本地化 `database-error` 消息, 不再抛 NPE(自身路径)或发送硬编码英文/`player-unknown`(其他玩家路径). 数据库故障时"记录不存在"是误导性提示
- velocity/bungee `PluginMessageListener` change 分支改用类型化 `config.getBoolean(...)` 读取 `premium-warning`, 替代无保护的 `(boolean) config.get(...)` 强转. 字符串型配置值会抛 `ClassCastException` 且被异步调度器静默吞掉

### /flp delete 代理修复

- `del-st` 消息现在携带 `isSourceInvoker` 标志: 玩家自己执行 `/flp delete` 时结果消息发给玩家本人, 控制台发起(或借中继玩家转发)时结果发给代理控制台. 中继玩家不再收到无关的删除结果
- 后端无玩家在线时不再静默丢弃: 删除请求入队并每秒重试直到有玩家上线(与 toggle 命令行为对齐); Folia 因无全局循环调度器改用链式延迟任务
- 代理端 `/flp delete` 在数据库查询本身失败时提示 `database-error`(不再谎报"记录不存在"), 删除成功时 fire 正版切换事件, 且 `deleteProfile` 返回 false 时复查记录以区分真失败与并发删除
- standalone 报错路径改用本地化 `database-error` 消息, 移除硬编码英文; 读取旧格式消息(仅玩家名, 无标志)时降级为"控制台发起"
- 新增 `DeletePremiumMessageTest`: 覆盖往返序列化, 控制台中继标志与旧格式容错

### 各平台独立配置模板

- BungeeCord/Velocity 现在使用专属的 `config-proxy.yml` 模板生成 `config.yml`: 后端专属键(`verifyClientKeys`, `respectIpLimit`)不再出现, 注释描述代理端职责(决策方: Mojang 查询, 数据库, 转发强制登录)
- Bukkit/Folia 的配置注释现在标明代理子服模式下失效(或仅部分生效)的键: `database`, `anti-bot`, Floodgate 相关键, JoinManagement 相关键等
- ConfigRefresher 现在会保留模板中无默认值的标量键(如 `ServerRSAPublicKeyFile`)的用户值
- `loadFile` 的默认值现在遵循所选配置模板(后端或代理); 在 `load` 之后调用 `setConfigTemplate` 会立即报错, 不再静默产生错误行为

### 代理正版记录持久化

- 代理在验证正版会话后现在由自身直接写入 `premium=true` 行(`ForceLoginManagement` 的 null hook 分支), 不再单独依赖后端 `SuccessMessage` 回执. AuthMe 6.0 代理部署下该回执不会发送(REGISTER 动作因 AuthMe 记录已存在跳过 ForceLoginTask; LOGIN 动作 `forceLogin` 因 `AsynchronousJoin` 已认证返回 false)
- 已核实正版但认证插件报失败(`forceLogin` 返回 false)的会话现在也会向代理补发回执, 恢复回执持久化通道
- 修复 #5: 有正版记录的玩家不会再因会话过期被 `secondAttemptCracked` 放行进离线模式
- 新增 `ForceLoginManagementTest`, 覆盖代理 null 分支落库, 行升级语义, AuthMe 6.0 bypass 补发回执, 成功路径回归与 cracked 路径回归

### SQLite 玩家名不区分大小写

- SQLite `premium` 表的 `Name` 列现在使用 `COLLATE NOCASE` 创建: Minecraft 玩家名不区分大小写("Steve" 与 "steve" 是同一账号), 同一玩家不会再出现仅大小写不同的两行(一行正版, 一行离线), 与 MySQL 默认不区分大小写的 collation 对齐
- 启动时对既有的区分大小写 `premium` 表做一次性迁移(重命名 → 重建 → 复制 → 删除, 单事务), 迁移幂等且保留所有行; 已存在大小写不同的正版和离线记录时, 每个名字保留一行(正版优先, 否则保留最早的记录)
- 新增 `SQLiteStorageTest`, 覆盖不敏感查找, 大小写变体重复行拒绝, 旧表迁移与迁移幂等性

### 数据库故障时 AsyncToggleMessage 空指针修复

- BungeeCord 与 Velocity 的 `/premium`, `/cracked` 切换在数据库查询失败时(SQLite 锁超时, MySQL 宕机, 连接断开)不再抛空指针异常: 任务中止, 向操作者发送新增的 `database-error` 提示并记录日志. 此前命令无声无息地无效, 只在代理控制台留下一行堆栈

### 其他问题修复

- 玩家在异步会话验证期间断开(如 Mojang 应答中取消登录)时验证任务立即中止: 不再向已断开的连接查询 Mojang 或踢出幽灵玩家(`VerifyResponseTask` 在继续前检查 Netty channel 活跃状态)
- Velocity 登录会话改为按连接远端地址(remote address)而非连接对象存储: 修复 `GameProfileRequest`(暴露不同的 `InboundConnection` 实例)与后续事件之间会话状态丢失; 会话已消失时 success 回执路径不再抛 NPE
- 代理端对"操作其他玩家"的管理切换消息改用已有的 `-other` 消息变体(移除了不再使用的专用键)
- Bukkit 切换中继不再重复发送待处理切换: `remove-if-present` 保证每个切换至多中继一次(即使 configure 监听器或重试已先中继); Folia 在无玩家在线时现在同样入队并每秒重试, 与 Bukkit 行为对齐

### 日志与诊断

- 新增代理端正版切换流程的 debug 日志与 `/flp` 命令诊断日志(注册结果, 命令调用)

### 文档

- README 更新及配置注释文档化(EFFECT-line)

## v0.4.0

### Paper 配置阶段自动注册

- 代理场景下, Paper 后端现在通过 `AsyncPlayerConnectionConfigureEvent` 在配置阶段自动注册玩家, 早于 AuthMe 的监听器运行, 而不使用 `HorriblePlayerLoginEventHack`
- UUID 不匹配守卫防止离线玩家被误注册为正版
- 启动日志确认配置监听器注册成功, 便于诊断

### 待处理切换自中继

- PLAY 阶段自中继取代旧的延迟重试机制. 玩家切换正版/离线模式后, 切换在下一次进入时可靠生效
- 遵循 kick-toggle 配置, 绕过 UUID 守卫处理待处理的切换, 处理无中继玩家时的离线切换
- 代理端切换时正确踢出目标玩家并解析正版 UUID
- 同样应用于 Folia 模块

### 切换离线时清理 AuthMe

- 正版玩家切换到离线时, FLP 彻底清理 AuthMe 记录: 清除 premium 标记, 强制注销账号(支持 AuthMe 5.x 和 6.0), 清除内存缓存
- 离线登录时通过 `ensureNotPremium()` 二次兜底清理

### 问题修复

- 移除日志消息中重复的 `[FLP]` 前缀
- 缺少 `.other` 权限时隐藏玩家名 tab 补全
- 强制执行 `/flp` 子命令的权限检查
- 修复 `PremiumCommand` 踢出消息使用了错误的语言键(`remove-premium` → `add-premium`)
- 修复 premium-warning 消息仅对本人命令显示, cracked→premium 切换时关闭 preJoin 登录对话框
- 移除 `org.sqlite` 重定位, 修复 Velocity/BungeeCord 上 SQLite 原生库加载失败
- 修复代理模式下 `CrackedCommand` NPE. 将 `getStorage()` 延迟到转发检查之后
- `mojang-retry-count` 和 `mojang-retry-delay` 现在适用于所有 Mojang API 调用(代理端 Name→UUID 查询和后端会话验证), 带指数退避

### 文档

- README 新增更多关于 Velocity 和 BungeeCord 的说明消息
- Issue 模板新增平台标签
- 说明切换到离线模式时 AuthMe 清理行为

## v0.3.1 (Hotfix)

### 严重问题修复

- 修复登录时 `ClassCastException: Integer cannot be cast to Long`. SnakeYAML 将较小的数值配置(如 `mojang-retry-delay: 500`)解析为 `Integer`, 但代码直接强转 `(long)` 导致崩溃. 所有数值配置读取改用 `Number.longValue()`/`intValue()` 安全转换

## v0.3.0

### 重要问题修复

- 反机器人模块共 6 处修复: TickingRateLimiter 时钟回退不再抛异常, 批量过期陈旧记录, compareTo 使用正确的 expireTime, 全局限制在每 IP 限制之前检查, 每 100 连接定期清理, 日志中用户名消毒
- 修复 Paper 上 `forwardSkin: false` 无效. PaperCacheListener 现在在设置皮肤前检查配置
- 修复 SkinsRestorer 皮肤被 Paper filledProfileCache 覆盖. 设置空占位纹理防止 `complete(true)` 拉取旧皮肤
- 防止 ProtocolLib pipeline 中 `floodgate_data_handler` 为 null, 避免 Floodgate 重命名/移除 handler 时 NPE

### 代码清理

- 移除 22 处 config `getXxx()` 调用中的冗余默认值(ConfigRefresher 保证键已存在)

### 会话重试改进

- Mojang 会话重试改为指数退避

### 构建

- 将 19 个共享依赖版本集中到父 pom.xml 属性
- 新增 FloodgateServiceTest(17 个测试)和 FloodgateManagementTest(15 个测试)
- maven-compiler-plugin 3.13.0 → 3.15.0

## v0.2.1

### 统一版本格式

- 所有插件描述文件(plugin.yml, bungee.yml, velocity-plugin.json)现在统一使用 `${revision}-${git.commit.id.abbrev}` 格式
- 移除了 bungee/velocity shade 配置中多余的 `ManifestResourceTransformer`
- 移除了 shade filter 中导致 MANIFEST 丢失的 `META-INF/MANIFEST.MF` 排除规则

## v0.2.0

### 配置刷新

- 在每次启动时从模板恢复 config.yml 的注释和键, 使得配置文件与最新版本一致, 也更具可迁移性

### 日志输出优化

- 在 config.yml 中添加 `debug: false` 选项以减少日志冗余

### JAR 名称包含提交哈希

- 在 JAR 文件名和 MANIFEST 中包含 git 提交哈希使得文件版本更明确

### 问题修复

- 修复 `sslMode` 和 `allowPublicKeyRetrieval` 使用无效的 `=` 语法

### 文档

- 优化了配置文件结构和注释

## v0.1.2

> 这是对 AuthMe 支持最完善的一个版本, 建议使用 **FLP 搭配 AuthMe** 的用户尽快更新. 接下来的版本将聚焦于其他 bug 修复和使用体验优化.

### AuthMe 6.0 自动集成

- FLP 自动接管 AuthMe 6.0 正版验证, 无需手动设置 `enablePremium=true`
- `forceEnablePremium()`: 通过 AuthMe 的 Settings API 设置 `enablePremium=true` 并持久化到 config.yml
- `unregisterPremiumPacketListener()`: 注销 AuthMe 的 PacketEvents 监听器, FLP 成为唯一验证源
- 每次正版登录时懒式重新断言, 防止 `/authme reload` 重新注册监听器

### AuthMe 6.0 首次正版登录修复

- 修复首次正版玩家的 preJoin 对话框未跳过的问题(getAuth()==null 情况)
- 在 LOGIN 阶段通过 saveAuth() + updatePremiumUuid() 预创建 AuthMe 数据库记录
- 修复首次登录后正版会话未持久化(导致重连失败)
- ForceLoginTask 现在即使 forceLogin 返回 ALREADY_AUTHENTICATED 也会保存 onlinemodePreferred=true

### 其他问题修复

- 修复版本检查显示 "unknown" 而非实际版本(在 JAR manifest 中添加 Implementation-Version)
- 修复 `/flp cracked` 命令对其他玩家显示错误消息
- 修复 source.kick() 后 START 包仍被转发导致 vanilla 服务器覆盖 FLP 的踢出消息

## v0.1.1

### AuthMe 6.0 preJoin 修复

- 修复 AuthMe 6.0 preJoin 对话框阻塞正版玩家
- 修复对话框阻塞连接导致 premium 标记永远无法写入的死锁
- 新增启动验证: 当 AuthMe 6.0 的 preJoin 开启但 `enablePremium` 未启用时输出 ERROR 日志

### Tab 补全

- 为 `/flp` 命令的子命令添加 Tab 补全(bukkit + folia)

### 语言文件

- 修复 `messages_zh.yml` 未保存到插件配置目录

### 构建

- JAR 文件名包含版本号

## v0.1.0

> **v0.1.0 是 FastLoginPlus 的首个重要更新版本**, 涵盖新平台支持, 安全加固以及 fork 以来积累的重要修复. **但这不会是一个稳定版本**

### Folia 平台支持

- 新增 Folia 作为独立模块, 使用 FoliaScheduler
- Folia 支持的稳定性仍需长期观察和检验

### 代理 SQLite 支持

- 为 BungeeCord 添加 SQLite 支持, 包含 sqlite-jdbc 依赖和重定位
- 为 Velocity 添加缺失的 org.sqlite 重定位以实现类隔离

### 自动更新检查

- 新增 UpdateChecker 检查 GitHub Releases, 支持启动时和周期性检查
- OP 玩家登录时会收到更新通知
- 配置项: check-update(默认: true)

### FastLoginAntiBotEvent

- 新增 FastLoginAntiBotEvent 核心接口(暴露 address/username/action)
- 在 Block/Ignore 操作时触发事件, 允许取消以绕过

### 多层反机器人升级

- PerIpRateLimiter: 双窗口(突发 + 长期)每 IP 速率限制
- IpBanManager: 临时 IP 封禁, 自动过期
- TrustedIpSet: 不可变白名单, 绕过所有反机器人检查
- WindowCounter: 每 IP 线程安全双窗口计数器
- AntiBotService: 重构为多层编排器(可信 IP → 封禁检查 → 每 IP 限制 → 全局限制)
- 新配置项: per-ip-connections, per-ip-expire, burst-limit, burst-window, ban-duration, trusted-ips

### 问题修复

- 修复首次登录时区块渲染竞态条件(修复 TuxCoding/FastLogin#1358)
- 为 SQLiteStorage.deleteProfile 添加锁以确保线程安全
- 统一版本管理, 使用 ${project.version}

## v0.0.7

### 统一命令命名空间

- 统一所有命令到 `/flp` 命名空间: `/premium` → `/flp premium`, `/cracked` → `/flp cracked`, `/fldelete` → `/flp delete`
- 从 plugin.yml 移除旧的独立命令定义
- 更新配置注释和用户消息中的命令引用

### PlaceholderAPI 变量

- 新增 `%fastloginplus_is_premium%` 变量(返回 true/false)
- 新增 `%fastloginplus_floodgate%` 变量(返回 Java/Bedrock/Linked/Unknown)

### 基岩版玩家支持

- 升级 Geyser 2.2.1→2.10.1 和 Floodgate 2.2.3→2.2.5
- README 新增基岩版玩家支持章节, 含 Geyser/Floodgate 指引

### 代码清理

- 移除 AsyncScheduler 中无用的版本检测和反射代码

## v0.0.6

- 将 `autoRegister`, `premiumUuid`, `nameChangeCheck` 默认值改为 `true`
- 将 `/cracked` 默认权限从 `true` 改为 `op`
- 优化配置文件注释和分区
- 明确 `offline-whitelist` 仅控制访问权限, 不负责注册
- 新增 `LOGIN-FLOW.md` 登录流程文档

## v0.0.5

### AuthMe 6.0 兼容

完整兼容 AuthMe 6.0 的正版系统. FLP 启动时自动检测 AuthMe 版本并适配, 无需用户手动配置:

- **运行时版本检测**: 通过 `PendingPremiumCache` 类存在性判断(比版本号解析更可靠)
- **正版状态注入**: Mojang 验证后通过反射注入 `PendingPremiumCache` + `PremiumLoginVerifier`, 跳过 AuthMe 的 Pre-Join 对话框
- **自动注册**: 新正版玩家会被强制注册到 AuthMe 数据库并标记为 premium
- **会话恢复**: 尊重 AuthMe 6.0 自身的 premium 跳过机制, 不再互相干扰
- **`/flp` 命令命名空间**: 检测到 AuthMe 6.0 时, FLP 命令注册为 `/flp`(如 `/flp premium`), 避免与 AuthMe 的 `/premium` 冲突
- **启动日志**: 服务器启动时输出详细的 AuthMe 兼容信息(版本, enablePremium 状态, 当前行为)
- 所有反射调用都有 try-catch 保护, AuthMe 内部类变更时自动降级为空操作

## v0.0.4

### 会话重试

新增 Mojang 会话服务器验证自动重试(仅 Spigot+ProtocolLib):

- 当 `hasJoined` 因网络错误(IOException)失败时, 插件会自动重试, 最多 `mojang-retry-count` 次(默认 3), 每次间隔 `mojang-retry-delay` 毫秒(默认 1000)
- HTTP 204(认证拒绝)不会重试, 仅重试网络错误
- 所有重试耗尽后显示新的踢出消息 `session-retry-exhausted`

### 日志改进

优化登录流程日志可读性:

- 用人类可读的消息替代原始 ProtocolLib 包名输出
- 将内部细节(包类型覆盖, 加密初始化)降为 DEBUG 级别
- 新增 "Verifying session for {player}" 日志, 标识验证开始

### 依赖

- Mockito 5.17.0 → 5.18.0(修复 JDK 25 ByteBuddy 兼容性)

## v0.0.3

### SkinsRestorer 兼容

修复 FastLogin 覆盖 SkinsRestorer 自定义皮肤的问题([TuxCoding/FastLogin#1347](https://github.com/TuxCoding/FastLogin/issues/1347)):

- 通过 SkinsRestorer `/skin` 命令设置的皮肤现在会被保留. 当 SR 有玩家的自定义皮肤时, FastLoginPlus 会跳过自身皮肤
- 新增 `SkinsRestorerCompat` 辅助类, 使用 SR 官方 API(`PlayerStorage.getSkinIdOfPlayer`)
- `plugin.yml` 中添加 SkinsRestorer 为 `softdepend`, 确保加载顺序正确

### 问题修复

- **Paper 服务端 `forwardSkin: false` 无效**: `PaperCacheListener` 在 Paper 上无论 `forwardSkin` 设置如何都会注册, 现已修复为正确读取配置.

## v0.0.2

### 离线白名单

用新的独立 **离线白名单** 功能替代 `switchMode`:

- `offline-whitelist: true`: 仅数据库中已有记录的玩家可以离线模式加入
- 新的离线玩家会被踢出, 显示本地化消息
- 正版玩家不受影响, 通过 Mojang API 自动检测并允许加入
- 数据库中已有的离线玩家仍可正常加入
- 开启 `offline-whitelist` 后自动触发 Mojang API 检查(无需额外开启 `autoRegister` 或 `nameChangeCheck`)

### 移除

- **`switchMode`** 配置项移除, 由 `offline-whitelist` 替代

## v0.0.1

FastLoginPlus 首个独立版本, 基于 [FastLogin](https://github.com/TuxCoding/FastLogin) fork 并增强.

### 项目更名

- 项目更名为 **FastLoginPlus**, Maven 坐标从 `fastlogin` 改为 `fastloginplus`
- 独立版本号体系, 从 v0.0.1 起步
- Java 包名(`com.github.games647.fastlogin`)保留不变, 便于合并上游更新

### 问题修复

- **switchMode 误踢正版新玩家**: 上游 `switchMode` 开启后, 首次加入的正版玩家会被错误踢出([#1359](https://github.com/TuxCoding/FastLogin/issues/1359)). 修复后, 正版玩家会通过 Mojang API 自动检测并正确放行. (注: `switchMode` 已在 v0.0.2 中被 `offline-whitelist` 替代)

### SQLite 并发

- 启用 **WAL (Write-Ahead Logging)** 模式, 代理架构下多线程读写不再互相阻塞
- 设置 **5 秒 busy timeout**, 避免 `SQLITE_BUSY` 瞬间报错
- 所有 `loadProfile` / `save` 操作加 `ReentrantLock`, 防止竞态条件

### fldelete 增强

上游的 `fldelete` 实现较为简陋(硬编码英文, 无 premium 保护, BungeeCord 下不可用), 本版本重写了完整实现:

- 消息文本改为本地化, 支持多语言
- 新增 premium 玩家保护, 不允许删除在线模式玩家的记录
- 支持 BungeeCord 环境, 通过 PluginMessage 转发删除请求
- 删除成功后触发 `BukkitFastLoginPremiumToggleEvent` 事件

### 多语言系统

- 内置 **英文**(`messages_en.yml`)和 **中文**(`messages_zh.yml`)语言文件
- `config.yml` 中通过 `language` 选项指定使用的语言(`en` / `zh` / 自定义)
- 支持自定义语言文件: 设置任意值(如 `ja`), 插件自动加载 `messages_ja.yml`, 不存在时回退到英文
- 启动时自动检测语言文件完整性, 缺失的键值从英文默认文件补全
- 配置文件注释改为中英双语
