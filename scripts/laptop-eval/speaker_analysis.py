"""Owner-matching analysis on laptop embeddings (SpeakerEmbed.java output). Mirrors VoiceMatch / OwnerSession. Vectorized.

Every person with enough clips is enrolled like VoiceProfile.save (3 samples, pairwise >= .6, averaged):
  single  one profile from quiet clips (today's setup)
  multi   one profile per room the person was recorded in (setup in several conditions)
Each turn (one clip = one captured chunk) is scored against every enrolled person:
  genuine   the person's own other clips (readings never used for enrollment), in every room
  stranger  every clip of every other person, in every room, plus stranger-only sets (FLEURS, 6 languages)
The stranger cohort (AS-norm) comes from people who are never evaluated.

Rooms: clean | noisy (shared pink noise, 10 dB) | far (realistic: about 2-3 m, RT60 .4 s, direct +3 dB) |
echo (stress: very strong reverberation, direct sound 27 dB below the echo; reported, not a tuning target).

Rule family (VoiceMatch.classifyScores): both = t, balancedMin = t - .05, balancedMean = t + .02, consistency .65;
optional: every window's AS-norm score >= z. Session carry (OwnerSession): whole-speech signature vs one confirmed owner
turn from the same room >= s, and profile score >= floor; long chunks and 2.5 s short phrases.

python -I speaker_analysis.py <out.json> [--cohort-out cohort.bin] <corpus dir> <embeddings.bin> [<corpus dir> <embeddings.bin> ...]
"""
import json
import struct
import sys
from collections import defaultdict
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[2] / ".deps" / "audio-tools"))
import numpy as np  # noqa: E402

argv = sys.argv[1:]
out = Path(argv.pop(0))
cohort_out = None; center = False
while argv and argv[0].startswith("--"):
    flag = argv.pop(0)
    if flag == "--cohort-out": cohort_out = argv.pop(0)
    elif flag == "--center": center = True
pairs = [(Path(argv[k]), Path(argv[k + 1])) for k in range(0, len(argv), 2)]

name, speaker, group = [], [], []
full, short, windows = {}, {}, defaultdict(list)
for corpus, binary in pairs:
    base = len(name)
    for line in open(corpus / "manifest.tsv", encoding="utf-8"):
        if line.strip():
            n, s, g = line.rstrip("\n").split("\t"); name.append(n); speaker.append(s); group.append(g)
    data = binary.read_bytes(); at = 0
    while at < len(data):
        clip = base + int.from_bytes(data[at:at + 4], "little"); kind = data[at + 4]; dim = int.from_bytes(data[at + 5:at + 7], "little")
        at += 7
        vec = np.frombuffer(data, dtype="<f4", count=dim, offset=at).copy() if dim else None
        at += dim * 4
        if kind == 0: full[clip] = vec
        elif kind == 1: windows[clip].append(vec)
        else: short[clip] = vec
N = len(name)


def room(g):
    for key in ("replay", "noisy", "echo", "far"):
        if key in g: return key
    return "clean"


condition = [room(g) for g in group]
# The first room build called the stress reverberation "far"; it is the echo room now.
condition = ["echo" if group[i] == "librispeech-far" else condition[i] for i in range(N)]
utterance = ["-".join(n[:-4].split("-")[-3:]) if n.removeprefix("replay-").startswith("ls-") else n.removeprefix("replay-") for n in name]
language = [g.split("-")[1] if g.startswith("fleurs-") else ("en" if speaker[i].startswith("ls") else
            speaker[i].split("-")[1] if speaker[i].startswith("cs-") else "owner") for i, g in enumerate(group)]
stranger_only = np.array([g.startswith("fleurs") for g in group])

cohort_ids = [i for i in range(N) if group[i] == "librispeech-test-other" and full.get(i) is not None]
per = defaultdict(list)
for i in cohort_ids:
    if len(per[speaker[i]]) < 6: per[speaker[i]].append(i)
cohort = np.stack([full[i] for ids in per.values() for i in ids]).astype(np.float32)
if center:
    # Centering (as WeSpeaker scores): subtract the strangers' average signature, then renormalize every signature.
    mean_voice = cohort.mean(axis=0)
    def centred(v):
        if v is None: return None
        c = v - mean_voice; return (c / np.linalg.norm(c)).astype(np.float32)
    full = {k: centred(v) for k, v in full.items()}; short = {k: centred(v) for k, v in short.items()}
    windows = defaultdict(list, {k: [centred(w) for w in ws] for k, ws in windows.items()})
    cohort = np.stack([centred(v) for v in cohort])
if cohort_out:
    with open(cohort_out, "wb") as f:
        f.write(struct.pack("<ii", *cohort.shape)); f.write(cohort.astype("<f4").tobytes())
        if center: np.save(cohort_out + ".mean.npy", mean_voice)
cohort_speakers = set(per)
eval_ids = [i for i in range(N) if speaker[i] not in cohort_speakers and full.get(i) is not None]


def unit(v):
    return v / np.linalg.norm(v)


def stats(m):
    top = np.sort(m @ cohort.T, axis=1)[:, -50:]
    return top.mean(axis=1), top.std(axis=1) + 1e-3


def usable(i):
    return full.get(i) is not None and len(windows[i]) >= 2 and all(w is not None for w in windows[i])


by_speaker = defaultdict(list)
for i in eval_ids:
    by_speaker[speaker[i]].append(i)

profiles, profile_person, profile_room, people_used, single_index = [], [], [], {}, {}
for spk, ids in by_speaker.items():
    if stranger_only[ids[0]]: continue
    used = set(); mine = []
    for cond in ("clean", "noisy", "far", "echo"):  # never enroll from the replay recording
        cand = [i for i in ids if condition[i] == cond and usable(i) and utterance[i] not in used]
        for start in range(0, max(0, len(cand) - 2)):
            pick = cand[start:start + 3]; vecs = [full[i] for i in pick]
            if all(float(a @ b) >= .6 for a in vecs for b in vecs):
                mine.append((cond, unit(np.mean(vecs, axis=0)))); used |= {utterance[i] for i in pick}; break
    if mine and mine[0][0] == "clean":
        people_used[spk] = used
        for cond, p in mine:
            if cond == "clean": single_index[spk] = len(profiles)
            profiles.append(p); profile_person.append(spk); profile_room.append(cond)
P = np.stack(profiles).astype(np.float32)
people = sorted(people_used)

# Turns: clips with >= 2 windows, none refused (the app returns UNKNOWN otherwise).
turns = [i for i in eval_ids if usable(i)]
W = np.stack([w for i in turns for w in windows[i]]).astype(np.float32)
counts = np.array([len(windows[i]) for i in turns]); starts = np.concatenate([[0], np.cumsum(counts)[:-1]])
pair_ok = np.ones(len(W) - 1, bool); pair_ok[(starts + counts - 1)[:-1]] = False  # no pair across clips
cons = np.einsum("ij,ij->i", W[:-1], W[1:])
S = P @ W.T                                           # profiles x windows
pm, ps = stats(P); wm, ws = stats(W)
Z = .5 * ((S - pm[:, None]) / ps[:, None] + (S - wm[None, :]) / ws[None, :])
lo = np.minimum(S[:, :-1], S[:, 1:]); mean = (S[:, :-1] + S[:, 1:]) / 2
pair_val = np.maximum(lo, np.minimum(lo + .05, mean - .02))
pair_val = np.where((cons >= .65)[None, :], pair_val, -np.inf)
pair_val = np.where(pair_ok[None, :], pair_val, np.inf)     # cross-clip pairs never decide
CRIT = np.minimum.reduceat(pair_val, starts, axis=1)       # profiles x turns
ZMIN = np.minimum.reduceat(Z, starts, axis=1)

turn_spk = np.array([speaker[i] for i in turns]); turn_cond = np.array([condition[i] for i in turns])
turn_lang = np.array([language[i] for i in turns]); turn_utt = [utterance[i] for i in turns]
person_of = np.array(profile_person)

def masks(person):
    """Genuine: own clips not used for enrollment. Stranger: everyone else's."""
    used = people_used[person]
    gen = (turn_spk == person) & np.array([u not in used for u in turn_utt])
    return gen, turn_spk != person

T_GRID = [round(float(x), 2) for x in np.arange(.40, .901, .01)]
Z_GRID = [None, 1.0, 2.0, 2.5, 3.0, 3.5, 4.0, 4.5, 5.0]
CONDS = ("clean", "noisy", "far", "echo", "replay")
results = {}
for mode in ("single", "multi"):
    rows = []  # per person: (gen mask, str mask, profile indices)
    for spk in people:
        idx = [single_index[spk]] if mode == "single" else list(np.where(person_of == spk)[0])
        g, s = masks(spk); rows.append((g, s, idx))
    grid = {}
    for t in T_GRID:
        for z in Z_GRID:
            ok = CRIT >= t
            if z is not None: ok = ok & (ZMIN >= z)
            gen_hit = defaultdict(lambda: [0, 0]); str_hit = defaultdict(int); str_lang = defaultdict(int)
            for g, s, idx in rows:
                acc = ok[idx].any(axis=0)
                for c in CONDS:
                    m = g & (turn_cond == c); gen_hit[c][0] += int(acc[m].sum()); gen_hit[c][1] += int(m.sum())
                    str_hit[c] += int((acc & s & (turn_cond == c)).sum())
                for lang in set(turn_lang[s & acc]):
                    str_lang[lang] += int((acc & s & (turn_lang == lang)).sum())
            key = f"t{t:.2f}" + ("" if z is None else f"_z{z:.1f}")
            real = [c for c in CONDS if c != "echo"]
            grid[key] = {
                "owner": sum(gen_hit[c][0] for c in real) / max(1, sum(gen_hit[c][1] for c in real)),
                "owner_by_room": {c: round(gen_hit[c][0] / max(1, gen_hit[c][1]), 3) for c in CONDS},
                "strangers_by_room": dict(str_hit), "strangers_by_language": dict(str_lang),
                "strangers": sum(str_hit[c] for c in real), "strangers_echo": str_hit["echo"]}
    # Closest stranger per room, as the raw-rule value (accepted iff t <= value).
    closest = {}
    for c in CONDS:
        vals = []
        for g, s, idx in rows:
            m = s & (turn_cond == c)
            if m.any(): vals.append(float(CRIT[idx][:, m].max()))
        closest[c] = max(vals) if vals else None
    results[mode] = {"grid": grid, "closest_stranger_raw": closest,
                     "genuine_turns": int(sum(r[0].sum() for r in rows)), "stranger_turns": int(sum(r[1].sum() for r in rows))}

# Session carry: one confirmed owner turn as anchor, strangers in the same room.
session = {}
for kind, store in (("long", full), ("short", short)):
    gen, strg = [], []
    for spk in people:
        prof = P[single_index[spk]]
        for c in CONDS:
            mine = [i for i in by_speaker[spk] if condition[i] == c and utterance[i] not in people_used[spk] and usable(i)]
            if len(mine) < 2: continue
            anchor = full[mine[0]]
            g = [store[i] for i in mine[1:] if store.get(i) is not None]
            if g:
                m = np.stack(g); gen += [(a, p, c) for a, p in zip(m @ anchor, m @ prof)]
            others = [i for i in eval_ids if speaker[i] != spk and condition[i] == c and store.get(i) is not None]
            if others:
                m = np.stack([store[i] for i in others]); strg += [(a, p, c) for a, p in zip(m @ anchor, m @ prof)]
    rep = {"closest_stranger_by_room": {c: float(max([a for a, _, cc in strg if cc == c], default=-1)) for c in CONDS}}
    for s_t in np.arange(.60, .961, .02):
        for floor in (0.0, .30, .40, .50):
            g_ok = [(a >= s_t and p >= floor, c) for a, p, c in gen]
            rep[f"s{s_t:.2f}_floor{floor:.2f}"] = {
                "owner_by_room": {c: round(float(np.mean([o for o, cc in g_ok if cc == c])), 3) for c in CONDS if any(cc == c for _, cc in g_ok)},
                "strangers_by_room": {c: int(sum(1 for a, p, cc in strg if cc == c and a >= s_t and p >= floor)) for c in CONDS}}
    rep["genuine"] = len(gen); rep["stranger"] = len(strg)
    session[kind] = rep

owner_turns = {c: float(np.mean(CRIT[single_index["OWNER"]][(turn_spk == "OWNER") & np.array([u not in people_used["OWNER"] for u in turn_utt])] >= .70))
               for c in ("clean",)} if "OWNER" in single_index else None
report = {"people_enrolled": len(people), "profiles": len(profiles), "cohort_people": len(per),
          "stranger_voices": len(set(turn_spk)), "turns": len(turns), "results": results, "session": session}
out.write_text(json.dumps(report, indent=1, default=float))


def best(mode, include_echo=False):
    safe = [(v["owner"], k) for k, v in results[mode]["grid"].items()
            if v["strangers"] == 0 and (not include_echo or v["strangers_echo"] == 0)]
    return max(safe) if safe else None


g = results["single"]["grid"]
print(json.dumps({
    "people_enrolled": len(people), "stranger_voices_total": len(set(turn_spk)), "turns": len(turns),
    "current_standard": {k: g["t0.70"][k] for k in ("owner", "owner_by_room", "strangers_by_room")},
    "current_before_notice": {k: g["t0.75"][k] for k in ("owner", "owner_by_room", "strangers_by_room")},
    "closest_stranger_single": results["single"]["closest_stranger_raw"],
    "closest_stranger_multi": results["multi"]["closest_stranger_raw"],
    "best_zero_stranger_single": best("single"), "best_zero_stranger_multi": best("multi"),
    "session_long_closest": session["long"]["closest_stranger_by_room"],
    "session_short_closest": session["short"]["closest_stranger_by_room"],
}, indent=1, default=float))
