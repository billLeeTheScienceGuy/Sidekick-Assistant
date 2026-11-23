"""Idea: one learned calibration (logistic regression on score, cohort z, speech length, SNR) instead of hand-set bars.
People are split in two halves: the calibration and its threshold (zero strangers on the training half, plus a .08-
equivalent margin) are learned on one half and measured on the other, then swapped. The raw score with the same
procedure is the baseline. Run twice: with every stranger, and without the pairs that every model scores as one
person (see SPEAKER_TUNING.md, "Larger test").

Usage: python -I speaker_calibration.py [--drop-pairs] <corpus dir> <signatures .bin> ...
"""
import sys
from pathlib import Path

here = Path(__file__).resolve().parent
drop = "--drop-pairs" in sys.argv
args = [a for a in sys.argv[1:] if a != "--drop-pairs"]
src = (here / "speaker_analysis.py").read_text(encoding="utf-8").split("results = {}")[0]
sys.argv = ["x", "NUL"] + args
g = {"__file__": str(here / "speaker_analysis.py"), "__name__": "x"}
exec(src, g)
np = g["np"]; CRIT, ZMIN = g["CRIT"], g["ZMIN"]; turns = g["turns"]; spk = g["turn_spk"]
people = list(g["people"]); single = g["single_index"]; used = g["people_used"]; utt = g["utterance"]
q = np.array([g["quality"].get(i, np.array([0, 0], dtype=np.float32)) for i in turns])
secs = np.log(np.clip(q[:, 0], .5, 60)); snr = np.clip(q[:, 1], -10, 60) / 10

PAIRS = {frozenset(p) for p in [
    ("ls4267", "ls8226"), ("cv-03643e0c842d570b", "cv-20018ecf222cd872"), ("cv-c2a07c79a1a62968", "cv-c69b225ce65b57f9"),
    ("cv-0130f27982127506", "cv-10956e1648dd0001"), ("cv-70d078ed35a22184", "cv-e29f04c79ece875b"),
    ("cv-327635db44765f8d", "cv-6b66ebf798e9c73e"), ("cv-31d03f892dd6a156", "cv-3915c32c52a18d5c"),
    ("cv-6561cb5f62806c1c", "cv-8e7eec39d53c4d9c"), ("cv-25c4c3ef37578468", "cv-7ddd2eef0ce4e664"),
    ("cv-37ca2f7988f85d42", "cv-b2e6a64e6265a09a"), ("cv-0130f27982127506", "cv-088dd259232e6709"),
    ("cv-088dd259232e6709", "cv-10956e1648dd0001"), ("cv-6f63ceed316e1d3c", "cv-86df6ab68d5dec85"),
    ("cv-5c3f347cfe7fb380", "cv-e22b0e183b01773e"), ("cv-12b668a1ada1828b", "cv-94130ea17023c483"),
    ("cv-12b668a1ada1828b", "cv-4edaa220f83b835e"), ("cv-37f0e8a78c2a3b81", "cv-971cbebbaa573fc9")]}


def features(p, idx):
    z = np.nan_to_num(ZMIN[p, idx], nan=0.0, posinf=10, neginf=-10)
    c = np.nan_to_num(CRIT[p, idx], nan=-1.0, posinf=1.0, neginf=-1.0)
    return np.stack([c, np.clip(z, -10, 10) / 5, secs[idx], snr[idx], c * secs[idx], c * snr[idx], np.ones_like(c)], 1)


def collect(group, rng):
    gen, st = [], []
    for person in group:
        p = single[person]
        gm = np.where((spk == person) & np.array([utt[i] not in used[person] for i in turns]))[0]
        sm = spk != person
        if drop: sm &= np.array([frozenset((person, s)) not in PAIRS for s in spk])
        sm = np.where(sm)[0]
        gen.append(features(p, gm)); st.append(features(p, sm))
    return np.concatenate(gen), np.concatenate(st)


def fit(gen, st, rng):
    sub = st[rng.choice(len(st), min(len(st), 400_000), replace=False)]
    X = np.concatenate([gen, sub]); y = np.r_[np.ones(len(gen)), np.zeros(len(sub))]
    w = np.r_[np.full(len(gen), .5 / len(gen)), np.full(len(sub), .5 / len(sub))]
    mu, sd = X[:, :-1].mean(0), X[:, :-1].std(0) + 1e-9
    Xn = np.c_[(X[:, :-1] - mu) / sd, X[:, -1]]
    beta = np.zeros(X.shape[1])
    for _ in range(3000):
        pr = 1 / (1 + np.exp(-Xn @ beta))
        beta -= 2.0 * (Xn.T @ (w * (pr - y)) + 1e-4 * beta)
    return lambda F: np.c_[(F[:, :-1] - mu) / sd, F[:, -1]] @ beta


rng = np.random.default_rng(7)
order = rng.permutation(people); halves = [list(order[::2]), list(order[1::2])]
data = [collect(h, rng) for h in halves]
print(f"strangers: {'without the 17 same-person pairs' if drop else 'all'}")
tot = {"raw": [0, 0, 0], "cal": [0, 0, 0]}
for k in (0, 1):
    (gtr, str_), (gte, ste) = data[k], data[1 - k]
    score = fit(gtr, str_, rng)
    for name, f in (("raw", lambda F: F[:, 0]), ("cal", score)):
        tr_gen, tr_st = f(gtr), f(str_)
        # margin: the same share of the genuine spread that .08 is for the raw score
        margin = 0.0  # both compared at "zero strangers on the training half"; the .08 safety margin is applied separately in the app
        bar = tr_st.max() + margin
        te_gen, te_st = f(gte), f(ste)
        tot[name][0] += int((te_gen >= bar).sum()); tot[name][1] += len(te_gen); tot[name][2] += int((te_st >= bar).sum())
        print(f"  half {k + 1} {name}: bar {bar:.3f}  owner {np.mean(te_gen >= bar) * 100:5.1f}%  strangers {int((te_st >= bar).sum())} of {len(te_st)}")
for name, (a, n, s) in tot.items():
    print(f"overall {name}: owner {a / n * 100:.1f}%  strangers accepted {s}")
