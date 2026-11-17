"""Second replay test: setup AND turns both recorded through the phone microphone (like the app: voice setup and
listening use the same microphone). For each of the 20 people (and the owner): profile = mean of the 3 replayed setup
clips (VoiceProfile.save); turns = their other replayed clips; strangers = everyone else's replayed turns.

python -I replay_enroll_analysis.py <replay2 dir> <embeddings.bin>
"""
import sys
from collections import defaultdict
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[2] / ".deps" / "audio-tools"))
import numpy as np  # noqa: E402

root, binary = Path(sys.argv[1]), Path(sys.argv[2])
rows = [l.rstrip("\n").split("\t") for l in open(root / "corpus" / "manifest.tsv", encoding="utf-8") if l.strip()]
full, windows = {}, defaultdict(list)
data = binary.read_bytes(); at = 0
while at < len(data):
    clip = int.from_bytes(data[at:at + 4], "little"); kind = data[at + 4]; dim = int.from_bytes(data[at + 5:at + 7], "little")
    at += 7
    vec = np.frombuffer(data, dtype="<f4", count=dim, offset=at).copy() if dim else None
    at += dim * 4
    if kind == 0: full[clip] = vec
    elif kind == 1: windows[clip].append(vec)

by = defaultdict(list)
for i, (_, spk, _) in enumerate(rows):
    by[spk].append(i)


def usable(i): return full.get(i) is not None and len(windows[i]) >= 2 and all(w is not None for w in windows[i])


def crit(p, i):
    ws = windows[i]; s = [float(p @ w) for w in ws]; best = np.inf
    for k in range(len(ws) - 1):
        if float(ws[k] @ ws[k + 1]) < .65: return -np.inf
        lo, mean = min(s[k], s[k + 1]), (s[k] + s[k + 1]) / 2
        best = min(best, max(lo, min(lo + .05, mean - .02)))
    return best


profiles, tests = {}, {}
for spk, ids in by.items():
    setup = [i for i in ids[:3] if full.get(i) is not None]
    if len(setup) < 3: continue
    vecs = [full[i] for i in setup]
    if not all(float(a @ b) >= .6 for a in vecs for b in vecs):
        print(f"{spk}: setup samples differ too much (app would refuse)"); continue
    p = np.mean(vecs, axis=0); profiles[spk] = p / np.linalg.norm(p)
    tests[spk] = [i for i in ids[3:] if usable(i)]

gen = {spk: [crit(profiles[spk], i) for i in tests[spk]] for spk in profiles}
strangers = [crit(profiles[a], i) for a in profiles for b in profiles if b != a for i in tests[b]]
closest = max(strangers)
print(f"people with a phone-recorded setup: {len(profiles)}; owner turns: {sum(len(v) for v in gen.values())}; stranger turns: {len(strangers)}")
print(f"closest stranger: {closest:.3f}  -> safe bar (closest + .08): {closest + .08:.2f}")
for bar in (.60, .64, .68, .71, .75):
    allg = [c for v in gen.values() for c in v]
    print(f"  bar {bar:.2f}: owner {100 * np.mean([c >= bar for c in allg]):5.1f}% | strangers {sum(c >= bar for c in strangers)}"
          + (f" | your clips {sum(c >= bar for c in gen['OWNER'])}/{len(gen['OWNER'])}" if "OWNER" in gen else ""))
if "OWNER" in gen:
    print("your clips (turn values):", [round(c, 2) for c in gen["OWNER"]])
