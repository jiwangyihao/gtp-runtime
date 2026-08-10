# Issue tracker：GitHub

本仓库的 Issue 与 PRD 使用 GitHub Issues，并通过 `gh` CLI 读写。

## 约定

- 创建：`gh issue create --title "..." --body "..."`
- 读取：`gh issue view <number> --comments`
- 列表：`gh issue list --state open --json number,title,body,labels,comments`
- 评论：`gh issue comment <number> --body "..."`
- 标签：`gh issue edit <number> --add-label "..."` / `--remove-label "..."`
- 关闭：`gh issue close <number> --comment "..."`

在仓库 clone 内执行时，由 `git remote` 推断目标仓库。

## Pull requests 作为 triage 入口

**否。** 外部 PR 按普通代码评审流程处理，不进入 Issues 的统一 triage 状态机。

技能要求“发布到 issue tracker”时创建 GitHub Issue；要求“读取相关 ticket”时使用 `gh issue view <number> --comments`。
