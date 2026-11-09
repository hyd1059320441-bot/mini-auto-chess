#!/usr/bin/env python3
"""Create original short, mono PCM chess sounds without external assets."""
from pathlib import Path
import math
import random
import struct
import wave
import json
import hashlib

ROOT = Path(__file__).resolve().parents[1] / "app/src/main/assets/audio"
RATE = 24000


def write(name, duration, frequency, decay, noise=0.0, rise=0.0):
    rng = random.Random(37)
    samples = []
    for index in range(int(RATE * duration)):
        t = index / RATE
        envelope = min(t / 0.004, 1.0) * math.exp(-decay * t)
        phase = 2 * math.pi * (frequency * t + rise * t * t / 2)
        sound = math.sin(phase) + 0.3 * math.sin(phase * 1.51)
        sound = (sound * (1 - noise) + rng.uniform(-1, 1) * noise) * envelope * 0.2
        samples.append(struct.pack("<h", int(max(-1, min(1, sound)) * 32767)))
    with wave.open(str(ROOT / f"{name}.wav"), "wb") as output:
        output.setnchannels(1)
        output.setsampwidth(2)
        output.setframerate(RATE)
        output.writeframes(b"".join(samples))


if __name__ == "__main__":
    ROOT.mkdir(parents=True, exist_ok=True)
    write("hit", 0.13, 390, 32, noise=0.45, rise=-800)
    write("shot", 0.15, 720, 25, noise=0.2, rise=-2400)
    write("cast", 0.25, 470, 13, rise=1700)
    write("heal", 0.32, 660, 10, rise=1000)
    write("magic_attack", .18, 890, 20, noise=.06, rise=1800)
    write("fall", .25, 190, 14, noise=.15, rise=-500)
    heroes = [
        ("copper_guard", "metal", 174), ("forest_ranger", "wind", 784),
        ("rune_apprentice", "chime", 622), ("ember_acolyte", "warm", 440),
        ("dawn_priest", "chime", 523), ("moss_guard", "wood", 146),
        ("rune_archer", "wind", 932), ("flame_fighter", "metal", 233),
        ("dawn_archer", "wind", 1046), ("forest_blessing", "warm", 659),
        ("rune_sage", "chime", 698), ("core_mage", "rumble", 110),
        ("dawn_oracle", "bell", 587), ("forest_witch", "wood", 349),
        ("rune_sentinel", "bell", 466), ("ember_breaker", "rumble", 82)]

    def pcm(name, samples):
        with wave.open(str(ROOT / f"{name}.wav"), "wb") as output:
            output.setnchannels(1)
            output.setsampwidth(2)
            output.setframerate(RATE)
            output.writeframes(b"".join(struct.pack("<h", round(max(-.95, min(.95, s)) * 32767)) for s in samples))

    for i, (hero, voice, base) in enumerate(heroes):
        duration = .58 + i % 4 * .07
        rng = random.Random(120 + i)
        samples = []
        for j in range(round(RATE * duration)):
            t = j / RATE
            sound = 0.
            for note in range(3):
                u = t - note * .065
                if u < 0:
                    continue
                attack = min(u / .009, 1)
                env = attack * math.exp(-u * (8 if voice in ("metal", "rumble", "wood") else 5))
                freq = base * (1, 1.25 + i % 3 * .04, 1.5)[note]
                phase = 2 * math.pi * (freq * u + (i - 7) * 26 * u * u)
                tone = math.sin(phase)
                if voice in ("metal", "bell", "chime"):
                    tone += .34 * math.sin(phase * (2.76 if voice == "metal" else 2)) + .13 * math.sin(phase * 4.2)
                elif voice in ("wood", "rumble"):
                    tone += .36 * rng.uniform(-1, 1) * math.exp(-u * 23)
                elif voice == "wind":
                    tone = .54 * tone + .48 * rng.uniform(-1, 1) * math.exp(-u * 16)
                else:
                    tone += .28 * math.sin(phase * 2)
                sound += tone * env * .12
            samples.append(sound * min((duration - t) / .025, 1))
        pcm(f"skill_{hero}", samples)

    # 32 bars, 108 BPM; C–Am–F–G, soft marimba/harp melody and brushed pulse.
    # Periodic modular time makes the tail of the final notes carry across the seam.
    beat = 60 / 108
    loop = round(32 * 4 * beat * RATE)
    music = [0.] * loop
    melody = [76, 79, 81, 79, 76, 74, 72, 74, 76, 79, 84, 81, 79, 76, 74, 72,
              77, 81, 84, 81, 79, 77, 76, 74, 79, 83, 86, 83, 81, 79, 74, 71]
    chords = [(48, 60, 64, 67), (45, 57, 60, 64), (41, 53, 57, 60), (43, 55, 59, 62)]

    def note(at, midi, length, volume, bright=False):
        start = round(at * RATE)
        frequency = 440 * 2 ** ((midi - 69) / 12)
        for j in range(round(length * RATE)):
            t = j / RATE
            env = min(t / .012, 1) * math.exp(-t * (5.2 if bright else 2.6)) * min((length - t) / .06, 1)
            phase = 2 * math.pi * frequency * t
            sound = math.sin(phase) + (.25 if bright else .12) * math.sin(phase * 2)
            music[(start + j) % loop] += sound * env * volume

    for bar in range(32):
        chord = chords[(bar // 2) % 4]
        note(bar * 4 * beat, chord[0], 2.1, .043)
        for b in range(8):
            note((bar * 4 + b / 2) * beat, chord[1 + b % 3], .85, .025, True)
        for b in range(2):
            note((bar * 4 + b * 2) * beat, melody[(bar * 2 + b) % len(melody)], 1.35, .052, True)
        for b in (0, 2):
            note((bar * 4 + b) * beat, 36, .14, .022)
    pcm("garden_music", music)
    stats = {}
    for file in sorted(ROOT.glob("*.wav")):
        with wave.open(str(file)) as source:
            raw = source.readframes(source.getnframes())
            values = struct.unpack(f"<{len(raw)//2}h", raw)
            stats[file.stem] = {"seconds": source.getnframes() / RATE,
                "peak": max(abs(v) for v in values) / 32768,
                "sha256": hashlib.sha256(raw).hexdigest()}
    skill_hashes = [stats[f"skill_{h[0]}"]["sha256"] for h in heroes]
    assert len(set(skill_hashes)) == 16
    assert all(v["peak"] < .95 for v in stats.values())
    assert abs(music[0] - music[-1]) < .002, "Music loop seam must be continuous"
    (ROOT / "audio_manifest.json").write_text(json.dumps({"sample_rate": RATE, "channels": 1,
        "unique_skills": 16, "music_bpm": 108, "music_bars": 32, "loop_seam": abs(music[0] - music[-1]),
        "files": stats}, indent=2) + "\n")
    print("Created 16 unique skills, 3 shared attacks, fall sound and original 32-bar music loop")
