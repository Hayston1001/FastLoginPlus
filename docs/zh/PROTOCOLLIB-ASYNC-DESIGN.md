# ProtocolLib 异步监听器设计

本文针对 **Bukkit/Folia 独立服 ProtocolLib 路径**, 不是代理后端身份处理. 当前实现以 `.optionAsync()` 和 `registerAsyncHandler(...).start()` 注册. 不要把历史同步监听器实验说成正在使用的设计.

## 为什么保留异步路径

登录可能涉及数据库/HTTP. 改同步实现需要在实际 ProtocolLib/服务端版本组合中证明取消, 注入 START 和原版状态转换正确. 旧失败报告或一次成功实验不足以支持替换现有路径.

当前实现把敏感的加密/档案/伪 START 操作串行到连接事件循环, 协调 ProtocolLib 处理锁与 marker 信号, 防止重复加密响应. Async marker 超时限制清理边界, 不是建议玩家等待那么久才能登录.

## 验证必须真实发生

Mojang 名字查询或写入 `spoofedUUID` 不等于正版会话. 不能因找到/注入 UUID 就把连接标为已验证. 每次登录都有新的握手挑战, 缓存的名字/UUID 不能替代 `hasJoined` 或代理原生验证握手.

注入数据包可能重新进入不同版本的拦截层. 改动前追踪实际 ProtocolLib, 事件取消, marker 生命周期和原版处理器, 不仅根据 `filters=false` 参数就推断全部版本行为.

## 映射失败与诊断

启动时检查 `ENCRYPTION_BEGIN` 能否解析, 运行时覆盖可修复部分映射, 不能保证修复所有拦截缺失. 没有验证日志可能意味着路径根本没执行, 超时, 拒绝或密码流程都是症状, 不能证明安全回退.

1. 记录准确服务端, ProtocolLib 和 FLP 构建, 开启 FLP debug, 真实客户端复现.
2. 对同一连接关联 START, 会话建立, 加密响应, 会话服务结论, 事件循环操作及账号动作.
3. 验证数据包库是否兼容目标服务端, 不要默认更高 ProtocolLib 版本解决所有映射.
4. 修改共享行为前检查 Bukkit 与 Folia 副本, 保留调度差异.

没有兼容组合时保留账号保护并反馈问题. 不推荐仅靠 UUID 绕过, 跳过会话验证或把 `/flp premium` 当成认证离线会话的方法.

## 有证据时再重新设计

上游提供受支持的拦截变化, 可复现状态机竞争或频繁映射失败时, 可以评估新方案. 要有回归测试和首次登录, 重连, 无效会话, 服务故障, 重复响应及断线的实际覆盖, 不能只靠编译和启动判断登录正确.

源码: [ProtocolLib 监听器/任务](https://github.com/Hayston1001/FastLoginPlus/tree/main/bukkit/src/main/java/com/github/games647/fastlogin/bukkit/listener/protocollib). 另见[登录信任](LOGIN-FLOW.md), [故障排查](operations/troubleshooting.md).
