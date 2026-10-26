# FastLoginPlus

[English](https://github.com/Hayston1001/FastLoginPlus/blob/main/README.md) | 简体中文

[![Modrinth downloads](https://img.shields.io/modrinth/dt/dmpkLjdL?style=flat-square&logo=modrinth&label=Modrinth&color=00AF5C)](https://modrinth.com/plugin/fastloginplus)
[![Build](https://img.shields.io/github/actions/workflow/status/Hayston1001/FastLoginPlus/gradle.yml?branch=main&style=flat-square&logo=githubactions&logoColor=white&label=Build)](https://github.com/Hayston1001/FastLoginPlus/actions/workflows/gradle.yml)
[![Release](https://img.shields.io/github/actions/workflow/status/Hayston1001/FastLoginPlus/release.yml?style=flat-square&logo=githubactions&logoColor=white&label=Release)](https://github.com/Hayston1001/FastLoginPlus/actions/workflows/release.yml)
[![MIT](https://img.shields.io/badge/License-MIT-2563EB?style=flat-square)](LICENSE)

[![modrinth](https://cdn.jsdelivr.net/npm/@intergrav/devins-badges@3/assets/cozy/available/modrinth_vector.svg)](https://modrinth.com/plugin/fastloginplus)
[![documentation](https://cdn.jsdelivr.net/npm/@intergrav/devins-badges@3/assets/cozy/documentation/gitbook_vector.svg)](https://hayston.gitbook.io/flp-wiki/zh/)

**连接经过验证的 Minecraft 身份与服务器认证插件.** 基于 [FastLogin](https://github.com/TuxCoding/FastLogin) 的活跃维护分支.

FastLoginPlus 可以让通过验证的 Java 正版持有人和受支持的 Floodgate 玩家免密登录, 同时为其他账号保留密码认证. 登录方式由身份验证, 服务器策略和本地账号归属共同决定, 正版用户名本身不是所有权证明.

> 文档面向即将发布的 **1.0.0**. 旧发布产物不一定包含全部文档功能.

## 功能

### 核心功能(来自 FastLogin)

* 通过 Mojang API 自动检测正版账号
* 自动注册正版玩家
* 正版 UUID 和皮肤转发

### 新特色(来自 FastLoginPlus)

* **[AuthMeReloaded](https://modrinth.com/plugin/authmereloaded) 6.0 兼容**: 自动检测 AuthMeReloaded 版本, 无需用户配置
* **[SkinsRestorer](https://github.com/SkinsRestorer/SkinsRestorer) 兼容**: 不再覆盖 SkinsRestorer 的自定义皮肤
* **Folia 支持**: 独立模块, 使用 Folia 兼容的调度器
* **Fabric 初步支持**: 处于开发阶段, 已适配 Velocity + [Easyauth](https://github.com/NikitaCartes/EasyAuth) 3.4.x, 正逐步扩大覆盖面
* **代理端 SQLite 支持**: BungeeCord 和 Velocity 内置 SQLite JDBC 驱动, 适用于单代理小型服
* **反机器人增强**: 每 IP 速率限制, 突发检测, 临时封禁, 可信 IP 白名单
* **多语言**: 支持切换自定义语言文件
* **分平台配置模板**: Bukkit/Fabric 与 BungeeCord/Velocity 各自生成专属的 config 文件
* **会话验证重试**: Mojang 验证遇到网络错误时自动重试, 而非直接失败

## 贡献与帮助

[![问题反馈](https://img.shields.io/badge/%E9%97%AE%E9%A2%98%E5%8F%8D%E9%A6%88-D97706?style=flat-square&logo=github&logoColor=white)](docs/zh/operations/reporting.md)
[![参与贡献](https://img.shields.io/badge/%E5%8F%82%E4%B8%8E%E8%B4%A1%E7%8C%AE-2EA44F?style=flat-square&logo=github&logoColor=white)](CONTRIBUTING_zh.md)
[![从源码构建](https://img.shields.io/badge/%E4%BB%8E%E6%BA%90%E7%A0%81%E6%9E%84%E5%BB%BA-0969DA?style=flat-square&logo=gradle&logoColor=white)](docs/zh/development/build.md)

## 星标历史

<a href="https://www.star-history.com/?repos=hayston1001%2Ffastloginplus&type=date&releases=&legend=bottom-right">
 <picture>
   <source media="(prefers-color-scheme: dark)" srcset="https://api.star-history.com/chart?repos=hayston1001/fastloginplus&type=date&theme=dark&legend=bottom-right" />
   <source media="(prefers-color-scheme: light)" srcset="https://api.star-history.com/chart?repos=hayston1001/fastloginplus&type=date&legend=bottom-right" />
   <img alt="Star History Chart" src="https://api.star-history.com/chart?repos=hayston1001/fastloginplus&type=date&legend=bottom-right" />
 </picture>
</a>

## 许可证

[MIT](LICENSE). 原作者为 [games647](https://github.com/TuxCoding), 由 [Hayston1001](https://github.com/Hayston1001) 维护
