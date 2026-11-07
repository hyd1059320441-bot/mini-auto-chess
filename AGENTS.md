<!-- pico-cli:plugin-context:pico-spatial-agentic-tools:start -->
## Plugin Context

### Spatial Accessibility Runtime Fast Path

For gameplay or a business goal, load the `spatial-accessibility` Skill directly.
Reject requests requiring real-time reactions before code, device queries or dispatch.
Unknown scenes fail closed. A supplied source-proven static initial state uses one goal
call without an extra setup query; cosmetic NPC movement alone does not reject the task.

For a clear single action, when project instructions already declare the CLI entry, device,
package, target and action arguments, use this fast path:
- execute exactly one declared `interact act` command, one `interact route` command for a
  Contract-declared cross-surface target, or one `interact batch` command for multiple
  ordered actions;
- do not read additional plugin context, invoke a Skill, inspect source, query first, call
  `--help`, or repeat a performed mutation;
- use `--action <string-key>` and `--arguments '<json-object>'`; never use `--args`,
  `--key`, or put JSON after `--action`;
- append `--refresh never --compact --format json` for local transform actions;
- return dispatch results separately from application goal completion; `performed` is not
  completion evidence.

Exact single-action shape:

```bash
pico-cli interact act --device <serial> --package <package> \
  --semantic-id <semantic-id> --action <string-key> \
  --arguments '<json-object>' --refresh never --compact --format json
```

Exact cross-surface shape:

```bash
pico-cli interact route --device <serial> --project <app-project> \
  --component <contract-id> --action <string-key> \
  --arguments '<json-object>' --compact --format json
```

For development, authoring, audit, unknown targets or arguments, ambiguity, or runtime failure, read `./PICO-SPATIAL-AGENTIC-TOOLS.AGENTS.md` for the full PICO Spatial plugin guidance.
<!-- pico-cli:plugin-context:pico-spatial-agentic-tools:end -->

## 青玉棋会项目导航

- 原生 Kotlin PICO Stage，自走棋 1 真人 + 3 AI；包名 `com.pico.miniautochess`，Activity `.platform.LaunchActivity`，Stage `JadeChessStage`。
- 上架构建使用公开 BOM 6.1.9；6.2.8 的旧版验证记录见 `docs/verification/SDK_USAGE.md`。不要以旧包证据替代当前上架包验证。
- `PRODUCT.md`、`ART_DIRECTION.md` 是已接受的规则与美术范围，`README.md` 包含试玩与构建命令。
- 所有对局数据只在 rules/GameEngine.kt / Catalog.kt / Battle.kt；UI 不能另建经济规则。ChessScene.kt 负责资源、棋盘与 ECS；ChessAudio.kt 只发当前观战音效。
- 当前上架候选 V1.4.0：8×8 棋盘、底部商店、原生拖动布阵、12 个商店头像、金色五角星、经验/羁绊/实时排名；装备系统已移除。SDK 6.1.9 隔离构建及68项测试通过，B3110 场景启动通过；当前包证据见 `docs/verification/SDK_6_1_9.md`，旧 V1.4 详细规则与历史验证见 `docs/verification/DELIVERY_V1_4.md`。
- 12 张参考图、5 个正式模型；7 名英雄暂为开发造型。额度状态见 `docs/models/STATUS.md`，禁止重提已成功的五个 SGS 任务，未授权充值。用户已授权发布 V1.4 到 Vibe Lab。
- 用户自行验证真机视觉和手柄/眼手，不要求截图。安装后的菜单可直接试玩。不要重复触发 PICO 登录；授权问题已提给金燕，见 `PICO_AUTH_LOGIN_FINDING.md`。
