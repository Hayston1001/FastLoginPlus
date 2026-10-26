# 事件与扩展边界

经典事件使用 core 接口和各平台实现. 注册监听器时依赖真实的平台事件类, core 接口本身不是 Bukkit `Event`. Folia 使用镜像的 Bukkit 事件类, 仍受自身线程约束.

| Core 契约 | 用途 | 重要边界 |
| --- | --- | --- |
| `FastLoginPreLoginEvent` | 在线路由动作前观察名字/来源/档案 | 不是认证证明, 没有通用取消契约 |
| `FastLoginAutoLoginEvent` | 拦截适用的强制自动登录 | 可取消, 不是覆盖所有注册/前置 AuthMe/Fabric 路径的通用事件 |
| `FastLoginPremiumToggleEvent` | 观察名单状态变更与原因 | 变更通知, 不是新的可信身份 |
| `FastLoginAntiBotEvent` | 拦截连接限制拒绝 | 取消后允许继续, 监听器可以削弱准入保护 |

实现位于经典平台各自 `event` 包. 根据真实触发点判断时机和线程, 不只看接口名. ProtocolLib worker 和代理异步路径不是 Bukkit 主线程. Velocity 异步事件需要完成但不能阻塞事件循环. Folia 动作使用正确的区域/实体/全局调度器.

## 认证集成

`AuthPlugin` 及专用 pre-login 契约提供账号查询和动作. 集成必须区分读取失败与账号不存在, 执行声明策略, 检查归属并验证保存/动作成功. 不能只因会话验证成功就把 `forceLogin` 失败当成功; 支持"已经登录"的路径也需要独立确认.

不要通过直接更新档案或写后端代理模式下并不存在的 FLP 数据库来绕过同意. 自定义管理面板应调用有授权的操作并保留密码/归属安全, 不能把 `Premium=true` 等同于完整登录.

## 协议变更

修改共享 `protocol` 契约, 同时测试 Velocity 和 Fabric 生成副本. 验证来源, 时效, 名字/UUID/连接关联, 策略完整性与重复处理. 使用 FLP 通道名不代表插件消息已经认证. 除非另行授权并设计兼容迁移, 保留既有 `fastloginplus:force` 格式.

Fabric 内部 gate/bridge 不是已承诺稳定的公开事件 API. 不要承诺与经典事件完全一致, 或在没有兼容契约时让第三方 Mod 依赖反射内部实现.

回归测试应覆盖断线, 重连, 过期回调, 重复请求, 策略改变, 读写失败与关闭/重载边界. 见[构建检查](build.md), [登录信任](../LOGIN-FLOW.md).
