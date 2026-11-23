"""Builds core/src/main/resources/us-state-borders.txt from a Census cartographic boundary file
(cb_2023_us_state_500k, public domain: https://www2.census.gov/geo/tiger/GENZ2023/shp/cb_2023_us_state_500k.zip).

Each ring is simplified with Douglas-Peucker at about 100 m and rounded to 4 decimals (about 10 m). Rings whose
area is below ~0.25 km^2 are dropped (tiny islets); a point there reads as "outside the US", which is the strictest rule.
RecordingJurisdiction adds a 500 m border-data margin for this file.

Line format (no JSON parser needed in core): CODE|Name|recording|biometric|ring;ring;...
  recording: one | mixed | all   (mixed = all-party for some conversations; SideKick treats it as all)
  biometric: none | notice | strict
Rules come from public secondary sources (see docs/PRIVACY_POLICY.md); not legal advice. Add a state by editing
the sets below and re-running:
  python scripts/build_state_borders.py path/to/cb_2023_us_state_500k.shp core/src/main/resources/us-state-borders.txt
"""
import math
import sys

import shapefile  # pyshp

RECORDING_ALL = {"CA", "FL", "IL", "MD", "MA", "MT", "NH", "PA", "WA"}
RECORDING_MIXED = {"CT", "DE", "MI", "NV", "OR", "VT"}
BIOMETRIC_STRICT = {"IL"}
BIOMETRIC_NOTICE = {"TX", "WA"}
TOLERANCE_M = 100.0
MIN_AREA_M2 = 250_000.0


def project(ring):
    lat0 = sum(p[1] for p in ring) / len(ring)
    kx = 111_320.0 * math.cos(math.radians(lat0))
    return [(x * kx, y * 111_320.0) for x, y in ring]


def area(points):
    return abs(sum(a[0] * b[1] - b[0] * a[1] for a, b in zip(points, points[1:] + points[:1]))) / 2


def simplify(ring):
    """Iterative Douglas-Peucker on locally projected metres; keeps the closing point."""
    xy = project(ring)
    keep = [False] * len(ring)
    keep[0] = keep[-1] = True
    stack = [(0, len(ring) - 1)]
    while stack:
        a, b = stack.pop()
        ax, ay = xy[a]; bx, by = xy[b]
        dx, dy = bx - ax, by - ay
        length = dx * dx + dy * dy
        best, index = -1.0, -1
        for i in range(a + 1, b):
            px, py = xy[i]
            if length == 0:
                d = math.hypot(px - ax, py - ay)
            else:
                t = max(0.0, min(1.0, ((px - ax) * dx + (py - ay) * dy) / length))
                d = math.hypot(px - ax - t * dx, py - ay - t * dy)
            if d > best:
                best, index = d, i
        if best > TOLERANCE_M:
            keep[index] = True
            stack += [(a, index), (index, b)]
    return [p for p, k in zip(ring, keep) if k]


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
        if len(ring) < 4 or area(project(ring)) < MIN_AREA_M2:
            continue
        ring = simplify(ring)
        if len(ring) >= 4:
            rings.append([round(c, 4) for point in ring for c in point])
    recording = "all" if code in RECORDING_ALL else "mixed" if code in RECORDING_MIXED else "one"
    biometric = "strict" if code in BIOMETRIC_STRICT else "notice" if code in BIOMETRIC_NOTICE else "none"
    states.append({"code": code, "name": rec["NAME"], "recording": recording, "biometric": biometric, "rings": rings})
states.sort(key=lambda s: s["code"])
with open(out, "w", encoding="utf-8", newline="\n") as f:
    f.write("# US Census Bureau cartographic boundary file cb_2023_us_state_500k (1:500,000), public domain, "
            "simplified ~100 m. CODE|Name|recording|biometric|rings\n")
    for s in states:
        f.write("|".join([s["code"], s["name"], s["recording"], s["biometric"],
                          ";".join(",".join(f"{v:g}" for v in r) for r in s["rings"])]) + "\n")
print(len(states), "states,", sum(len(s["rings"]) for s in states), "rings,",
      sum(len(r) // 2 for s in states for r in s["rings"]), "points")
