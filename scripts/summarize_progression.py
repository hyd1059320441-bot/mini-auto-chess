import collections
import csv
import json
import sys
from pathlib import Path

folder = Path(sys.argv[1])
duels = list(csv.DictReader((folder / "duels.csv").open()))
trios = list(csv.DictReader((folder / "trios.csv").open()))
heroes = {r["id"]: r for r in csv.DictReader((folder / "heroes.csv").open()) if r["star"] == "1"}
assert len(duels) == 9216 and len(trios) == 1536

def summarize(rows):
    counts = collections.Counter(r["outcome"] for r in rows)
    return dict(games=len(rows), wins=counts["LEFT"], losses=counts["RIGHT"], draws=counts["DRAW"],
                timeouts=sum(float(r["wall_seconds"]) >= 39.99 for r in rows))

result = {"games": len(duels) + len(trios), "upgrades": {}, "costs": {}, "trio_costs": {}}
for a, b in [(2, 1), (3, 2)]:
    selected = [r for r in duels if r["left_id"] == r["right_id"] and r["left_star"] == str(a) and r["right_star"] == str(b)]
    result["upgrades"][f"{a}vs{b}"] = summarize(selected)
    assert result["upgrades"][f"{a}vs{b}"]["losses"] == 0, "Star inversion"
for a, b in [(2, 1), (3, 2), (3, 1)]:
    same_cost = lambda r: int(heroes[r["left_id"]]["cost"]) == a and int(heroes[r["right_id"]]["cost"]) == b
    rows = [r for r in duels if same_cost(r) and r["left_star"] == r["right_star"] and
            heroes[r["left_id"]]["job"] == heroes[r["right_id"]]["job"]]
    result["costs"][f"{a}vs{b}"] = {job: summarize([r for r in rows if heroes[r["left_id"]]["job"] == job])
                                          for job in ["卫士", "射手", "法师", "辅助"]}
    result["trio_costs"][f"{a}vs{b}"] = summarize([r for r in trios if same_cost(r)])
result["equal_9_gold_solo"] = summarize([r for r in duels if int(heroes[r["left_id"]]["cost"]) == 1 and
    r["left_star"] == "3" and int(heroes[r["right_id"]]["cost"]) == 3 and r["right_star"] == "2"])
(folder / "progression-summary.json").write_text(json.dumps(result, indent=2, ensure_ascii=False) + "\n")
print(json.dumps(result, indent=2, ensure_ascii=False))
