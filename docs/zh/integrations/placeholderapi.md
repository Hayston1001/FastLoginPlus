# PlaceholderAPI

Bukkit/Folia 检测到 PlaceholderAPI 后注册扩展. 扩展由 FLP 提供, 不要默认要求另从 eCloud 下载一份.

| 占位符 | 值/含义 |
| --- | --- |
| `%fastloginplus_status%` | `Premium`, `Cracked`, `Unknown`, 表示 FLP 缓存的运行时状态 |
| `%fastloginplus_is_premium%` | 根据该状态返回 `true` 或 `false` |
| `%fastloginplus_floodgate%` | 根据缓存平台状态返回 `Java`, `Bedrock`, `Linked`, `Unknown`; 平台状态缺失时默认 `Java` |

安装 PlaceholderAPI 后可测试:

```text
/papi parse me %fastloginplus_status%
```

这些值用于展示, 不是持久化数据库查询, 更不是绕过认证的许可. 离线玩家或尚未填充状态的连接可能显示默认/未知值. 平台身份与 Java 正版状态是不同维度, 不能合并为一个安全判断.

没有替换时检查 PlaceholderAPI 启动, FLP 扩展注册, 占位符拼写和展示插件是否支持 PlaceholderAPI. 代理插件和 Fabric Mod 不原生提供该扩展.
