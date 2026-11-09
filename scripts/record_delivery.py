"""Record verified V1.10.0 build/device evidence and generate its delivery note."""
import collections
import hashlib
import json
import re
import shutil
import xml.etree.ElementTree as ET
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / "docs/verification"
PREFIX = "v1.10.0"
assert OUT.is_dir()

def record(suffix, value):
    (OUT / f"{PREFIX}-{suffix}").write_text(json.dumps(value, indent=2, ensure_ascii=False) + "\n")

for suffix in ("build.log", "install.log", "launch.log", "app-info.json", "errors.log",
               "android-runtime.log", "placement.json", "startup.log", "crash.log",
               "a11y-project.json", "a11y-runtime.json", "effects-placement.json"):
    shutil.copy2(Path("/tmp") / f"jade-chess-{PREFIX}-{suffix}", OUT / f"{PREFIX}-{suffix}")

suites = [ET.parse(p).getroot() for p in (ROOT / "app/build/test-results/testDebugUnitTest").glob("TEST-*.xml")]
totals = {k: sum(int(s.attrib.get(k, 0)) for s in suites) for k in ("tests", "failures", "errors", "skipped")}
assert totals["tests"] >= 121 and all(totals[k] == 0 for k in ("failures", "errors", "skipped")), totals
suite_counts = {s.attrib["name"].rsplit(".", 1)[-1]: int(s.attrib["tests"]) for s in suites}
assert suite_counts.get("BattleEconomyTest", 0) >= 7 and suite_counts.get("FairPreparationTest", 0) >= 3
assert suite_counts.get("StreakEconomyTest", 0) >= 6 and suite_counts.get("CombatVisualsTest", 0) >= 3
assert "BUILD SUCCESSFUL" in (OUT / f"{PREFIX}-build.log").read_text()
record("tests.json", {"version": "V1.10.0", **totals, "passed": True,
    "command": "./gradlew :app:assembleDebug :app:testDebugUnitTest --console=plain",
    "simulation_matches": 100, "strategy_simulation_matches": 150,
    "suites": [{k: s.attrib[k] for k in ("name", "tests", "failures", "errors", "skipped", "time")} for s in suites]})
apk = ROOT / "app/build/outputs/apk/debug/app-debug.apk"
installed = Path(f"/tmp/jade-chess-{PREFIX}-installed.apk")
sha = hashlib.sha256(apk.read_bytes()).hexdigest()
assert sha == hashlib.sha256(installed.read_bytes()).hexdigest()
info = json.loads((OUT / f"{PREFIX}-app-info.json").read_text())
assert info["running"] and info["versionName"] == "V1.10.0" and str(info["versionCode"]) == "15"
record("installed-apk-check.json", {"device": info["deviceId"], "package": info["packageName"],
    "version_name": "V1.10.0", "version_code": 15, "bytes": apk.stat().st_size, "sha256": sha,
    "installed_sha256": sha, "installed_matches": True,
    "method": "pico-cli files pull base.apk; compare SHA-256"})
log = (OUT / f"{PREFIX}-startup.log").read_text()
gate = json.loads((OUT / f"{PREFIX}-placement.json").read_text())
assert gate["passed"]
assert "SCENE_READY generated=5 templates=16" in log
assert "sceneForwardOffsetZ=-0.3" in log and "heroVisualScale=0.85" in log
assert "boardSurfaceHeights=[0.265, 0.265, 0.265, 0.265] uniform=PASS" in log
assert "occupiedSlots=40" in log and "separated=PASS" in log and "numeric=FAIL" not in log
assert "FX_READY effectPool=40 sparkMeshCap=240 projectilePool=32 skillSeconds=1.2..1.8 wallTime=true realtimeLights=0" in log
assert json.loads((OUT / f"{PREFIX}-effects-placement.json").read_text())["passed"]
assert not (OUT / f"{PREFIX}-android-runtime.log").read_text().strip()
assert not (OUT / f"{PREFIX}-crash.log").read_text().strip()
assert "FATAL EXCEPTION" not in log and "Fatal signal" not in log
errors = (OUT / f"{PREFIX}-errors.log").read_text()
a11y = json.loads((OUT / f"{PREFIX}-a11y-project.json").read_text())
fairness = json.loads((ROOT / "docs/fairness-v1.10.0/summary.json").read_text())
assert fairness["before"]["games"] == fairness["after"]["games"] == 400
record("runtime.json", {"version": "V1.10.0", "version_code": 15, "sdk": "6.1.9",
    "device": info["deviceId"], "running": True, "pid": info["pid"], "apk_sha256": sha,
    "installed_matches": True, "build": "PASS", "tests_passed": totals["tests"],
    "source_gate": "PASS", "experimental_api_opt_in_gate": "PASS",
    "source_gate_advisories": gate.get("advisories", []),
    "scene_ready": True, "scene_offset_meters": [0, 0, -.3], "hero_visual_scale": .85,
    "board_height_y_meters": .265, "shop_separated": True, "occupied_drag_targets": 40,
    "fatal_exception_observed": False, "fatal_signal_observed": False,
    "platform_error_tags_preserved": dict(collections.Counter(re.findall(r" E ([^:]+):", errors))),
    "balance_inherited_from": "V1.8.0", "balance_tournament_rerun": False,
    "same_style_seat_fairness": fairness,
    "streak_bonus": {"2_3": 1, "4": 2, "5_plus": 3, "draw": "reset",
                     "same_rules_all_seats": True, "paid_after_interest_snapshot": True},
    "effects": {"effect_pool": 40, "spark_mesh_cap": 240, "projectile_pool": 32,
                "skill_wall_seconds": [1.2, 1.8], "full_brightness_fraction": .6,
                "projectile_seconds": [.3, .55], "damage_timing_unchanged": True,
                "real_time_lights": 0},
    "simulation_matches": 100, "strategy_simulation_matches": 150,
    "a11y_source_passed": a11y["data"]["passed"],
    "a11y_disposition": "SDK6.1.9 lacks Spatial AccessibilityComponent; two existing Attachment owners unresolved; native Compose semantics retained",
    "visual": "UNOBSERVED", "interaction_verification": "user_headset",
    "runtime_scope": "installation_liveness_menu_scene_numeric_geometry", "charged_model_tasks": 0})

note = f"""# 青玉棋会 V1.10.0 交付记录

V1.10.0 / code15，公开Spatial SDK6.1.9，com.pico.miniautochess；已安装并启动于B3110（{info["deviceId"]}）。

## 修改

- 连胜/连败2–3场+1金、4场+2金、5场以上+3金，平局清零，胜负切换重新从1场计数；真人与AI相同。
- 奖励随基础收入结算一次，计息余额先快照，连胜败奖励不参与自身当轮利息。镜像不改变被复制棋手的连胜败；致死落败仍结算已有真实结果。
- HUD显示当前连胜败和奖励，回合账本显示基础、胜利、连胜败、利息与经验。
- 技能主图案/护盾/治疗/范围波持续1.2–1.8秒，前60%保持亮度后淡出。按前台实际秒数播放，2倍加速不再缩短可读时间；护盾/治疗图案跟随目标。
- 远程普攻有飞行箭矢或能量球与拖尾，飞行0.30–0.55秒；命中位置保留反馈。近战保留挥砍。捕获战斗事件坐标，目标移动或死亡不抹掉弹道。
- 视觉弹道不改原伤害结算时机、攻击间隔或英雄数值。切换观战/回合/重开清理特效，暂停冻结当前效果。
- 特效40槽、每槽最多6火花（240），弹道独立32槽；技能主图案优先于普通命中。共享网格/贴图、无实时灯，避免普攻挤掉技能图案。
- 保留V1.9战斗备战席经营、上场锁定、延迟合成、终局弹窗、AI行动时机和随机机制。

## 验证

完整构建成功，{totals["tests"]}项测试0失败/错误/跳过。新增6项连胜败账本和3项特效时序/弹道坐标回归；保留7项战斗经营与3项公平时机回归。实验API、场景与特效摆放源码门禁通过，扫描提示及原始结果保留。

加入连胜败后的同策略席位审计400局，四席FLEX、种子1001–1400，审计模式允许真人死亡后继续决出冠军。V1.9参考冠军数为{fairness["before"]["champions"]}，本轮为{fairness["after"]["champions"]}。每步检查卡池、金币、人口与真实配对守恒；结论只限同策略席位，不等于真人胜率或所有策略相同强度。

100局当前默认AI与150局三策略模拟通过卡池/金币/人口/配对、真人死亡立即终局与终局冻结检查。英雄战斗数值未变，本轮未重跑V1.8数值锦标赛；新增收入会影响经营路径，因此已重新运行完整经济模拟与400局席位审计。

安装回拉base.apk与构建SHA-256一致。场景16模板/5正式模型及FX_READY对象池初始化通过；累计前移.30m、模型.85、桌面高度.265m，商店/备战席分离与40拖动槽位检查通过。启动窗口AndroidRuntime/crash缓冲无记录；平台错误标签原始日志保留。

SDK6.1.9不支持Spatial AccessibilityComponent，两个既有Attachment仍未解析；原生Compose语义保留，空间无障碍未通过。没有收费3D资源。遵循用户自行佩戴验证的偏好，本轮未截图/录像或自动玩局；技能实际可见性、观战切换、暂停恢复与满人口时的真机帧率由用户确认。启动日志只证明资源池初始化，不证明战斗效果已在真机观察。

## 佩戴确认

1. 连胜或连败第二场起出现+1金，第四场+2、第五场+3；平局清零，切换胜负重计，结算明细与金币一致。
2. 观察射手弓箭与法师/辅助能量球从攻击者飞向目标，近战仍为挥砍；目标死亡后弹道也能到达原命中位置。
3. 观察16种技能主图案、范围波、护盾和治疗上升光；应能看清，2倍加速时仍保持显示时间。
4. 暂停冻结弹道/特效，继续后恢复；切换观战不补播历史攻击，重开清理旧效果。

## 文件

- dist/v1.10.0/jade-chess-v1.10.0-preview.apk
- dist/v1.10.0/jade-chess-v1.10.0-source.zip
- [连胜败与特效约定](../UX_V1_10.md)
- [400局席位审计](../fairness-v1.10.0/report.md)
- [100局](simulation-v1.10.0-100.md) / [150局多策略](strategy-v1.10.0-150.md)
- [继承英雄数值](../hero-data-v1.8.0.md) / [继承数值平衡证据](../balance-v1.8.0/report.md)
- v1.10.0-tests.json / runtime.json / installed-apk-check.json

APK {apk.stat().st_size}字节；SHA-256：`{sha}`
"""
(OUT / "DELIVERY_V1_10.md").write_text(note)
print(json.dumps({"tests": totals, "installed": True, "sha256": sha,
                  "fairness_games": fairness["after"]["games"]}, indent=2))
