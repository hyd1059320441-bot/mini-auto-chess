"""Publish readable hero data and balance evidence from the production CSVs."""
import csv
import hashlib
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
FOLDER = ROOT / "docs/balance-v1.8.0"

def read(name):
    return list(csv.DictReader((FOLDER / name).open()))

def num(value):
    return f"{float(value):g}"

def table(headers, rows):
    return "\n".join(["| " + " | ".join(headers) + " |",
                      "| " + " | ".join(["---"] * len(headers)) + " |"] +
                     ["| " + " | ".join(map(str, row)) + " |" for row in rows]) + "\n"

rows = read("heroes.csv")
heroes = [r for r in rows if r["star"] == "1"]
assert len(heroes) == 16 and len(rows) == 48
powers = {r["id"]: [num(s["base_skill_power"]) for s in rows if s["id"] == r["id"]] for r in heroes}
traits = read("traits.csv")
parts = [
    "# 青玉棋会 V1.8.0 英雄与战斗数据\n",
    "从构建对应的生产Catalog、CombatMath、Battle导出：16名英雄，4阵营、4职业、8种羁绊；1费5名、2费6名、3费5名。没有装备。\n",
    "## 基础属性与技能曲线\n",
    "下表生命/攻击/护甲/魔抗/间隔为1星裸面板；技能列为1/2/3星基础强度。实际上场还应用对应羁绊，职业1人档会立即激活。\n",
    table(["英雄", "阵营", "职业", "费用", "生命", "攻击", "护甲", "魔抗", "间隔/秒", "射程/格", "技能1/2/3星"],
          [[r["name"], r["faction"], r["job"], r["cost"]] +
           [num(r[k]) for k in ("base_health", "base_attack", "base_armor", "base_magic_resist", "base_interval", "range")] +
           ["/".join(powers[r["id"]])] for r in heroes]),
    "生命和普攻攻击按1/1.8/3.24成长；技能使用每名英雄的独立表。费用是购买价，1/2/3星分别消耗1/3/9份：三星1费与二星3费的基础购买投入均为9金，刷新成本另计。\n",
    "## 技能目标与伤害类型\n",
    table(["英雄", "技能", "类型", "目标与效果"],
          [[r["name"], r["skill_name"], {
              "SHIELD": "护盾/法术反击", "VOLLEY": "物理", "BOLT": "法术", "HEAL": "治疗",
              "CLEAVE": "物理", "NOVA": "范围法术"}[r["skill"]],
            {"SHIELD": "自身获得技能强度护盾；1.5格内敌人各受35%技能强度的法术伤害",
             "VOLLEY": "射程内最近的最多2名敌人，各受100%技能强度物理伤害",
             "BOLT": "射程内最近1名敌人受100%技能强度法术伤害",
             "HEAL": "生命比例最低的存活友军恢复技能强度生命，可治疗自己，不限距离",
             "CLEAVE": "自身周围1.8格内敌人各受100%技能强度物理伤害",
             "NOVA": "目标及周围1.6格；主目标100%，其他目标65%技能强度法术伤害"}[r["skill"]]]
           for r in heroes]),
    "护盾反击是施法时的一次伤害，没有持续反伤。护盾累加上限为自身最大生命。治疗不复活、不超过最大生命。护甲和魔抗分别按100/(100+对应防御)减伤，护盾吸收两类伤害；治疗不经过减伤。\n",
    "## 蓝量与施法\n",
    table(["职业", "初始蓝量", "蓝量上限", "普攻回蓝", "受击回蓝/规则步", "每秒回蓝"],
          [[job, "20/30/40（1/3/5法师）" if job == "法师" else "0", "100"] +
           [num(next(r[k] for r in heroes if r["job"] == job)) for k in ("attack_mana", "hit_mana", "mana_per_second")]
           for job in ["卫士", "射手", "法师", "辅助"]]),
    "受击回蓝在一个0.1秒规则步中至少受一次伤害且仍存活时发放一次，多个敌人同一步命中不重复发放。满100蓝独立施法，释放后归零；远程伤害技能需有效射程，横扫需邻近敌人。施法使普攻冷却至少0.35秒。\n",
    "## 阵营与职业成员\n",
]
for key in ["faction", "job"]:
    labels = list(dict.fromkeys(r[key] for r in heroes))
    parts.append(table(["阵营" if key == "faction" else "职业", "人数", "成员"],
                       [[label, sum(r[key] == label for r in heroes),
                         "、".join(r["name"] for r in heroes if r[key] == label)] for label in labels]))
parts += [
    "## 全部羁绊\n",
    table(["羁绊", "不同英雄门槛", "效果"], [[r["label"], r["threshold"], r["effect"]] for r in traits]),
    "只有上场的不同英雄计数；重复英雄和备战席不计。阵营效果只作用于本阵营，职业效果只作用于本职业；辅助提供的全队魔抗作用于全体上场英雄。基础魔抗20与卫士、辅助加成相加。\n",
    "## 时限与胜负\n",
    "0.1秒同时意图结算，25秒后2倍加速，最多再15秒。加速阶段治疗、林地回血、新增护盾降至50%，现存护盾和技能伤害不削减；仍僵持才比较存活人数、剩余生命比例，完全相同为平局。真人淘汰立即终局；三人镜像、一次结算和拖动/卖出规则保留。\n",
    "## 完整实战面板\n",
    "以下CSV保留每英雄每星级的裸面板、技能、单独上场后的实际属性和回蓝：[heroes.csv](balance-v1.8.0/heroes.csv)。英雄单独上场时1人职业羁绊已生效。\n",
]
(ROOT / "docs/hero-data-v1.8.0.md").write_text("\n".join(parts))

names = {"forest_support": "林地辅助", "rune_mage": "符文法师", "guard5": "五卫士",
         "archer_support": "射手辅助", "ember4": "四烬火", "dawn4": "四曙光", "mixed": "混合"}
training = json.loads((FOLDER / "training/summary.json").read_text())
validation = json.loads((FOLDER / "validation/summary.json").read_text())
baseline = json.loads((FOLDER / "iterations/baseline/summary.json").read_text())
progression = json.loads((FOLDER / "progression-summary.json").read_text())
assert all(25 <= c["score_pct"] <= 75 for c in validation["all"].values())
assert validation["timeout_pct"] <= 10
report = [
    "# V1.8.0 平衡迭代与验收\n",
    "16英雄/3费档/7人口；所有对打直接调用APK对应的生产Kotlin Battle，没有平行伤害模拟器。竞品机制与官方版本实例的出处见[V1.7.1审计](../balance-v1.7.1/report.md#参考游戏的设计依据)。本轮采用其星级预算、独立技能曲线和魔法反制思路，没有声称照抄竞品当前数值。\n",
    "## 方法与门槛\n",
    "七类预设阵容，在36/48/60金三个基础购买预算分别对打。合成花费按1/3/9份计算；各阵容允许±1金，固定星级分配，实际构成在armies.csv。训练种子1–4；最后验证站位种子201–224。左右有向配对均执行；含固定前排、分散、随机错列。固定布局重复不是独立玩家样本。\n",
    "门槛：同英雄升星不出现倒挂；同职业同星级高费输出/前排总体占优，辅助同时看团队支援；七类阵容在所有预算合计的验证得分均25%–75%；验证超时判定不超过10%；低费三星与高费二星两条路线都有对局可赢。具体预算和配对允许强势期与克制，不要求逐配对50%。得分=胜1、平0.5、负0。\n",
    "## 最终改动\n",
    "- 生命/攻击保留1/1.8/3.24；五名法师采用独立技能曲线，晨钟1星360；范围技能副目标65%。\n"
    "- 法师技能10/20/35%、初蓝20/30/40；基础魔抗20，卫士增加8/20/36，辅助全队增加10/25。\n"
    "- 射手攻速10/25/40%；25秒后的治疗、回血与新增护盾50%，将僵持转为可结束的对局。\n"
    "- 自动布阵使用实际生命、双防、DPS、技能目标数/施法周期、全队羁绊和前排覆盖；整组搜索保留手动锁定位置，费用不当战力。新增三种经营策略用于对照，游戏默认仍为灵活经营。\n",
    "## 等投资阵容结果\n",
    table(["阵容", "旧训练得分", "新训练得分", "最终验证得分", "验证36/48/60金"],
          [[names[key], f'{baseline["all"][key]["score_pct"]}%', f'{training["all"][key]["score_pct"]}%',
            f'{data["score_pct"]}%', "/".join(f'{validation[b][key]["score_pct"]}%' for b in ("36", "48", "60"))]
           for key, data in validation["all"].items()]),
    f'最终验证{validation["games"]}场，超时{validation["timeout_count"]}场（{validation["timeout_pct"]}%）。各阵容均满足总体得分门槛。\n',
    "## 升星与费用\n",
    table(["对照", "场数", "高星胜", "高星负", "平"],
          [[key, d["games"], d["wins"], d["losses"], d["draws"]] for key, d in progression["upgrades"].items()]),
    table(["同职业同星级", "职业", "高费胜", "高费负", "平"],
          [[key, job, d["wins"], d["losses"], d["draws"]] for key, jobs in progression["costs"].items() for job, d in jobs.items()]),
    f'三星1费与二星3费的9金单挑共100场：三星1费{progression["equal_9_gold_solo"]["wins"]}胜、'
    f'{progression["equal_9_gold_solo"]["losses"]}负、{progression["equal_9_gold_solo"]["draws"]}平；'
    f'{progression["equal_9_gold_solo"]["timeouts"]}场按超时判定。裸单挑偏重生存/自疗，不能取代阵容结论。\n',
    "## 迭代原始记录\n",
    "A只替换星符/熔核技能曲线；B再降低法师加成与起蓝；C加入基础/卫士/辅助魔抗；D改其余三名法师技能曲线与晨钟基础伤害；E副目标65%；F加速期间治疗/回血50%；G新增护盾也降至50%；H射手攻速调整为10/25/40%。每轮在同一批固定投资/星级/站位上重跑。最终源码和类哈希单独记录，历史轮次仅保留其逐场输出。\n",
]
for folder in sorted((FOLDER / "iterations").iterdir()):
    if (folder / "summary.json").is_file():
        s = json.loads((folder / "summary.json").read_text())
        report.append(f'- [{folder.name}](iterations/{folder.name}/summary.json)：{s["games"]}场，法师{s["all"]["rune_mage"]["score_pct"]}%，射手{s["all"]["archer_support"]["score_pct"]}%，超时{s["timeout_pct"]}%。')
report += [
    "\n## 证据边界\n",
    "结果针对预设阵容、星级分配、预算及列出的站位；不覆盖所有英雄重复组合、所有运营决策或真实玩家偏好。强度会随预算、主坦星级和站位变化，具体差异完整保留，不能宣称所有局面完美平衡。150局多策略与100局常规模拟检验经济/卡池/结束流程，不代替阵容对打证据。\n",
    "[英雄全部数据](../hero-data-v1.8.0.md) · [验证逐场结果](validation/lineups.csv) · [验证阵容](validation/armies.csv) · [升星/费用](progression-summary.json) · [完整单挑](duels.csv) · [三人替换](trios.csv)\n",
]
(FOLDER / "report.md").write_text("\n".join(report))
files = list((ROOT / "app/src/main/java/com/pico/miniautochess/rules").glob("*.kt")) + list((ROOT / "scripts").glob("*Audit.java"))
files += [ROOT / "scripts/BalanceTournament.java", ROOT / "scripts/summarize_balance.py", ROOT / "scripts/summarize_progression.py"]
hashes = {str(p.relative_to(ROOT)): hashlib.sha256(p.read_bytes()).hexdigest() for p in files}
classes = ROOT / "app/build/tmp/kotlin-classes/debug/com/pico/miniautochess/rules"
hashes.update({str(p.relative_to(ROOT)): hashlib.sha256(p.read_bytes()).hexdigest() for p in classes.glob("*.class")})
(FOLDER / "evidence.json").write_text(json.dumps({"source_and_classes_sha256": hashes,
    "training_games": training["games"], "validation_games": validation["games"],
    "progression_games": progression["games"], "thresholds_passed": True}, indent=2) + "\n")
print("Exported hero-data-v1.8.0.md and balance-v1.8.0/report.md")
