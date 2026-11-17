"""Simulates the full owner rule on one model's embeddings, turn by turn within each room ("session"):

  1. profile: quiet-room profile with bar Q, other-room profiles with bar R (VoiceMatch rule family), cohort z guard
  2. session carry: after the first profile-confirmed turn in the room, a turn whose whole-speech signature matches
     every confirmed turn >= S (and their mean), with profile score >= .40, counts as the owner

Strangers: every other person's turns in the same room, checked against the owner's profiles and against the owner's
confirmed session turns (as if the stranger spoke in the owner's session).

python -I speaker_final_rule.py <Q> <R> <S> <corpus> <bin> [<corpus> <bin> ...]
"""
import sys
from pathlib import Path

Q, R, S = (float(x) for x in sys.argv[1:4])
here = Path(__file__).resolve().parent
src = (here / "speaker_analysis.py").read_text(encoding="utf-8").split("T_GRID =")[0]
sys.argv = ["x", "NUL"] + sys.argv[4:]
g = {"__file__": str(here / "speaker_analysis.py"), "__name__": "x"}
exec(src, g)
np = g["np"]
CRIT, ZMIN, turns, turn_spk, turn_cond = g["CRIT"], g["ZMIN"], g["turns"], g["turn_spk"], g["turn_cond"]
full, P, person_of, profile_room = g["full"], g["P"], g["person_of"], np.array(g["profile_room"])
bar = np.where(profile_room == "clean", Q, R)
accept = ((CRIT >= bar[:, None]) & (ZMIN >= 2.0))          # profiles x turns
turn_full = np.stack([full[i] for i in turns])
rooms = ("clean", "noisy", "far", "echo", "replay")
stats = {r: [0, 0, 0, 0, 0] for r in rooms}  # genuine total, by profile, by carry, stranger turns, stranger accepted
worst = 0.0
for spk in g["people"]:
    idx = np.where(person_of == spk)[0]
    used = g["people_used"][spk]
    single = g["single_index"][spk]
    for room in rooms:
        mine = [k for k, i in enumerate(turns) if turn_spk[k] == spk and turn_cond[k] == room and g["utterance"][i] not in used]
        if not mine: continue
        confirmed = []
        for k in mine:
            stats[room][0] += 1
            if accept[idx, k].any():
                stats[room][1] += 1; confirmed.append(turn_full[k]); continue
            if confirmed:
                sims = [float(c @ turn_full[k]) for c in confirmed]
                centre = np.mean(confirmed, axis=0); centre /= np.linalg.norm(centre)
                if min(sims) >= S and float(centre @ turn_full[k]) >= S and float(P[single] @ turn_full[k]) >= .40:
                    stats[room][2] += 1
        others = [k for k in range(len(turns)) if turn_spk[k] != spk and turn_cond[k] == room]
        stats[room][3] += len(others)
        for k in others:
            hit = bool(accept[idx, k].any())
            if not hit and confirmed:
                sims = [float(c @ turn_full[k]) for c in confirmed]
                worst = max(worst, min(sims))
                centre = np.mean(confirmed, axis=0); centre /= np.linalg.norm(centre)
                hit = min(sims) >= S and float(centre @ turn_full[k]) >= S and float(P[single] @ turn_full[k]) >= .40
            stats[room][4] += hit
print(f"rule: quiet profile >= {Q}, other-room profiles >= {R}, session >= {S}")
for room, (n, prof, carry, sn, sa) in stats.items():
    if n: print(f"  {room:7s} owner {100 * (prof + carry) / n:5.1f}% (profile {100 * prof / n:5.1f}%, + session {100 * carry / n:5.1f}%) of {n} turns | strangers accepted {sa} of {sn}")
n = sum(v[0] for k, v in stats.items() if k != "echo"); ok = sum(v[1] + v[2] for k, v in stats.items() if k != "echo")
print(f"  overall (quiet+noisy+far{'+replay' if stats['replay'][0] else ''}): owner {100 * ok / max(1, n):.1f}% | strangers accepted {sum(v[4] for v in stats.values())} | closest stranger to a session voice {worst:.3f}")
