#!/usr/bin/env python3
"""Exercise a real match through published buttons; no rule or clock overrides."""
import json
from pathlib import Path
import re
import selectors
import subprocess
import sys
import time

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / "docs/verification"
DEVICE = "PB331XPGL6040019G"
PACKAGE = "com.pico.miniautochess"
BASE = ["pico-cli", "interact"]
VERSION = "1.4"
sequence = 0


def call(args, name):
    result = subprocess.run(args, cwd=ROOT, capture_output=True, text=True, timeout=30)
    (OUT / f"v{VERSION}-{name}.json").write_text(result.stdout)
    if result.returncode:
        raise RuntimeError(f"{name}: {result.stdout} {result.stderr}")
    return json.loads(result.stdout)


def act(text):
    global sequence
    sequence += 1
    return call(BASE + ["act", "--package", PACKAGE, "--device", DEVICE, "--text", text,
        "--action", "click", "--arguments", "{}", "--refresh", "auto", "--compact",
        "--role", "Button", "--format", "json"], f"action-{sequence:02d}")


def capture(name):
    return call(BASE + ["query", "--package", PACKAGE, "--device", DEVICE, "--limit", "100",
        "--compact", "--format", "json"], name)


def main():
    global sequence, VERSION
    loss_case = "--loss-case" in sys.argv
    if loss_case:
        VERSION = "1.4-final"
    resume = "--resume" in sys.argv
    if resume:
        sequence = max(int(p.stem.rsplit("-", 1)[-1]) for p in OUT.glob(f"v{VERSION}-action-*.json"))
    log = subprocess.Popen(["adb", "-s", DEVICE, "logcat", "-v", "threadtime", "-T", "1",
        "-s", "JadeChess:I", "JadeChessResults:I", "JadeChessCombat:I", "JadeChessFx:I",
        "JadeChessPanels:I", "JadeChessStars:I", "AndroidRuntime:E"], stdout=subprocess.PIPE,
        stderr=subprocess.STDOUT, bufsize=0)
    selector = selectors.DefaultSelector()
    selector.register(log.stdout, selectors.EVENT_READ)
    prepared = set()
    captured = resume and (OUT / f"v{VERSION}-device-immediate-result.json").exists()
    paid_captured = resume and (OUT / f"v{VERSION}-device-round-payment.json").exists()
    defeated_captured = resume and (OUT / f"v{VERSION}-device-defeated.json").exists()
    finished = False
    start = time.monotonic()
    try:
        if not resume:
            act("开始棋会")
        first = capture("device-resumed" if resume else "device-start")
        names = [x.get("name", "") for x in first.get("content", [])]
        assert not any(word in x for x in names for word in ("铜刃", "玉甲", "装备"))
        assert any("升级还差" in x for x in names), names
        if not resume:
            assert any("备战 · 第 1" in x for x in names), names
            assert len([x for x in first.get("controls", []) if x.get("name", "").startswith("招募")]) == 5
            if loss_case:
                # A legal underfilled army exercises population damage and elimination.
                # No test-only API, gold grant, clock change or rule override is used.
                offers = [c["name"] for c in first.get("controls", []) if c.get("name", "").startswith("招募")]
                unique = [name for name in offers if offers.count(name) == 1]
                if unique:
                    act(unique[0])
                    act("准备完毕")
                    capture("device-single-hero")
            else:
                act("棋手托管")
                capture("device-ai-preparation")
            prepared.add(1)
        elif any("备战 · 第 " in x for x in names):
            round_number = int(next(re.search(r"备战 · 第 (\d+)", x).group(1) for x in names if "备战 · 第 " in x))
            prepared.add(round_number)
        with (OUT / f"v{VERSION}-device-match.log").open("a" if resume else "w") as output:
            while time.monotonic() - start < 660:
                events = selector.select(timeout=10)
                if not events:
                    if log.poll() is not None:
                        raise RuntimeError("ADB log stream ended unexpectedly")
                    continue
                line = log.stdout.readline().decode("utf-8", errors="replace")
                if not line:
                    raise RuntimeError("ADB log stream closed")
                output.write(line)
                output.flush()
                if "AndroidRuntime" in line and "FATAL" in line:
                    raise RuntimeError(line)
                if "JadeChessResults" in line or re.search(r"JadeChess: phase=", line):
                    print(line.rstrip(), flush=True)
                if not captured and "phase=BATTLE" in line and "playerId=0," in line and "roundPaid=false" in line:
                    capture("device-immediate-result")
                    captured = True
                if not paid_captured and "JadeChessResults" in line and "playerId=0," in line and "roundPaid=true" in line:
                    paid = capture("device-round-payment")
                    names = [x.get("name", "") for x in paid.get("content", [])]
                    assert any("基础5" in x and "利息" in x for x in names), names
                    paid_captured = True
                if not defeated_captured and "你:0:" in line:
                    capture("device-defeated")
                    defeated_captured = True
                phase = re.search(r"JadeChess: phase=(\w+) round=(\d+)", line)
                if not phase:
                    continue
                name, round_number = phase.group(1), int(phase.group(2))
                if name == "PREPARATION" and round_number not in prepared:
                    prepared.add(round_number)
                    current = capture(f"device-round-{round_number}")
                    if loss_case and any(c.get("name") == "准备完毕" for c in current.get("controls", [])):
                        act("准备完毕")
                elif name == "FINISHED":
                    final = capture("device-finished")
                    names = [x.get("name", "") for x in final.get("content", [])]
                    assert any("棋会结束" in x and "第 " in x for x in names), names
                    assert any(c.get("name") == "再开一局" for c in final.get("controls", []))
                    act("再开一局")
                    act("暂停")
                    restarted = capture("device-restarted-paused")
                    assert any(c.get("name") == "继续" for c in restarted.get("controls", []))
                    finished = True
                    break
        if not finished:
            raise TimeoutError("Real match did not reach FINISHED within 660 seconds")
        print(json.dumps({"finished": True, "immediate_result_captured": captured,
            "round_payment_captured": paid_captured, "defeated_captured": defeated_captured,
            "scenario": "underfilled_army" if loss_case else "autoplay",
            "public_actions": sequence, "wall_seconds": round(time.monotonic() - start, 1)}), flush=True)
    finally:
        selector.close()
        log.terminate()
        log.wait(timeout=10)


if __name__ == "__main__":
    main()
