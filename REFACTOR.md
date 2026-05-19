# 重构说明（相对 `mutilmodel-old`）

## 目标

- **不修改** `mutilmodel-old`；本目录为重构副本。
- 更少代码、更清晰分层；**技能 Markdown 大幅精简**，确定性规则下沉 Java。

## 主要变化

| 领域 | 原实现 | 重构后 |
|------|--------|--------|
| 技能加载 | `FormVisionFillSkillSupport` + `UploadGuideDialogSkillSupport` | 统一 `SkillLoader` |
| 意图路由 | `DemoChatService` 内嵌正则 + 长 sysPrompt | `ChatIntents` + `AgentPrompts` |
| 材料卡 | 技能长文约束 + 模型输出 + 部分服务端覆盖 | **`UploadGuideFactory` 全权构造**（四证示意 / 仍缺项） |
| `upload_guide_dialog.md` | ~145 行 | **~15 行**（仅 reply 话术） |
| `form_vision_fill.md` | ~172 行 | **~37 行**（键表 + 歧义 + reply 骨架） |
| 仍缺卡 | `UploadGuideFromCoverage` | 合并入 `UploadGuideFactory` |

## 行为对齐

- 文本命中上传/缺件意图：`upload_guide` **一律服务端生成**，模型专注 `reply`（及可选 `form_patch`）。
- 视觉 SSE：`upload_guide` 仍为 `null`；`FormVisionPatchNormalizer` / `FormVisionMultiEntityConflictDetector` 未改语义。
- 前端可继续 `normalizeVisionUploadGuide` 作兜底；主路径已由后端保证四证卡结构。

## 运行

与旧项目相同：`mvn spring-boot:run`（或 `-Pfrontend`）。配置见 `application.yml` / `application-local.yml`。
