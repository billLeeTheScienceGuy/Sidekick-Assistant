"""Ideas to raise owner recognition without letting strangers in, simulated turn by turn per person and room.

Each person speaks their turns in order in one room ("session"); every idea gets its own bar, MARGIN above the closest
stranger it ever produced (strangers = every other person's turns in the same room, scored against the owner's
profile and the owner's session state). Ideas are added one at a time on top of the current rule:

  base     profiles: quiet-room >= Q, other rooms >= R (VoiceMatch family) and cohort z >= 2
  carry    session carry: whole-speech signature vs every confirmed turn and their mean >= S, profile >= .40
  adapt    (idea 2) session-adapted profile: quiet profile moved toward the confirmed turns (weight A), window rule
  fusion   (idea 3) second model: mean of both models' turn values (each against its quiet profile)
  neighbor (idea 4) a turn between two accepted turns of the session that sounds like both (whole-speech similarity)

python -I speaker_ideas.py <Q> <R> <S> --a <corpus> <bin> ... [--b <corpus> <bin> ...]
"""
import sys
from pathlib import Path

MARGIN = .08
argv = sys.argv[1:]
Q, R, S = (float(argv.pop(0)) for _ in range(3))
groups, cur = {"a": [], "b": []}, None
for x in argv:
    if x in ("--a", "--b"): cur = x[2]
    else: groups[cur].append(x)
here = Path(__file__).resolve().parent
src = (here / "speaker_analysis.py").read_text(encoding="utf-8").split("T_GRID =")[0]


def load(pairs):
    sys.argv = ["x", "NUL"] + pairs
    g = {"__file__": str(here / "speaker_analysis.py"), "__name__": "x"}
    exec(src, g)
    return g


A = load(groups["a"]); B = load(groups["b"]) if groups["b"] else None
np = A["np"]
names = [A["name"][i] for i in A["turns"]]
index_a = {n: k for k, n in enumerate(names)}
index_b = {B["name"][i]: k for k, i in enumerate(B["turns"])} if B else {}
spk = A["turn_spk"]; cond = A["turn_cond"]; utt = [A["utterance"][i] for i in A["turns"]]
FULL = np.stack([A["full"][i] for i in A["turns"]])
W, starts, counts = A["W"], A["starts"], A["counts"]
cons = A["cons"]


def crit(profile, k, g=A):
    """Raw-rule value of turn k against one profile (accepted iff bar <= value)."""
    a, n = g["starts"][k], g["counts"][k]
    s = g["W"][a:a + n] @ profile
    best = np.inf
    for j in range(n - 1):
        if g["cons"][a + j] < .65: return -np.inf
        lo, mean = min(s[j], s[j + 1]), (s[j] + s[j + 1]) / 2
        best = min(best, max(lo, min(lo + .05, mean - .02)))
    return best


bar = np.where(np.array(A["profile_room"]) == "clean", Q, R)
BASE = (A["CRIT"] >= bar[:, None]) & (A["ZMIN"] >= 2.0)
rooms = [r for r in ("clean", "noisy", "babble", "far", "echo", "replay") if (cond == r).any()]


def unit(v): return v / np.linalg.norm(v)


# Pass 1: collect stranger statistics for each idea (closest stranger), with no bars applied yet.
closest = {"carry": -1.0, "adapt": -1.0, "fusion": -1.0, "neighbor": -1.0}
sessions = []
for person in A["people"]:
    idx = np.where(A["person_of"] == person)[0]; quiet = A["P"][A["single_index"][person]]
    qb = B["P"][B["single_index"][person]] if B and person in B["single_index"] else None
    used = A["people_used"][person]
    for room in rooms:
        mine = [k for k in range(len(names)) if spk[k] == person and cond[k] == room and utt[k] not in used]
        others = [k for k in range(len(names)) if spk[k] != person and cond[k] == room]
        if not mine: continue
        sessions.append((person, room, idx, quiet, qb, mine, others))
        confirmed = [k for k in mine if BASE[idx, k].any()]
        if confirmed:
            anchor = FULL[confirmed[:1]]
            closest["carry"] = max(closest["carry"], float((FULL[others] @ anchor[0]).max()) if others else -1)
            adapted = unit((1 - .3) * quiet + .3 * unit(FULL[confirmed].mean(axis=0)))
            closest["adapt"] = max([closest["adapt"]] + [crit(adapted, k) for k in others])
        if qb is not None:
            fused = [(crit(quiet, k) + crit(qb, index_b[names[k]], B)) / 2 for k in others if names[k] in index_b]
            closest["fusion"] = max([closest["fusion"]] + fused)
        if len(confirmed) >= 2:
            pairs = list(zip(confirmed, confirmed[1:]))[:3]
            for k in others:
                for x, y in pairs:
                    closest["neighbor"] = max(closest["neighbor"], min(float(FULL[k] @ FULL[x]), float(FULL[k] @ FULL[y])))

bars = {k: round(v + MARGIN + .005, 2) for k, v in closest.items()}
bars["carry"] = max(bars["carry"], S)
print("closest stranger per idea:", {k: round(v, 3) for k, v in closest.items()})
print("bars (closest + .08):", bars)

# Pass 2: owner acceptance, ideas added one at a time; strangers re-checked under the full rule.
order = ["base", "carry", "adapt", "fusion", "neighbor"] if B else ["base", "carry", "adapt", "neighbor"]
gain = {i: {r: [0, 0] for r in rooms} for i in order}
strangers = 0
for person, room, idx, quiet, qb, mine, others in sessions:
    accepted = set(); confirmed = []
    for step, idea in enumerate(order):
        for k in mine:
            if k in accepted: continue
            ok = False
            if idea == "base": ok = bool(BASE[idx, k].any())
            elif idea == "carry" and confirmed:
                sims = FULL[confirmed] @ FULL[k]
                ok = sims.min() >= bars["carry"] and float(unit(FULL[confirmed].mean(axis=0)) @ FULL[k]) >= bars["carry"] and float(quiet @ FULL[k]) >= .40
            elif idea == "adapt" and confirmed:
                ok = crit(unit(.7 * quiet + .3 * unit(FULL[confirmed].mean(axis=0))), k) >= bars["adapt"]
            elif idea == "fusion" and qb is not None and names[k] in index_b:
                ok = (crit(quiet, k) + crit(qb, index_b[names[k]], B)) / 2 >= bars["fusion"]
            elif idea == "neighbor":
                pos = mine.index(k)
                if 0 < pos < len(mine) - 1 and mine[pos - 1] in accepted and mine[pos + 1] in accepted:
                    ok = min(float(FULL[k] @ FULL[mine[pos - 1]]), float(FULL[k] @ FULL[mine[pos + 1]])) >= bars["neighbor"]
            if ok:
                accepted.add(k)
                if idea == "base": confirmed.append(k)
        for i in order[step:]:
            pass
        gain[idea][room][0] += len(accepted); gain[idea][room][1] += len(mine)
    # Strangers under the full rule (every idea at once), in this owner's session.
    for k in others:
        hit = bool(BASE[idx, k].any())
        if confirmed:
            sims = FULL[confirmed] @ FULL[k]
            hit |= sims.min() >= bars["carry"] and float(unit(FULL[confirmed].mean(axis=0)) @ FULL[k]) >= bars["carry"] and float(quiet @ FULL[k]) >= .40
            hit |= crit(unit(.7 * quiet + .3 * unit(FULL[confirmed].mean(axis=0))), k) >= bars["adapt"]
        if qb is not None and names[k] in index_b:
            hit |= (crit(quiet, k) + crit(qb, index_b[names[k]], B)) / 2 >= bars["fusion"]
        if len(confirmed) >= 2:
            hit |= any(min(float(FULL[k] @ FULL[x]), float(FULL[k] @ FULL[y])) >= bars["neighbor"] for x, y in zip(confirmed, confirmed[1:]))
        strangers += hit

for idea in order:
    row = gain[idea]
    real = [r for r in rooms if r != "echo"]
    total = sum(row[r][0] for r in real) / max(1, sum(row[r][1] for r in real))
    print(f"+ {idea:8s} owner {100 * total:5.1f}% | " + " ".join(f"{r} {100 * row[r][0] / max(1, row[r][1]):.0f}%" for r in rooms))
print(f"strangers accepted under the full rule: {strangers} (stranger turns checked: {sum(len(s[6]) for s in sessions)})")
