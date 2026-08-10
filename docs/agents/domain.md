# Domain docs

本仓库采用 single-context。

## 探索前读取

1. 根目录 `CONTEXT.md`；
2. `docs/adr/` 中与当前工作相关的 ADR。

文件不存在时静默继续，不要仅为补齐目录而创建空文档。`CONTEXT.md` 只记录领域词汇；难以反转、缺少上下文会令人意外、且确实经过取舍的决策才写 ADR。

## 消费规则

- 输出中的领域概念必须沿用 `CONTEXT.md` 的 canonical term，不得自行漂移到近义词。
- 若需要的概念尚未定义，应先检查是否引入了错误语言；确有领域缺口时再通过 domain modeling 补充。
- 若方案与现有 ADR 冲突，必须明确指出冲突及重新开启决策的理由，不得静默覆盖。

## 布局

```text
/
├── CONTEXT.md
└── docs/
    └── adr/       # 首个符合条件的 ADR 出现时再创建
```
