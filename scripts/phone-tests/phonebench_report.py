"""Scores the on-phone model comparison (PhoneModelBenchTest, set "phonebench": 20 noisy everyday clips per language).
Pull the results first:
  adb shell run-as com.sidekick.app.benchmark sh -c 'cat files/evaluation/phonebench/bench-*.json' (or copy each file)
Usage: python -I phonebench_report.py <folder with bench-*.json>
Prints character error rate (CER, lower is better) per model and language, and seconds per clip on the phone.
"""
import json
import re
import sys
import unicodedata
from collections import defaultdict
from pathlib import Path


def norm(text: str) -> str:
    text = unicodedata.normalize("NFKC", text.lower())
    return re.sub(r"[\s\p{P}]" if False else r"[\s.,!?;:'\"“”‘’«»()\[\]、。，！？：；…-]", "", text)


def edits(a: str, b: str) -> int:
    prev = list(range(len(b) + 1))
    for i, ca in enumerate(a, 1):
        cur = [i] + [0] * len(b)
        for j, cb in enumerate(b, 1):
            cur[j] = min(prev[j] + 1, cur[j - 1] + 1, prev[j - 1] + (ca != cb))
        prev = cur
    return prev[-1]


def main():
    folder = Path(sys.argv[1])
    sys.stdout.reconfigure(encoding="utf-8")
    table = defaultdict(lambda: defaultdict(lambda: [0, 0, 0.0, 0, 0]))  # model -> lang -> [edits, chars, seconds, clips, errors]
    load = {}
    for f in sorted(folder.glob("bench-*.json")):
        for r in json.loads(f.read_text(encoding="utf-8")):
            ref, hyp = norm(r["reference"]), r["recognized"]
            t = table[r["mode"]][r["language"]]
            t[3] += 1; t[2] += r["elapsedMs"] / 1000
            load[r["mode"]] = r.get("loadMs", 0) / 1000
            if hyp.startswith("ERROR"):
                t[4] += 1; t[0] += len(ref); t[1] += len(ref); continue
            t[0] += edits(ref, norm(hyp)); t[1] += len(ref)
    langs = ["en", "ko", "zh", "fr", "de", "ru", "ar"]
    print("CER % (seconds per clip) · lower is better · load = seconds to load the model")
    print(f"{'model':12s} {'load':>5s} " + " ".join(f"{l:>14s}" for l in langs))
    for model, row in table.items():
        cells = []
        for l in langs:
            if l not in row: cells.append(f"{'':>14s}"); continue
            e, c, s, n, err = row[l]
            cells.append(f"{100 * e / max(c, 1):5.1f} ({s / n:4.1f}s)" + ("!" if err else " "))
        print(f"{model:12s} {load.get(model, 0):5.1f} " + " ".join(f"{x:>14s}" for x in cells))
    print("! = some clips failed (counted as fully wrong)")


if __name__ == "__main__":
    main()
