# Maintain and publish the Wiki

Git is the editable source of this manual. GitBook renders and publishes it. No obsolete `gitbook-cli`, `book.json` or `LANGS.md` setup is required.

## Repository layout

- `docs/gitbook-docs.yaml` maps English and Chinese content spaces. Its paths are relative to the Git Sync project directory, which must be **`docs`**.
- `docs/en/` and `docs/zh/` each contain `.gitbook.yaml`, the Wiki `README.md`, `SUMMARY.md` and all pages for that language.
- `SUMMARY.md` defines sidebar groups and order. These seven groups are Markdown navigation groups, not seven paid Site sections.
- Root repository READMEs and changelogs remain repository entry/history documents, not duplicated Wiki page sources.

Keep corresponding language paths aligned. Use ordinary Markdown where possible. Each page belongs once in its language's SUMMARY. Links within one language use relative paths; source code, root contribution guides and other language roots use public GitHub URLs, because a synced content space cannot read sibling directories. On the site use GitBook's language selector.

## First-time setup for a personal account

1. Open the GitBook organization workspace. It is an administrative workspace, not a requirement to own a company. Under **Sites**, create `FastLoginPlus` and keep it unpublished.
2. Open **Git Sync**, connect GitHub and grant the GitBook GitHub App access only to the intended repository where possible.
3. Select `Hayston1001/FastLoginPlus` and the branch containing these documents. Changes must exist remotely before GitBook can read them; a local uncommitted file cannot sync. Do not push merely to complete this guide without repository-owner approval.
4. Set **Project directory** to `docs`. Use the existing manifest: English maps to `./en`, Chinese to `./zh`, with languages `en` and `zh`. The manifest uses top-level spaces as language variants, not separate product sections.
5. Select **GitHub -> GitBook** for the initial synchronization. Confirm repository, branch and direction before starting, since the chosen source can replace destination content.
6. If GitBook already generated its own live mapping, reconcile it rather than blindly replacing identifiers. Keep a live space's `key` permanent: changing it creates another space and can break links/IDs. The supplied `flp-en`/`flp-zh` keys are intended for a new site.
7. Preview both languages. Check navigation, tables, code blocks, internal links, long pages and any diagrams on desktop/mobile. Keep the 1.0.0 unreleased notice until the actual release is ready.
8. Only after review, choose public publishing and **Publish**. Copy the real published URL to repository entry points in a separate approved change; do not invent a URL.

Current GitBook Free supports Git Sync and language variants; advanced paid features and custom domains are not prerequisites. Plans/UI can change, so check the current [pricing](https://www.gitbook.com/pricing) and [setup guide](https://gitbook.com/docs/docs-as-code/git-sync/enabling-github-sync).

## Daily editing and review

Prefer repository edits and reviewed PRs so code and behavior docs change together. Git Sync is **bidirectional**: merging web-editor changes can commit to the configured Git branch. Do not edit the same page in both places concurrently or assume the web editor is a read-only preview. Review any bot commits and your normal branch protections.

Before merging, check local links/anchors, SUMMARY coverage, matching language paths, actual configuration defaults/scopes and platform differences. Do not treat a successful sync as validation of plugin behavior. GitBook preview/publishing requires account-side verification in addition to repository checks.

Official references: [content configuration](https://gitbook.com/docs/docs-as-code/git-sync/content-configuration), [monorepos](https://gitbook.com/docs/docs-as-code/git-sync/monorepos) and [variants](https://gitbook.com/docs/manage-your-site/site-structure/variants).
