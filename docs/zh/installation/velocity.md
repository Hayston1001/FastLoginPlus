# Velocity 与经典后端

代理和每个参与 FLP 认证的后端都要安装 FLP. 账号命令在后端执行, 不是 Velocity 控制台. Fabric 见[单独指南](fabric.md).

## 推荐方式: modern forwarding + Paper/Folia

1. 停止代理和后端, 备份 FLP 与认证插件数据.
2. 代理 `plugins/` 安装 **Velocity** FLP 产物. Paper 安装 **Bukkit** FLP, Folia 安装 **Folia** FLP, 后端同时安装认证插件. 此部署下 FLP 不需要 ProtocolLib.
3. 编辑 `velocity.toml`:

   ```toml
   online-mode = false
   player-info-forwarding-mode = "modern"
   ```

   FLP 按连接请求在线验证, 不要因为部分玩家是正版就把整个代理设为在线模式.
4. 每个后端 `server.properties` 设置 `online-mode=false`, `spigot.yml` 设置 `settings.bungeecord: false`.
5. 在 `config/paper-global.yml` 启用 `proxies.velocity.enabled`, 设置 `proxies.velocity.online-mode: false`, 并把 `proxies.velocity.secret` 设为 Velocity 的 `forwarding.secret` 内容. 旧版 Paper 使用 `paper.yml`, 应查阅对应版本文档, 不要同时创建两套配置.
6. 启动一次生成 FLP 文件. 把代理 `plugins/fastloginplus/proxyId.txt` 中的 UUID 复制到每个后端 `plugins/fastloginplus/allowed-proxies.txt`, 每行一个 UUID. 修改后停服并重启后端.
7. Java/Bedrock 策略和 FLP 数据库配置放在**代理**. 后端语言及认证插件配置放在**后端**. 共享认证数据库不能代替代理的 FLP 名单.
8. 限制后端端口只允许代理连接, 完成[验证测试](verification.md).

转发 secret 与 FLP 代理 ID 是用途不同的两个值, 不能互相填入对方文件. 两者都应妥善保管, 不要发到问题报告里.

## Spigot 或 legacy forwarding

原生 Spigot 不支持 Velocity modern forwarding. 使用 Velocity 的 `legacy` 模式时, 后端开启 `settings.bungeecord: true`, 关闭 Paper Velocity modern forwarding, 代理和后端都保持 `online-mode=false`. FLP 代理 ID 白名单按上面相同方法配置.

Legacy 的转发链本身不提供认证. 当前 FLP 会对自身正版准入所用的身份声明验签, 但这不能保护整个 Minecraft 登录链和其他插件. 必须做好防火墙和后端隔离. 不要同时启用两种转发模式. 条件允许时优先使用 modern forwarding; 本指南不为任意转发扩展作兼容承诺.

## 检查结果

- 后端启动日志显示代理模式和非空的受信任代理列表, 不需要 FLP 独立服数据包拦截.
- 代理持有 FLP 记录. 后端仍读取本地认证账号, 结合代理声明的策略决定账号动作.
- 正版验证成功但存在未同意绑定的密码账号时, 仍可能需要密码, 不代表转发失败.
- 请求已转发不等于数据库已修改成功. 应检查最终反馈与重连行为.

平台配置依据: [Velocity 转发](https://docs.papermc.io/velocity/player-information-forwarding/), [网络安全](https://docs.papermc.io/velocity/security/).
