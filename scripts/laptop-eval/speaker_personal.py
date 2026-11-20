"""Two owner-recognition ideas, tested with the same split as speaker_calibration.py (bars learned at "zero strangers"
on one half of the people, measured on the other half, then swapped; the 17 same-person pairs left out).

1. Personal voice model: for each owner, a linear direction that separates their setup recordings from the app's
   stranger voices (shrunk Fisher discriminant: w = (cohort covariance + shrinkage)^-1 (owner mean - stranger mean)).
   Each speaker window gets w.x, expressed in stranger deviations (z against the stranger voices); a turn's score is
   its weakest window. Tested alone and as a second way in next to today's rule (either one accepts).
   "learned": up to 5 of the owner's own clean-room turns that today's rule accepted very confidently (>= .78) are
   added to the setup recordings, as learning across sessions would; those turns are then left out of the test for
   every method.
2. Distance tie-breaker: a turn that sounds near (SNR >= 25 dB: clean, close speech) may pass with a bar lowered by
   delta. Strangers here are recorded just as close, so this is the worst case of someone standing next to the phone.

Usage: python -I speaker_personal.py <corpus dir> <signatures .bin> ...
"""
import sys
from pathlib import Path

here = Path(__file__).resolve().parent
src = (here / "speaker_analysis.py").read_text(encoding="utf-8").split("results = {}")[0]
sys.argv = ["x", "NUL"] + sys.argv[1:]
g = {"__file__": str(here / "speaker_analysis.py"), "__name__": "x"}
exec(src, g)
cal = (here / "speaker_calibration.py").read_text(encoding="utf-8")
PAIRS = eval(cal.split("PAIRS = ", 1)[1].split("\n\n\n")[0], {"frozenset": frozenset})
np = g["np"]; CRIT, ZMIN = g["CRIT"], g["ZMIN"]; turns = g["turns"]; spk = g["turn_spk"]; cond = g["turn_cond"]
people = list(g["people"]); single = g["single_index"]; used = g["people_used"]; utt = g["utterance"]
W, starts, cohort, full, windows = g["W"], g["starts"], g["cohort"], g["full"], g["windows"]
speaker, condition = g["speaker"], g["condition"]
q = np.array([g["quality"].get(i, np.array([0, 0], dtype=np.float32)) for i in turns])
near = q[:, 1] >= 25
turn_utt = np.array([utt[i] for i in turns])

mu_c = cohort.mean(0); C = np.cov(cohort.T)
by_clip = {}
for i, s in enumerate(speaker):
    by_clip.setdefault(s, []).append(i)


def direction(pos, lam):
    S = (1 - lam) * C + lam * np.trace(C) / C.shape[0] * np.eye(C.shape[0])
    w = np.linalg.solve(S, pos.mean(0) - mu_c)
    proj = cohort @ w
    return w, proj.mean(), proj.std() + 1e-9


def personal_scores(person, lam, extra):
    # Only what the app stores: the averaged quiet-room setup signature (learning across sessions moves that same
    # signature, so the personal model follows it without storing anything else).
    w, m, sd = direction(g["P"][single[person]][None, :].astype(np.float64), lam)
    z = (W @ w - m) / sd
    return np.minimum.reduceat(z, starts)


def learned(person):
    p = single[person]
    k = np.where((spk == person) & (cond == "clean") & ~np.isin(turn_utt, list(used[person])) & (CRIT[p] >= .78))[0][:5]
    return k


def collect(group, lam, learn):
    out = []
    for person in group:
        p = single[person]
        lk = learned(person) if learn else np.array([], int)
        extra = [w for k in lk for w in windows[turns[k]]] + [full[turns[k]] for k in lk]
        gm = (spk == person) & ~np.isin(turn_utt, list(used[person])); gm[lk] = False
        sm = (spk != person) & np.array([frozenset((person, s)) not in PAIRS for s in spk])
        raw = np.nan_to_num(CRIT[p], nan=-1, posinf=-1, neginf=-1)
        zg = np.nan_to_num(ZMIN[p], nan=-10, posinf=-10, neginf=-10)
        per = personal_scores(person, lam, extra) if lam is not None else np.zeros(len(turns))
        per = np.where(raw > -1, per, -99)  # inconsistent turns never pass
        for mask, kind in ((gm, 1), (sm, 0)):
            idx = np.where(mask)[0]
            out.append(np.stack([raw[idx], zg[idx], per[idx], near[idx], cond[idx] != "echo", np.full(len(idx), kind)], 1))
    return np.concatenate(out)


rng = np.random.default_rng(7)
order = rng.permutation(people); halves = [list(order[::2]), list(order[1::2])]


def evaluate(lam, learn):
    data = [collect(h, lam, learn) for h in halves]
    res = {k: [0, 0, 0, 0] for k in ("raw", "personal", "either", "near")}
    for k in (0, 1):
        tr, te = data[k], data[1 - k]
        def split(d): return d[d[:, 5] == 1], d[d[:, 5] == 0]
        tg, ts = split(tr); eg, es = split(te)
        rbar = ts[:, 0].max() + 1e-6                      # raw: zero strangers on the training half
        pbar = ts[:, 2].max() + 1e-6                      # personal alone
        left = ts[ts[:, 0] < rbar]
        ebar = left[:, 2].max() + 1e-6                    # personal as a second way in
        # near tie-breaker: largest lowering that still lets no near training stranger in
        nts = ts[ts[:, 3] == 1]
        nbar = nts[:, 0].max() + 1e-6
        rules = {"raw": lambda d: d[:, 0] >= rbar, "personal": lambda d: d[:, 2] >= pbar,
                 "either": lambda d: (d[:, 0] >= rbar) | (d[:, 2] >= ebar),
                 "near": lambda d: (d[:, 0] >= rbar) | ((d[:, 3] == 1) & (d[:, 0] >= nbar))}
        for name, f in rules.items():
            a = f(eg); r = res[name]
            r[0] += int(a.sum()); r[1] += len(eg); r[2] += int(f(es).sum()); r[3] += int((a & (eg[:, 4] == 1)).sum())
        print(f"   half {k + 1}: raw bar {rbar:.3f}, personal bar {pbar:.2f} z, second-way bar {ebar:.2f} z, near bar {nbar:.3f}")
    for name, (a, n, s, _) in res.items():
        print(f"   {name:9s} owner {a / n * 100:5.1f}%  strangers accepted {s}")


for lam in (.05, .2, .5, .8):
    print(f"shrinkage {lam}")
    evaluate(lam, False)


# 3. Learned voice weighting (WCCN): a 192 x 192 transform learned from how each training-half person's own clips vary
#    (rooms, days, phrasing), shipped once with the app. Signatures are transformed, renormalized and scored with the
#    same rule; bars at zero strangers on the training half, measured on the other half.
cons_ok, pair_ok = g["cons"] >= .65, g["pair_ok"]
Pidx = np.array([single[p] for p in people]); Ps = g["P"][Pidx].astype(np.float64); pos_of = {p: n for n, p in enumerate(people)}


def crit(Pt, Wt):
    S = Pt @ Wt.T
    lo = np.minimum(S[:, :-1], S[:, 1:]); mean = (S[:, :-1] + S[:, 1:]) / 2
    v = np.maximum(lo, np.minimum(lo + .05, mean - .02))
    v = np.where(cons_ok[None, :], v, -np.inf); v = np.where(pair_ok[None, :], v, np.inf)
    return np.nan_to_num(np.minimum.reduceat(v, starts, axis=1), nan=-1, posinf=-1, neginf=-1)


def wccn(group, lam):
    rows = []
    for person in group:
        vs = [full[i] for i in by_clip[person] if full.get(i) is not None]
        if len(vs) >= 2:
            m = np.stack(vs).astype(np.float64); rows.append(m - m.mean(0))
    D = np.concatenate(rows); Sw = D.T @ D / len(D)
    Sw = (1 - lam) * Sw + lam * np.trace(Sw) / Sw.shape[0] * np.eye(Sw.shape[0])
    return np.linalg.cholesky(np.linalg.inv(Sw))


def masks(person):
    gm = (spk == person) & ~np.isin(turn_utt, list(used[person]))
    sm = (spk != person) & np.array([frozenset((person, s)) not in PAIRS for s in spk])
    return gm, sm


def scores(Cm, group):
    gen, st = [], []
    for person in group:
        gm, sm = masks(person); row = Cm[pos_of[person]]; gen.append(row[gm]); st.append(row[sm])
    return np.concatenate(gen), np.concatenate(st)


base = crit(Ps, W.astype(np.float64))
for lam in (.1, .3, .6):
    tot = {"today": [0, 0, 0], "weighted": [0, 0, 0], "either": [0, 0, 0]}
    for k in (0, 1):
        trn, tst = halves[k], halves[1 - k]
        L = wccn(trn, lam)
        def t(X): Y = X @ L; return Y / np.linalg.norm(Y, axis=1, keepdims=True)
        Cw = crit(t(Ps), t(W.astype(np.float64)))
        bg, bs = scores(base, trn); wg, ws_ = scores(Cw, trn)
        rb, wb = bs.max() + 1e-6, ws_.max() + 1e-6
        eb = ws_[bs < rb].max() + 1e-6
        tbg, tbs = scores(base, tst); twg, tws = scores(Cw, tst)
        for name, ga, sa in (("today", tbg >= rb, tbs >= rb), ("weighted", twg >= wb, tws >= wb),
                             ("either", (tbg >= rb) | (twg >= eb), (tbs >= rb) | (tws >= eb))):
            r = tot[name]; r[0] += int(ga.sum()); r[1] += len(ga); r[2] += int(sa.sum())
    print(f"voice weighting, shrinkage {lam}")
    for name, (a, n, s) in tot.items():
        print(f"   {name:9s} owner {a / n * 100:5.1f}%  strangers accepted {s}")
