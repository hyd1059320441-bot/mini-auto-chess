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
- 当前本地试玩V1.5.0/code7（未发布）：16英雄、有限共享卡池与回合/人口概率表、满蓝独立施法、2/4和1/3/5等可达羁绊、六人口/合成补位、双向交换、金色选中与姓名/血蓝条、灯笼承台、等宽终局列。公开SDK仍为6.1.9，最新证据在 `docs/verification/DELIVERY_V1_5.md`，产物在 `dist/v1.5`。旧6.2.8无障碍合同/证据不适用；6.1.9不能声明Spatial Accessibility，保留原生Compose语义并记录审计缺口。
- 当前增量试玩V1.6.0/code8：16英雄六类程序动作/独立技能声音与图案、hp100/mp5刻度与当前/上限、无常驻格线玉石台面、亭子正面横梁/屋顶和原创可开关音乐。五个GLB没有骨骼，只有整体姿态；十一程序英雄摆动四肢。不得声称完整新模型已生成或骨骼动画已完成。说明 `docs/UX_V1_6.md`、交付 `docs/verification/DELIVERY_V1_6.md`、产物 `dist/v1.6`。
- 当前摆放微调V1.6.1/code9：scenePlacement整体沿-Z前移.10m，placementOwner保留局部identity；HUD遮挡距离同步使用观察者在owner内的坐标。交付 `docs/verification/DELIVERY_V1_6_1.md`、产物 `dist/v1.6.1`。
- 当前增量V1.6.2/code10：全场景再沿-Z前移.10m（累计-.20m），所有英雄模型缩小15%，状态文字保持尺寸；概率只由人口决定，移除round参数与旧回合表。6人口30/40/30，7人口16/30/54（1/2/3费），云顶4/5费权重并入3费。约定 `docs/UX_V1_6_2.md`、交付 `docs/verification/DELIVERY_V1_6_2.md`、产物 `dist/v1.6.2`。
- 当前增量V1.7.0/code11：全场景累计-.30m；模型保留85%，固定脚部命中盒+占用槽位拖动；状态条读取动画body边界。拖到商店中央实测区域松手卖出；16张画像和1/2/3费白绿蓝框。取消第十轮终局，三人镜像继续至最后一人；25秒后2倍加速，最多再15秒。约定 `docs/UX_V1_7.md`、交付 `docs/verification/DELIVERY_V1_7.md`、产物 `dist/v1.7`。
- 当前增量V1.7.1/code12：真人死亡当次结算即终局，已有参战结果支付一次，中断战斗冻结；剩余AI显示未完赛。所有放大文字指定行高，卡池26/33sp。拖动以drag.uid保持金色模型、脉动外框与“拖动中”标签。约定 `docs/UX_V1_7_1.md`，交付 `docs/verification/DELIVERY_V1_7_1.md`，产物 `dist/v1.7.1`。
- 当前数值V1.8.0/code13：独立法师技能曲线、范围副目标65%、基础魔抗20、卫士双防与辅助全队魔抗；法师10/20/35%与起蓝20/30/40，射手10/25/40%。加速期间治疗/回血/新增护盾50%。ArmyEvaluator按实际整队战力有界搜索，补位保留手动站位；三类经营策略测试。约定 `docs/UX_V1_8.md`，数值与原始对打 `docs/balance-v1.8.0`，交付 `docs/verification/DELIVERY_V1_8.md`，产物 `dist/v1.8.0`，SDK仍6.1.9。
- 当前经营V1.9.0/code14：交战/结算可经营和拖动备战席，上场副本锁定，涉及棋盘的合成/升级补位延至下轮备战。MatchResultDialog原生居中胜利/失败，按钮“再来一局”“退出”（navigator.closeStage）。GameEngine每局新种子、商店顺序轮换、AI延迟四步经营与风格每局随机。约定 `docs/UX_V1_9.md`，400局同策略席位前后对照 `docs/fairness-v1.9.0`，交付 `docs/verification/DELIVERY_V1_9.md`，产物 `dist/v1.9.0`。保留V1.8数值和SDK6.1.9，不把同策略冠军率当作真人实战胜率。
- 当前视效/经济V1.10.0/code15：signed streak记录真实胜负，2–3场+1金/4场+2/5场以上+3，平局清零，奖励计息后支付一次。CombatVisuals定义墙钟时序；技能1.2–1.8秒且前60%保持亮度，弹道独立32槽，特效40槽/240火花。Battle事件记录坐标/time，ChessScene只播放新事件；射手箭矢、远程法师/辅助能量球飞行0.30–0.55秒，伤害时机不变。约定 `docs/UX_V1_10.md`，交付 `docs/verification/DELIVERY_V1_10.md`，产物 `dist/v1.10.0`，本轮经济席位审计 `docs/fairness-v1.10.0`；SDK仍6.1.9。
- 12 张参考图、5 个正式模型；7 名英雄暂为开发造型。额度状态见 `docs/models/STATUS.md`，禁止重提已成功的五个 SGS 任务，未授权充值。用户已授权发布 V1.4 到 Vibe Lab。
- 用户自行验证真机视觉和手柄/眼手，不要求截图。安装后的菜单可直接试玩。不要重复触发 PICO 登录；授权问题已提给金燕，见 `PICO_AUTH_LOGIN_FINDING.md`。
