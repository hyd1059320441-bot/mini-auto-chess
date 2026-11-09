"""Summarize the production battle CSV, retaining every fixture and draw."""
import collections
import csv
import json
import sys
from pathlib import Path

folder = Path(sys.argv[1])
rows = list(csv.DictReader((folder / "lineups.csv").open()))
summary = {}
for budget in ["all", "36", "48", "60"]:
    selected = rows if budget == "all" else [r for r in rows if r["budget"] == budget]
    counts = collections.defaultdict(collections.Counter)
    for r in selected:
        # Each ordered row has a matching reversed fixture. Aggregate left seats
        # only to avoid double-counting both teams when comparing fixture counts.
        counts[r["left_comp"]][r["outcome"]] += 1
    summary[budget] = {
        key: dict(games=sum(c.values()), wins=c["LEFT"], losses=c["RIGHT"], draws=c["DRAW"],
                  score_pct=round(100 * (c["LEFT"] + .5*c["DRAW"]) / sum(c.values()), 2))
        for key, c in counts.items()
    }
summary["games"] = len(rows)
summary["timeout_count"] = sum(float(r["wall_seconds"]) >= 39.99 for r in rows)
summary["timeout_pct"] = round(100 * summary["timeout_count"] / len(rows), 2)
(folder / "summary.json").write_text(json.dumps(summary, indent=2) + "\n")
for key, data in summary["all"].items():
    budgets = "/".join(str(summary[b][key]["score_pct"]) for b in ("36", "48", "60"))
    print(f'{key}: {data["score_pct"]}% ({data["wins"]}W {data["losses"]}L {data["draws"]}D); budgets={budgets}')
print(f'games={summary["games"]}; timeout={summary["timeout_pct"]}%')
