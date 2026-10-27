# Fabric 配置

配置文件: Fabric 后端的 `config/fastloginplus-fabric.yml`. 停服修改后重启后端. 本文件只控制本地设置; Java 和 Bedrock 登录策略在 [Velocity 配置](velocity.md)中设置.

| 选项 | 默认值 | 作用 |
| --- | --- | --- |
| `language` | `en` | 后端消息语言, 支持 `en` 和 `zh`; 其他非空值使用英文并告警 |
| `debug` | `false` | 输出排障日志 |
| `check-update` | `true` | 在后台检查新版本, 不自动下载安装 |
| `update-check-interval` | `24` | 更新检查间隔, 单位为小时; 小于 1 时使用 24 |
| `allowed-proxy-ids` | 空 | 受信任的 Velocity 代理 UUID, 多个 UUID 用逗号分隔; 从代理 `plugins/fastloginplus/proxyId.txt` 获取 |

`allowed-proxy-ids` 不是转发密钥. 留空会影响依赖可信代理身份的 Bedrock 登录和正版绑定. 代理的 `forwarding.secret` 则填写在 FabricProxy-Lite 配置中. 安装与 EasyAuth 配置见 [Fabric 安装指南](../installation/fabric.md).
