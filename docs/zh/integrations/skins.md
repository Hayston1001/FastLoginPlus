# 皮肤与 SkinsRestorer

`forwardSkin: true` 允许 FLP 把已验证 Java 档案的纹理交给服务器. Mojang `textures` 属性同时可以包含皮肤和披风, 关闭后影响两者, 不只是皮肤图片.

独立服使用 Bukkit/Paper 对应路径应用纹理. 代理后面由**代理** `forwardSkin` 决定转发档案, 修改后端副本不会覆盖它. 其他插件或服务端档案缓存仍可能提供皮肤, 所以 `forwardSkin: false` 不是"所有人都没有皮肤"的总开关.

## SkinsRestorer

按 SkinsRestorer 自身指南选择受支持的平台/版本. FLP 兼容路径会检查自定义皮肤并避免覆盖. 连接使用离线 UUID 时, 需要身份的查询仍应在适当位置使用已验证 Mojang 身份, 不能把连接 UUID 默认当成 Mojang UUID.

发生冲突时:

1. 记录服务端, FLP, 代理和 SkinsRestorer 版本, 以及玩家是否明确设置了自定义皮肤.
2. 检查负责身份的一端 `forwardSkin` 和所选 `premiumUuid` 策略.
3. 在测试环境分别用已知自定义皮肤和默认正版皮肤重连.
4. 清理数据之前, 检查 debug 日志中的兼容 API 错误和档案缓存影响.

看见正版皮肤不代表认证成功. 这项经典集成不承诺 Fabric Mod 内实现 SkinsRestorer API, 也不保证兼容所有皮肤插件.
