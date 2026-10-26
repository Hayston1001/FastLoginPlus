# 命令与权限

## 在哪里执行

在 **Bukkit/Folia 或 Fabric 后端**执行 `/flp`, 按支持范围使用游戏内或后端控制台. 当前 Velocity/BungeeCord 插件没有注册原生 `/flp`. 代理部署由后端把名单变更转发给代理, 不维护第二套 FLP 名单.

## Bukkit 与 Folia

下表 `<prefix>` 在 Bukkit/Paper 为 `fastloginplus.bukkit.command`, 在 Folia 为 `fastloginplus.folia.command`.

| 命令 | 权限 | 默认与作用 |
| --- | --- | --- |
| `/flp premium` | `<prefix>.premium` | 所有人; 请求自己的正版路径, 按配置确认 |
| `/flp premium <玩家>` | `<prefix>.premium.other` | 默认管理员; 授权其他名字 |
| `/flp cracked` | `<prefix>.cracked` | 管理员; 切换自己的名字为 cracked |
| `/flp cracked <玩家>` | `<prefix>.cracked.other` | 默认管理员; 修改其他名字 |
| `/flp delete <玩家>` | `<prefix>.delete` | 管理员; 删除非 premium FLP 记录 |
| `/flp` | 直接检查管理员身份 | 版本与用法 |

**没有实现 `delete.other` 检查**. Delete 必须指定名字, 只检查基础权限. Premium/cracked 的 `.other` 节点在描述符中包含基础权限. 权限插件可以修改实际授予结果.

经典别名为 `prem`, `unpremium`, `del`. `/premium` 不是 FLP 别名. 不指定玩家的 premium/cracked 要求玩家身份, 控制台必须提供目标名字. 示例参数是名字, 不是 UUID.

代理模式需要后端在线玩家承载插件消息. 无载体时, 经典后端把 toggle/delete 请求保存在 `pending-relay.json`. 队列按后端独立维护, 重启后保留, 等待该后端出现载体. 没有队列 TTL, 不要认为放置不用的请求已经过期. 同名冲突的待转发切换以当前队列值为准. 继续发命令前应检查代理/后端最终反馈.

## Fabric

| 命令 | 要求 |
| --- | --- |
| `/flp premium` | 玩家已登录状态正常的 EasyAuth 密码账号 |
| `/flp premium <玩家>` | 原版 gamemaster/管理员命令权限 |
| `/flp cracked` | gamemaster/管理员权限, 且自己的账号已经登录 |
| `/flp cracked <玩家>` | gamemaster/管理员命令权限 |
| `/flp delete <玩家>` | gamemaster/管理员命令权限, 本地账号不能仍处于绑定状态 |

Fabric 不使用 Bukkit 权限节点或经典别名, 没有注册裸 `/flp` 的版本/帮助处理和 `/flp reload`. 控制台指定玩家的命令需要在线玩家承载消息, 空服时报告失败, 不会持久化排队. 代理最终结果与后端即时转交提示不同.

## 破坏性与延迟影响

授予权限之前先看[绑定与切换](binding.md). AuthMe cracked 清理可能删除/重置认证账号. EasyAuth 解绑保留已有密码, 无密码账号释放需要 30 秒内再次操作. FLP delete 不删除世界或所有认证记录.

`kick-toggle` 影响踢出行为, 不提供认证证明. 所有命令都不会 transfer 或自动重连玩家. 管理员应核对目标名字, 确认最终写入并测试下一次登录.
