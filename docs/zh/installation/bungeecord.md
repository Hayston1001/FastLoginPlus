# BungeeCord 与经典后端

本页适用于 BungeeCord + Bukkit/Paper/Folia 认证后端, 不适用于 Fabric. 兼容 Bungee 的分支也应根据实际 API 和转发实现测试.

## 安装与配置

1. 备份代理, 后端, FLP 记录和认证数据库, 停止整个网络.
2. 代理安装 **BungeeCord** FLP. 每个参与认证的后端安装对应的 **Bukkit** 或 **Folia** FLP 及认证插件. 代理模式下 FLP 不需要后端 ProtocolLib.
3. BungeeCord `config.yml` 设置 `online_mode: false`, `ip_forward: true`. FLP 对单个连接请求 Mojang 认证.
4. 后端 `server.properties` 设置 `online-mode=false`, `spigot.yml` 设置 `settings.bungeecord: true`. Paper 关闭 Velocity modern forwarding. Paper 的 BungeeCord 转发 online-mode 配置应与代理的离线设置一致, 键的位置以该 Paper 版本配置说明为准.
5. 启动代理生成实例 UUID. 使用 **BungeeCord 自身 `config.yml` 中的 `stats` UUID**, 不是 Velocity 的 `proxyId.txt`. 将 UUID 填入每个后端的 `plugins/fastloginplus/allowed-proxies.txt`, 每行一个, 再重启后端. 不要随意删除或重新生成代理 UUID.
6. 代理配置 FLP 策略与数据库, 后端配置认证插件和本地消息.
7. 用防火墙限制后端端口只接受受信任代理, 测试[正常与异常登录路径](verification.md).

## 安全边界

BungeeCord legacy forwarding 不认证完整的转发身份. 当前 FLP 的签名声明会加强自身决策所需的验证, 但 UUID 白名单和档案属性都不能让公网后端变得安全. 能控制代理或受信任后端插件的一方, 本身就在信任边界内. 代理 ID 也会通过插件消息传递, 应视为敏感值, 不能当作防火墙替代品.

代理 FLP 配置保持 `verify-backend-messages: true`. 为兼容旧环境而临时关闭会降低消息检查强度, 不是修复错误白名单的方法.

## 命令与存储

在后端游戏内或后端控制台执行 `/flp`. FLP 没有注册 BungeeCord 原生命令 `/flp`. 后端转发可能需要在线玩家承载消息; 经典后端在没有载体时持久化待转发请求.

代理数据库持有网络的 FLP premium/cracked 记录, 后端认证数据库存储密码和本地账号状态. 可选的 BungeeAuth 钩子是另一项集成, 不代表应该把 AuthMe 后端 JAR 装进代理.

参考官方 [IP 转发指南](https://www.spigotmc.org/wiki/bungeecord-ip-forwarding/)和[命令说明](../accounts/commands.md).
