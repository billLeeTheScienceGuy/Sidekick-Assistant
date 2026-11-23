"""Fair model comparison from speaker_analysis.py outputs: every model gets its own bar, set MARGIN above the closest
stranger it produced in any room (quiet, noisy, far, echo), then owner acceptance is compared at that bar.

python -I speaker_compare.py <eval dir>
"""
import json
import sys
from pathlib import Path

MARGIN = .08
SIZES = {"campplus-advanced": 28.3, "voxceleb_resnet34_LM": 26.5, "nemo_en_titanet_small": 40.3,
         "3dspeaker_speech_eres2net_sv_en_voxceleb_16k": 26.5, "3dspeaker_speech_eres2netv2_sv_zh-cn_16k-common": 71.4}
ROOMS = ("clean", "noisy", "far", "echo")
rows = []
for f in sorted(Path(sys.argv[1]).glob("*.json")):
    base = f.stem.replace("--center", "")
    if base not in SIZES: continue
    d = json.loads(f.read_text())
    line = {"model": base, "centered": "--center" in f.stem, "MB": SIZES[base], "stranger_voices": d.get("stranger_voices", None)}
    for mode in ("single", "multi"):
        r = d["results"][mode]
        closest = max(v for v in r["closest_stranger_raw"].values() if v is not None)
        bar = round(min(.90, closest + MARGIN) + .004, 2)
        key = f"t{bar:.2f}"
        g = r["grid"].get(key)
        line[mode] = {"closest_stranger": round(closest, 3), "bar": bar,
                      "owner": round(g["owner"], 3) if g else None,
                      "owner_by_room": g["owner_by_room"] if g else None,
                      "strangers_at_bar": (g["strangers"] + g["strangers_echo"]) if g else None}
    s = d["session"]["long"]; closest = max(v for v in s["closest_stranger_by_room"].values())
    st = round(min(.96, closest + MARGIN) + .009, 2)
    st = min((float(k[1:5]) for k in s if k.startswith("s0") and float(k[1:5]) >= st - 1e-9), default=None)
    key = f"s{st:.2f}_floor0.40" if st is not None else None
    line["session"] = {"closest_stranger": round(closest, 3), "bar": st, "owner_by_room": s[key]["owner_by_room"] if key in s else None}
    rows.append(line)
print(json.dumps(rows, indent=1))
