"""Which strangers got through, and from whom. For each plain bar, lists every (owner, stranger) pair with accepted
turns, and how alike the two people are overall (clean-clip centroids). A pair that accepts in both directions with
centroids ≥ .90 is marked "possible same person": public sets give anonymous ids, so one volunteer can appear twice
(Common Voice gives a new id per browser or device). They still count as strangers in every total.

Usage: python -I speaker_accepted_pairs.py <bar> [<bar> ...] -- <corpus dir> <signatures .bin> ...
"""
import sys
from collections import Counter, defaultdict
from pathlib import Path

here = Path(__file__).resolve().parent
split = sys.argv.index("--")
BARS = [float(b) for b in sys.argv[1:split]]
src = (here / "speaker_analysis.py").read_text(encoding="utf-8").split("results = {}")[0]
sys.argv = ["x", "NUL"] + sys.argv[split + 1:]
g = {"__file__": str(here / "speaker_analysis.py"), "__name__": "x"}
exec(src, g)
np = g["np"]; CRIT = g["CRIT"]; people = g["people"]; single_index = g["single_index"]; masks = g["masks"]
turn_spk = g["turn_spk"]; turn_cond = g["turn_cond"]; speaker = g["speaker"]; group = g["group"]; full = g["full"]

clean = defaultdict(list); source = {}
for i, v in g["full"].items():
    if v is not None and g["condition"][i] == "clean":
        clean[speaker[i]].append(v / np.linalg.norm(v))
    source.setdefault(speaker[i], group[i])
centroid = {s: (lambda m: m / np.linalg.norm(m))(np.mean(v, 0)) for s, v in clean.items()}


def alike(a, b):
    return float(centroid[a] @ centroid[b]) if a in centroid and b in centroid else float("nan")


for bar in BARS:
    hits = Counter(); rooms = defaultdict(Counter)
    for person in people:
        _, s = masks(person)
        acc = (CRIT[[single_index[person]]] >= bar).any(axis=0) & s
        for t in np.where(acc)[0]:
            hits[(person, turn_spk[t])] += 1; rooms[(person, turn_spk[t])][turn_cond[t]] += 1
    pairs = {tuple(sorted(k)) for k in hits}
    print(f"bar {bar:.2f}: {sum(hits.values())} stranger turns accepted, {len(pairs)} distinct pairs of people")
    same = 0
    for a, b in sorted(pairs, key=lambda p: -(hits[p] + hits[p[::-1]])):
        both = hits[(a, b)] > 0 and hits[(b, a)] > 0; sim = alike(a, b)
        flag = both and sim >= .90; same += flag
        print(f"  {hits[(a, b)] + hits[(b, a)]:4d} turns  {a} ({source[a]}) <-> {b} ({source[b]})  "
              f"alike {sim:.3f}  {'both ways' if both else 'one way'}{'  possible same person' if flag else ''}  "
              f"rooms {dict(rooms[(a, b)] + rooms[(b, a)])}")
    print(f"  possible same person: {same} of {len(pairs)} pairs")
