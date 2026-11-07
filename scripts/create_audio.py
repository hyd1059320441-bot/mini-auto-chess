#!/usr/bin/env python3
"""Create original short, mono PCM chess sounds without external assets."""
from pathlib import Path
import math
import random
import struct
import wave

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
    print("Created 4 original mono 24kHz PCM sounds")
