# `craftapi/` —— 内化的 CraftAPI(Mojang API 客户端)

[English→](../en/CRAFTAPI-BASELINE.md)

这个模块**不是 FastLoginPlus 自己的代码**. 它是上游 [CraftAPI](https://github.com/games647/CraftAPI) 库的内化副本, 该库掌管 FLP 与 Mojang 之间的全部 HTTP 交互: 名字→UUID 查询、会话验证(`hasJoined`)、离线 UUID 算法以及 UUID 存储格式. FLP 自身不实现任何 Mojang 请求.

Maven 坐标是 `com.github.games647:fastloginplus.craftapi:${revision}`(`maven.deploy.skip=true`); Java **包名保持不变**(`com.github.games647.craftapi`), 因此 `core`、`bukkit`、`folia`、`bungee`、`velocity` 都不需要改源码. 该副本被 shade 进全部四个平台 JAR.

## 为什么要内化

上游最后一个 release 不可复现, 也无法安全升级:

- 上游 `0.8.1` tag 指向 `863ecfac`(2024-05-13, Java 11 + `java.net.http.HttpClient`), 而我们实际发布的产物是从 `6f0ded9f`(2024-05-05)构建的 Java 8 / `HttpURLConnection` 版本. 我们发布的东西在上游没有对应 tag.
- 上游 `1.0` 线是 Java 11 字节码(`v55`). `javac --release 8` 编译它不会有任何告警, 所以直接引入会把 bukkit 的运行时下限从 Java 8 悄悄抬到 Java 11, 而且只在用户服务器上以 `UnsupportedClassVersionError` 的形式暴露. 它还会丢掉公开的 `setOutgoingAddresses` / `sslFactory` 入口, 也就是 `ip-addresses` 功能.
- 我们发布的那份构建里有两个缺陷在此修复(见下): `mojang-request-limit` 形同虚设, 以及代理回退请求的是端点根路径而不是玩家名.

本文是内化模块的**基线档案**: 来自哪棵上游树、本地改了什么、什么不许静默改变、以及如何升级. 它是一份纳入版本管理的常青文档, 位于 `docs/en/` —— 相当于本地 `deps/` 目录里那些依赖评估的对应物, 之所以提升到这里, 是因为它描述的是我们自己拥有的模块, 而不是我们跟踪的外部依赖. 插件 JAR 不受影响: 只有 `craftapi/src/main/resources` 会被打包, 所以这个文件永远不会进入用户的服务器.

## 上游基线

| 项 | 值 |
|---|---|
| 仓库 | `https://github.com/games647/CraftAPI` |
| 提交 | `6f0ded9f`(2024-05-05)—— 我们发布的 `craftapi-0.8.1.jar` 就是由这棵树构建的 |
| Java | `release 8`, 字节码 `v52`(由根 POM 的 `enforceBytecodeVersion` 规则断言) |
| 源码规模 | 28 个主源码文件, 10 个测试文件, 一个二进制测试资源(`yggdrasil_session_pubkey.der`) |
| 许可证 | [Unlicense](../../craftapi/src/main/resources/META-INF/LICENSE-CraftAPI-Unlicense.txt)(库本体, 公有领域)与 [MIT](../../craftapi/src/main/resources/META-INF/LICENSE-FastUUID-MIT.txt)(FastUUID, © 2018 Jon Chambers). 两者都会打进每个平台 JAR. |

## 本地改动

行为修复(均有离线测试覆盖):

1. `MojangResolver` —— 主端点返回 `403` 时, 该 resolver 实例永久切换到 `api.minecraftservices.com`(已知的 Mojang 配置错误 WEB-7591 / WEB-7666); 如果备用端点也返回 `403`, 则抛 `IOException`, 而不是把玩家当成盗版.
2. `MojangResolver` —— 429 处理: 被限流的直连请求会通过代理用*完全相同的完整 URL*重试一次; 如果代理也被限流(或没有配置代理), 则抛 `RateLimitException`.
3. `MojangResolver.setMaxNameRequests` —— 将取值夹到 `0..600`(旧代码 `Math.max(600, value)` 会把任何配置值都变成 600)并重建滑动窗口限流器, 于是 `mojang-request-limit` 真正生效. `0` 表示"从不直连查询 Mojang, 总是走代理". `MAX_NAME_REQUESTS_LIMIT` 暴露该上界.
4. `MojangResolver.getProxyConnection` —— 向代理发送含玩家名的完整 URL(此前用的是端点根路径, 玩家名丢失), 并且把"代理选择为空"视同 `DIRECT`, 而不是抛 `IndexOutOfBoundsException`.
5. `MojangResolver.findProfile` —— 响应处理显式化: `200` 但缺 `id`/`name` 视为 `IOException`(绝不判为正版), `204`/`404` 表示"非正版", 其他任何状态码都是 `IOException`. `hasJoined(..., null)` 被定义为"不发送 `ip` 参数", `core` 中的 `ProxyAgnosticMojangResolver` 现在依赖这一语义; 畸形的 `200` 返回 `Optional.empty()` 而不是抛 `NPE`.
6. `AbstractResolver` —— 连接按 `HttpURLConnection` 创建(这样离线测试可以把端点指向本地 `http://` 服务器), 且出口地址的 SSL socket factory 只装在 `HttpsURLConnection` 上; `readJson` 把畸形 JSON 体转成 `IOException`, 而不是让未受检的 `JsonSyntaxException` 逃出去.
7. `TickingRateLimiter` —— 每次取用时丢弃*所有*过期桶, 而不只是最旧的那个.
8. `MojangResolver` —— 静态初始化时调用 `HttpsURLConnection.getDefaultSSLSocketFactory()`(JDK-8197807: 否则第一个 HTTPS 请求要为 SSL context 初始化买单).
9. 端点字段(`uuidUrl`、`backupUuidUrl`、`useBackupUuidUrl`、`hasJoinedUrlRaw`、`hasJoinedUrlProxyCheck`)改为包内可见, 以便测试重定向它们; FLP 从不把它们暴露为配置.
10. `MojangResolver` —— 粘滞的 `403` 状态不再兼作"本次请求走了备用端点": 在途请求的端点改由参数传递(`viaBackupEndpoint`), 该标记是 `volatile` 的. 两个并发查询若都从主端点收到 `403`, 现在会各自回退, 而不是其中一个读到另一个刚设下的标记、然后抛出 `Both Mojang APIs returned 403 Forbidden`. (FLP 在并发登录线程上查名字, 所以这在生产上是可达的, 上游 1.0 也是同样的形态.)
11. `MojangResolver` —— 每个非 `200` 响应都会在分支返回、递归或抛异常之前被读取完(`drainQuietly`), `hasJoined` 对其 `404`/`204` 的提前返回同样如此, 而未产生响应的连接会用 `disconnect()` 释放. 未读的错误响应体会让客户端丢弃 socket 而不是把它还回 keep-alive 池, 所以以前成片的 `429` 或代理错误会留下连接残留.

整理性改动: 所有文件加上 FLP 的 MIT 许可证头(FastUUID 保留其上游声明)并满足 Checkstyle(`OperatorWrap` 换行、花括号、javadoc 的 `@param`). 唯一对 API 可见的后果是 `FastUUID` 变为 `final`、`Textures.KEY` 变为 `static final`(值相同)—— 两者都被 `bukkit`/`folia` 引用, 因此该改动由 reactor 构建做编译检查.

## 什么不许静默改变

- `UUIDAdapter.generateOfflineId`、`toMojangId`、`parseId` —— 离线 UUID 就是玩家的身份, 而无连字符小写形式就是数据库格式. `UUIDAdapterGoldenTest` 用此前发布的 `craftapi-0.8.1.jar` 产生的值把这两件事钉死.
- 依赖版本(`gson 2.10.1`、`guava 32.1.2-jre`): `bukkit`/`folia` 会把两者 shade 并 relocate, `bungee`/`velocity` 则有意排除两者(代理自带一份). 升级它们会改变每个平台 JAR 的内容.

## 升级流程

上游*有响应但已停滞*: 2024-05 之后没有 release, 只有一个分支(`main`), 而 `main` 就是 Java 11 那条线. 从上游引入任何东西之前: 重新核对渠道/tag 漂移、Java 下限与被丢弃的 `ip-addresses` 入口(三者都在上文描述过), 重新套用改动清单, 然后跑 `mvn test -pl craftapi` 以及下面的产物一致性检查.

## 验证

- `mvn test -pl craftapi` —— 78 个测试, 不需要网络(上游测试套件, 加上离线 `HttpServer` / 原始 socket 套件与黄金向量).
- 与已发布 `craftapi-0.8.1.jar` 的类一致性: 同样的 32 个类名; 签名差异只有上面那些有意为之的改动, 加上编译器生成的枚举/lambda 命名.
- 每个平台 JAR 都恰好包含这 32 个类的一份副本, 全为 `v52`, 外加两个许可证文件.
- `mvn dependency:tree` 不再解析 `com.github.games647:craftapi`.
