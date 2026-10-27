# 数据库配置

## 哪个数据库

独立服 FLP 在本端保存 premium/cracked 记录. 代理部署以**代理** FLP 数据库为准, 经典后端在代理模式下不再打开第二个 FLP 名单数据库. 认证插件数据库是另一份本地账号存储, Fabric 上的 EasyAuth 也是如此.

SQLite 是默认方案. 不要让多个进程在网络共享盘使用同一个 SQLite 文件, 不要未经一致性备份直接复制运行中的数据库. 多代理或多后端账号同步需要单独设计与测试, 选择 MySQL 并不能解决所有跨服竞争和本地账号不一致.

## 配置 MySQL 或 MariaDB

不需要外部数据库时, 保持默认的 SQLite 配置即可. 如需使用 MySQL 或 MariaDB:

1. 在数据库服务器创建数据库和专用账号, 并授予该账号访问此数据库所需的权限.
2. 停止 FLP 所在服务器. 独立服编辑本机的 `plugins/fastloginplus/config.yml`; 代理部署编辑代理的同一路径.
3. 修改文件中已有的以下选项, 将示例值替换为实际信息:

   ```yaml
   driver: 'mysql'
   database: 'fastloginplus'
   host: '127.0.0.1'
   port: 3306
   username: 'flp_user'
   password: '替换为数据库密码'
   ```

4. 根据数据库和驱动要求配置加密连接. BungeeCord 不自带 MariaDB 驱动, 切换前请先确认服务端有可用的兼容驱动.
5. 启动服务器并检查数据库连接错误. 原有 SQLite 数据不会因为修改配置而自动迁移, 切换已有服务器前应备份并另行安排数据迁移.

这些是 FLP 名单数据库设置, 不会配置或迁移认证插件的账号数据库.

## 选项

| 选项 | 默认值 | 含义 |
| --- | --- | --- |
| `driver` | `sqlite` | FLP 存储, MySQL/MariaDB 使用 `mysql` 配置 |
| `database` | `{pluginDir}/FastLogin.db` | SQLite 路径, MySQL 时改为数据库名 |
| `host` | `127.0.0.1` | MySQL 主机 |
| `port` | `3306` | MySQL 端口 |
| `username` | `myUser` | 必须改为专用数据库用户 |
| `password` | `myPassword` | 必须修改, 不要公开 |
| `timeout` | `30` | 连接池获取连接的超时, 秒 |
| `lifetime` | `1800` | 连接最大存活秒数; 0 不限制, 小于 300 的正值会被提升 |
| `useSSL` | `false` | 启用所配置的 MySQL SSL 路径 |
| `sslMode` | `Required` | 该 SSL 路径传给 JDBC 驱动, 应检查驱动的验证模式 |
| `allowPublicKeyRetrieval` | `false` | SSL 路径中的 MySQL 认证公钥获取选项 |
| `ServerRSAPublicKeyFile` | 未设置 | 驱动/认证需要时指定本地 RSA 公钥文件 |

这些 FLP 选项不配置 EasyAuth 存储. Fabric 支持的组合使用 EasyAuth 本地 SQLite 并另外读取账号状态. 不要向 Fabric 后端本地 YAML 添加 `driver`.

## MySQL/MariaDB 注意事项

启动 FLP 前创建数据库和最小权限账号. `lifetime` 应低于数据库服务器空闲超时. 按实际 JDBC 驱动检查 TLS 和认证参数, 只有 `useSSL` 不代表已经验证主机名/证书.

各产物运行时驱动不同: Bukkit/Folia 通常使用服务器提供的 JDBC 驱动; Velocity 内置 SQLite 和 MariaDB, BungeeCord 内置 SQLite 但没有内置 MariaDB 驱动. 不要假设两个代理的 MySQL 配置都开箱可用, 切换前确认驱动来源. 已有 SQL 支持不代表可以往 Java 8 服务器放入任意新版驱动.

## 备份与错误

应一起备份 FLP 记录, 认证账号, 待转发队列, 代理 ID 和玩家数据. 只备份 FLP 不能恢复密码或撤销世界数据变化. 读写失败后先检查错误和持久化状态, 再重试账号命令, 不能通过把所有名字当成新账号来"修复"数据库故障.

另见[备份与升级](../operations/upgrading.md), [名单语义](../accounts/lists.md).
