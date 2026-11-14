"""Builds core/src/main/resources/us-state-borders.txt from the Census cartographic boundary file
(cb_<year>_us_state_20m, public domain). Each state: postal code, name, recording-consent rule, outer rings
as lon,lat,lon,lat,... rounded to 4 decimals (about 10 m; the source is generalised anyway).
Line format (no JSON parser needed in core): CODE|Name|rule|ring;ring;...

Consent rules (secondary sources, see the SideKick consent document; not legal advice):
  all   = every party's consent for private conversations
  mixed = all-party for some kinds of conversation; SideKick treats it as all
  one   = one party's consent
"""
import json, sys
import shapefile  # pyshp

ALL = {"CA", "FL", "IL", "MD", "MA", "MT", "NH", "PA", "WA"}
MIXED = {"CT", "DE", "MI", "NV", "OR", "VT"}

src, out = sys.argv[1], sys.argv[2]
reader = shapefile.Reader(src)
fields = [f[0] for f in reader.fields[1:]]
states = []
for shape_record in reader.iterShapeRecords():
    rec = dict(zip(fields, shape_record.record))
    code = rec["STUSPS"]
    shape = shape_record.shape
    parts = list(shape.parts) + [len(shape.points)]
    rings = []
    for a, b in zip(parts, parts[1:]):
        ring = shape.points[a:b]
        flat = [round(c, 4) for point in ring for c in point]
        if len(ring) >= 4:
            rings.append(flat)
    states.append({"code": code, "name": rec["NAME"], "rule": "all" if code in ALL else "mixed" if code in MIXED else "one", "rings": rings})
states.sort(key=lambda s: s["code"])
with open(out, "w", encoding="utf-8") as f:
    f.write("# US Census Bureau cartographic boundary file, 1:20,000,000, public domain. CODE|Name|rule|rings\n")
    for s in states:
        f.write("|".join([s["code"], s["name"], s["rule"], ";".join(",".join(f"{v:g}" for v in r) for r in s["rings"])]) + "\n")
print(len(states), "states,", sum(len(s["rings"]) for s in states), "rings")
