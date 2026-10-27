# Folia 独立服

Folia 使用区域化线程. 插件兼容 Paper 不代表兼容 Folia.

## 安装步骤

1. 备份服务器和认证数据, 检查[运行要求](../overview/requirements.md).
2. 停服, 将 **Folia** FLP 产物放入 `plugins/`, 不使用 Bukkit 产物. 认证插件和数据包库也必须选择支持 Folia 的构建.
3. 在 `server.properties` 设置 `online-mode=false`. 直接连接的独立服关闭 BungeeCord 和 Velocity 转发.
4. 启动一次, 确认所有依赖成功启用, 再停服. 按[独立服安装说明](standalone.md)编辑 `plugins/fastloginplus/config.yml`, 包括 UUID 选择.
5. 重启服务器, 确认插件正常加载, 再使用测试账号检查正版和密码登录.

Folia 的权限前缀是 `fastloginplus.folia.command.*`, 不是 `fastloginplus.bukkit.command.*`.

## 位于代理后面时

使用 [Velocity](velocity.md) 或 [BungeeCord](bungeecord.md) 指南, 后端安装 Folia 版本. 此时 FLP 不需要后端 ProtocolLib, 但其他插件仍需支持 Folia.

安装或修改配置后请完整重启, 不要使用热加载器或通用 reload 命令.

Folia 的插件兼容与调度模型见[官方文档](https://docs.papermc.io/folia/).
