"""Learning the owner's voice across sessions: after each session (one room), the saved quiet-room profile moves a
little toward that session's very confident owner turns; never further than a fixed distance from the original setup.

  p_next = normalize((1 - RATE) * p + RATE * mean(confident turns))   only turns with value >= CONFIDENT, at least 2
  reset guard: if similarity(p_next, setup profile) < KEEP, keep p (no update)

Each person goes through sessions in the order of ROOMS; owner recognition in each session uses the profile as it was
at the start of that session (bar Q). Strangers: every other person's turns in that room against the owner's current
profile. Compares with a fixed profile (no learning).

python -I speaker_crosssession.py <Q> <corpus> <bin> [<corpus> <bin> ...]
"""
import sys
from pathlib import Path

Q = float(sys.argv[1])
import os
RATE, CONFIDENT, KEEP, MARGIN = (float(os.environ.get(k, d)) for k, d in (("RATE", .15), ("CONFIDENT", .78), ("KEEP", .90), ("MARGIN", .08)))
ROOMS = os.environ.get("ROOMS", "clean,noisy,far,babble,echo").split(",")
here = Path(__file__).resolve().parent
src = (here / "speaker_analysis.py").read_text(encoding="utf-8").split("T_GRID =")[0]
sys.argv = ["x", "NUL"] + sys.argv[2:]
g = {"__file__": str(here / "speaker_analysis.py"), "__name__": "x"}
exec(src, g)
np = g["np"]
turns, spk, cond = g["turns"], g["turn_spk"], g["turn_cond"]
W, starts, counts, cons = g["W"], g["starts"], g["counts"], g["cons"]
FULL = np.stack([g["full"][i] for i in turns])


def unit(v): return v / np.linalg.norm(v)


def crit_all(profile):
    """Turn values for every turn against one profile (vectorized like speaker_analysis)."""
    s = W @ profile
    lo = np.minimum(s[:-1], s[1:]); mean = (s[:-1] + s[1:]) / 2
    pv = np.maximum(lo, np.minimum(lo + .05, mean - .02))
    pv = np.where(cons >= .65, pv, -np.inf)
    ok = np.ones(len(W) - 1, bool); ok[(starts + counts - 1)[:-1]] = False
    pv = np.where(ok, pv, np.inf)
    return np.minimum.reduceat(pv, starts)


rooms = [r for r in ROOMS if (cond == r).any()]
static = {r: [0, 0] for r in rooms}; learned = {r: [0, 0] for r in rooms}
closest_static, closest_learned, strangers_learned, drift = -1.0, -1.0, 0, []
for person in g["people"]:
    setup = g["P"][g["single_index"][person]]
    used = g["people_used"][person]
    p = setup.copy()
    for room in rooms:
        mine = (spk == person) & (cond == room) & np.array([g["utterance"][i] not in used for i in turns])
        others = (spk != person) & (cond == room)
        if not mine.any(): continue
        c_static, c_learned = crit_all(setup), crit_all(p)
        static[room][0] += int((c_static[mine] >= Q).sum()); static[room][1] += int(mine.sum())
        learned[room][0] += int((c_learned[mine] >= Q).sum()); learned[room][1] += int(mine.sum())
        if others.any():
            closest_static = max(closest_static, float(c_static[others].max()))
            closest_learned = max(closest_learned, float(c_learned[others].max()))
            strangers_learned += int((c_learned[others] >= Q).sum())
        confident = np.where(mine & (c_learned >= CONFIDENT))[0]
        if len(confident) >= 2:
            candidate = unit((1 - RATE) * p + RATE * unit(FULL[confident].mean(axis=0)))
            if float(candidate @ setup) >= KEEP: p = candidate
        drift.append(float(p @ setup))

print(f"bar {Q}; learning rate {RATE}, only turns >= {CONFIDENT}, never below {KEEP} similarity to the setup profile")
for room in rooms:
    s, l = static[room], learned[room]
    print(f"  {room:7s} fixed profile {100 * s[0] / max(1, s[1]):5.1f}% -> learned {100 * l[0] / max(1, l[1]):5.1f}%  ({s[1]} turns)")
real = [r for r in rooms if r != "echo"]
fs = sum(static[r][0] for r in real) / max(1, sum(static[r][1] for r in real))
fl = sum(learned[r][0] for r in real) / max(1, sum(learned[r][1] for r in real))
print(f"  overall (no echo): fixed {100 * fs:.1f}% -> learned {100 * fl:.1f}%")
print(f"strangers: closest fixed {closest_static:.3f}, closest learned {closest_learned:.3f}; accepted with learning {strangers_learned}; "
      f"lowest similarity to setup {min(drift):.3f}")
