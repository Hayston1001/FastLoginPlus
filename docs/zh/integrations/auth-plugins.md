# 其他认证插件

## 可用集成

| 平台 | 认证插件 |
| --- | --- |
| Bukkit/Folia | AuthMe, CrazyLogin, LogIt, LoginSecurity, UltraAuth, xAuth, Passky |
| BungeeCord | BungeeAuth |
| Velocity | 在参与认证的后端安装认证插件 |
| Fabric | EasyAuth, 详见 [Fabric 安装指南](../installation/fabric.md) |

此列表不保证所有插件版本都兼容当前服务端. 请检查各插件的版本要求和启动日志, 并在开放服务器前用测试账号确认登录. 每个后端建议只使用一个认证插件.

## 能力不能互换

不同认证插件支持的自动注册, 登录和账号切换功能不同, 不能假设它们具有与 AuthMe 相同的行为. 如果插件为玩家生成过密码, 切回密码登录时可能需要使用该插件的恢复流程.

Folia 要求认证插件本身支持 Folia. 把 Bukkit FLP 换成 Folia FLP, 不会自动适配其他插件的调度器调用.

BungeeAuth 钩子与后端 AuthMe 不同, 也不创建代理原生 `/flp` 管理命令. 没有明确所有权和身份模型时, 不要组合多套独立自动登录系统.

遇到不兼容或启动错误时, 请保留日志并[报告问题](../operations/reporting.md), 不要通过移除安全检查强行运行.
