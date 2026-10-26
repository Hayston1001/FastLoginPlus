# 维护与发布 Wiki

Git 是手册可编辑源码, GitBook 负责展示和发布. 不需要旧版 `gitbook-cli`, `book.json` 或 `LANGS.md`.

## 仓库结构

- `docs/gitbook-docs.yaml` 映射中英文内容空间. 路径相对 Git Sync project directory, 必须设置为 **`docs`**.
- `docs/en/` 和 `docs/zh/` 各含 `.gitbook.yaml`, Wiki `README.md`, `SUMMARY.md` 和该语言全部页面.
- `SUMMARY.md` 控制侧栏分组与顺序. 七个栏目是 Markdown 导航分组, 不是七个付费 Site sections.
- 仓库根 README 与 changelog 保留入口/历史职责, 不复制成另一份 Wiki 正文来源.

中英文对应路径保持一致, 尽量使用普通 Markdown. 每个页面在本语言 SUMMARY 只出现一次. 同语言内部使用相对链接, 源码, 根贡献指南和另一语言根目录用公开 GitHub 链接, 因为同步内容空间不能读取兄弟目录. 网站内使用 GitBook 语言切换器.

## 个人账户首次接入

1. 进入 GitBook organization 工作区. 它只是管理工作区, 不要求成立公司. 在 **Sites** 创建 `FastLoginPlus`, 暂不发布.
2. 打开 **Git Sync**, 连接 GitHub, 尽可能只给 GitBook GitHub App 授权目标仓库.
3. 选择 `Hayston1001/FastLoginPlus` 和含这些文档的分支. 文件必须存在于远程, 本地未提交文件不能同步. 不要为了完成本指南就未经仓库主人批准推送.
4. **Project directory** 填 `docs`. 使用现有 manifest: 英文 `./en`, 中文 `./zh`, 语言 `en` 与 `zh`. Manifest 用顶层 spaces 作为语言变体, 不拆成不同产品 sections.
5. 首次同步选择 **GitHub -> GitBook**. 确认仓库, 分支和方向后再开始, 因为所选来源可以替换目标内容.
6. 已有 GitBook 自动生成的在线映射时, 应协调配置而不是直接覆盖标识. 已使用的 space `key` 必须稳定, 修改会创建新空间并可能破坏链接/ID. 提供的 `flp-en`/`flp-zh` 用于新站点.
7. 预览两种语言, 检查桌面/手机导航, 表格, 代码块, 内部链接, 长页面和图表. 正式发版准备完成前保留 1.0.0 尚未发布提示.
8. 审阅通过后选择公开发布并点 **Publish**. 真实发布 URL 在另一次批准的修改中加入仓库入口, 不编造网址.

当前 GitBook Free 支持 Git Sync 与语言变体, 不必先购买高级功能或自定义域名. 套餐/界面可能变化, 应检查当时的[价格页](https://www.gitbook.com/pricing)和[接入指南](https://gitbook.com/docs/docs-as-code/git-sync/enabling-github-sync).

## 日常编辑与审阅

优先在仓库编辑并经 PR 审阅, 让代码与行为文档一起更新. Git Sync 是**双向**的, 网页编辑合并也可能向配置的 Git 分支提交. 不要两处同时改一页, 不要把网页编辑器视为只读预览. 检查机器人提交并遵守分支保护.

合并前检查本地链接/锚点, SUMMARY 覆盖, 双语路径, 实际默认值/生效范围与平台差异. 同步成功不证明插件行为正确. GitBook 预览/发布还需要在账号侧验证, 不能用仓库检查代替.

官方资料: [内容配置](https://gitbook.com/docs/docs-as-code/git-sync/content-configuration), [monorepo](https://gitbook.com/docs/docs-as-code/git-sync/monorepos), [语言变体](https://gitbook.com/docs/manage-your-site/site-structure/variants).
