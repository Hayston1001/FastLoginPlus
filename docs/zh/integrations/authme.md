# AuthMeReloaded

AuthMe 安装在 Bukkit, Paper 或 Folia 服务器上. 代理网络中, 每个需要认证的后端都需要安装兼容版本的 AuthMe 和 FLP; 不要把后端 AuthMe 插件放进代理的 `plugins/`.

## 选择版本

| 项目 | AuthMe 5.x | AuthMe 6.x |
| --- | --- | --- |
| 密码账号 | 支持注册和密码登录, FLP 可配合自动登录 | 同样支持 |
| 正版账号管理 | 使用 FLP 的账号命令 | FLP 协调 AuthMe 的正版账号功能, 使用 FLP 的账号命令 |
| 登录前对话框 | 不具备 AuthMe 6 的对话框功能 | 适用版本的 Paper/Folia 可显示登录前对话框 |
| Bedrock 自动注册和登录 | 不保证支持新版提前登录流程 | 需要对应的 AuthMe 接口; 6.0.1 提供所需能力 |

选择与服务器兼容的构建, 并在启动日志中确认 AuthMe 和 FLP 都成功加载. 各版本和服务端的具体兼容性应在自己的服务器上验证.

## 账号命令和设置

- 使用 `/flp premium` 绑定正版账号, 使用 `/flp cracked` 切回密码登录. 操作前请阅读[绑定与切换](../accounts/binding.md).
- 使用 AuthMe 6 时, FLP 会管理其正版功能; 不要同时配置另一套相互冲突的正版验证或代理自动登录.
- 如果使用 AuthMe 的代理伴生插件, FLP 管理该插件时会设置 `premium.keepOfflineUuidCompatibility: false`. 玩家使用哪种 UUID 由 FLP 的 `premiumUuid` 设置决定.
- 要遵守 AuthMe 的每 IP 注册数量限制, 在后端 FLP 配置中启用 `respectIpLimit`.

## 切换前请备份

`/flp cracked` 可能清除或重置 AuthMe 账号记录. 操作前备份认证数据库, 并确保玩家可以安全地重新注册. 这与 EasyAuth 保留原密码的行为不同.

已有密码账号不会仅因为名字属于正版玩家就被自动接管. 玩家需要先登录并同意绑定. 如果登录对话框阻止玩家进入, 请检查代理和后端日志, 参阅[故障排查](../operations/troubleshooting.md).
