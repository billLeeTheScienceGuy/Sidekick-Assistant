"""Quality-aware bars: one owner bar per chunk-quality group (speech length x signal-to-noise ratio) instead of one bar
for every chunk. Each group's bar = MARGIN above the closest stranger in that group, never below FLOOR, and a group
with fewer than MIN_STRANGERS stranger turns keeps the single global bar (too little evidence to lower it).

Uses the quiet-room profile (the setup most people do). Prints the table of bars and owner recognition with the global
bar vs quality-aware bars; strangers accepted must stay 0.

python -I speaker_quality.py <global bar> <corpus> <bin> [<corpus> <bin> ...]
"""
import json
import sys
from pathlib import Path

GLOBAL = float(sys.argv[1])
MARGIN, FLOOR, MIN_STRANGERS = .08, .55, 20_000
LENGTHS = [(0, 5.0, "under 5 s"), (5.0, 8.0, "5-8 s"), (8.0, float("inf"), "8 s or more")]
SNRS = [(-float("inf"), 15.0, "noisy (<15 dB)"), (15.0, 25.0, "some noise (15-25 dB)"), (25.0, float("inf"), "clean (25+ dB)")]
here = Path(__file__).resolve().parent
src = (here / "speaker_analysis.py").read_text(encoding="utf-8").split("T_GRID =")[0]
sys.argv = ["x", "NUL"] + sys.argv[2:]
g = {"__file__": str(here / "speaker_analysis.py"), "__name__": "x"}
exec(src, g)
np = g["np"]
turns, CRIT, ZMIN = g["turns"], g["CRIT"], g["ZMIN"]
spk, cond = g["turn_spk"], g["turn_cond"]
q = np.array([g["quality"].get(i, np.array([0, 0], dtype=np.float32)) for i in turns])
seconds, snr = q[:, 0], q[:, 1]


def group(k):
    li = next(n for n, (a, b, _) in enumerate(LENGTHS) if a <= seconds[k] < b)
    si = next(n for n, (a, b, _) in enumerate(SNRS) if a <= snr[k] < b)
    return li, si


groups = np.array([group(k) for k in range(len(turns))])
gen_mask, str_rows = [], []
for person in g["people"]:
    p = g["single_index"][person]
    used = g["people_used"][person]
    gm = (spk == person) & np.array([g["utterance"][i] not in used for i in turns])
    gen_mask.append((p, gm)); str_rows.append((p, spk != person))

table = {}
for li in range(len(LENGTHS)):
    for si in range(len(SNRS)):
        sel = (groups[:, 0] == li) & (groups[:, 1] == si)
        s_vals = np.concatenate([CRIT[p][m & sel] for p, m in str_rows])
        g_vals = np.concatenate([CRIT[p][m & sel] for p, m in gen_mask])
        closest = float(s_vals.max()) if len(s_vals) else None
        bar = GLOBAL if len(s_vals) < MIN_STRANGERS or closest is None else max(FLOOR, round(closest + MARGIN + .005, 2))
        table[(li, si)] = dict(bar=bar, strangers=len(s_vals), closest=closest, owner_turns=len(g_vals),
                               owner_global=float(np.mean(g_vals >= GLOBAL)) if len(g_vals) else None,
                               owner_quality=float(np.mean(g_vals >= bar)) if len(g_vals) else None)

print(f"global bar {GLOBAL}; margin {MARGIN}; groups with fewer than {MIN_STRANGERS} stranger turns keep the global bar")
print(f"{'length':14s} {'noise':22s} {'strangers':>9s} {'closest':>8s} {'bar':>5s} {'owner turns':>11s} {'owner @global':>13s} {'owner @group':>12s}")
for (li, si), r in table.items():
    print(f"{LENGTHS[li][2]:14s} {SNRS[si][2]:22s} {r['strangers']:9d} {r['closest'] if r['closest'] is not None else float('nan'):8.3f} {r['bar']:5.2f} "
          f"{r['owner_turns']:11d} {100 * (r['owner_global'] or 0):12.1f}% {100 * (r['owner_quality'] or 0):11.1f}%")
bars = np.array([table[(a, b)]["bar"] for a, b in groups])
tot_g = sum(m.sum() for _, m in gen_mask)
own_global = sum((CRIT[p][m] >= GLOBAL).sum() for p, m in gen_mask) / tot_g
own_quality = sum(((CRIT[p] >= bars) & m).sum() for p, m in gen_mask) / tot_g
strangers = sum(((CRIT[p] >= bars) & m).sum() for p, m in str_rows)
print(f"overall owner: global {100 * own_global:.1f}% -> quality-aware {100 * own_quality:.1f}% | strangers accepted {strangers}")
Path(here.parents[1] / ".deps/speaker-public/eval/quality-bars.json").write_text(json.dumps(
    {f"{LENGTHS[a][2]}|{SNRS[b][2]}": v for (a, b), v in table.items()}, indent=1))
