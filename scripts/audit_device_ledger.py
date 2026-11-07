#!/usr/bin/env python3
"""Independently verify public round logs against the accepted economy/health rules."""
import json
from pathlib import Path
import re
import sys


def audit(path):
    health = {i: 32 for i in range(4)}
    immediate = {}
    payments = {}
    bases = [1, 1, 2, 2, 3, 3, 4, 4, 5, 5]
    for line in path.read_text().splitlines():
        if "JadeChessResults:" not in line:
            continue
        match = re.search(r"round=(\d+) phase=(\w+)", line)
        if not match:
            continue
        round_number, phase = int(match[1]), match[2]
        for body in re.findall(r"RoundResult\(([^)]+)\)", line):
            record = dict(pair.split("=", 1) for pair in body.split(", "))
            player = int(record["playerId"])
            key = (round_number, player)
            lost, won = record["result"] == "落败", record["result"] == "胜利"
            base, population = int(record["baseDamage"]), int(record["survivorPopulation"])
            damage, win = int(record["damage"]), int(record["winGold"])
            assert 0 <= population <= 7, record
            assert base == (bases[round_number - 1] if lost else 0), record
            assert win == (1 if won else 0), record
            if key not in immediate:
                assert health[player] > 0, ("Previously eliminated seat participated", key)
                expected = min(health[player], base + population) if lost else 0
                assert damage == expected, (key, damage, expected)
                health[player] -= damage
                immediate[key] = record
            if record["roundPaid"] == "true":
                balance, interest = int(record["interestBalance"]), int(record["interest"])
                assert interest == balance // 10, record
                assert int(record["income"]) == 5 + win + interest, record
                assert int(record["xpGain"]) in (0, 2), record
                if key in payments:
                    assert payments[key] == record, ("Payment ledger changed", key)
                payments[key] = record
                if player == 0 and phase in ("TRANSITION", "FINISHED"):
                    actual = int(re.search(r"humanGold=(\d+)", line)[1])
                    assert actual == balance + 5 + interest, (actual, record)
    assert immediate and payments, "No non-empty real round evidence"
    assert payments.keys() == immediate.keys(), "A participant did not receive final round income"
    return {
        "passed": True, "log": path.name,
        "rounds": max(r for r, _ in payments), "real_round_payments": len(payments),
        "loss_payments": sum(r["result"] == "落败" for r in payments.values()),
        "positive_interest_payments": sum(int(r["interest"]) > 0 for r in payments.values()),
        "final_health_from_damage": health,
        "checked": ["round_base_plus_survivors", "lethal_clamp", "win_coin",
                    "five_base_on_every_outcome", "interest_after_win_before_base",
                    "human_gold_matches_payment", "no_payment_for_prior_eliminations"],
        "payments": [dict(round=key[0], **record) for key, record in sorted(payments.items())],
    }


if __name__ == "__main__":
    result = audit(Path(sys.argv[1]))
    Path(sys.argv[2]).write_text(json.dumps(result, ensure_ascii=False, indent=2) + "\n")
    print(json.dumps({k: v for k, v in result.items() if k != "payments"}, ensure_ascii=False, indent=2))
