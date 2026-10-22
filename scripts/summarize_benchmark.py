"""Summarize fixture-only JSONL or tagged adb logcat output. Python standard library."""
import collections
import json
import math
import statistics
import sys


def summarize(path):
    groups = collections.defaultdict(list)
    with open(path, encoding="utf-8-sig") as source:
        for line in source:
            if "{" not in line:
                continue
            row = json.loads(line[line.index("{"):])
            groups[(row.get("runId", "baseline"), row["audioSeconds"])].append(row)
    for (name, duration), rows in groups.items():
        times = sorted(row["elapsedMs"] for row in rows)
        result = {
            "run": name, "audioSeconds": duration, "count": len(rows),
            "medianMs": statistics.median(times),
            "p95Ms": times[math.ceil(len(times) * .95) - 1], "maxMs": max(times),
            "medianRTF": statistics.median(times) / (duration * 1000),
            "maxObservedPssKb": max(row.get("sampledPeakPssKb", row["pssKb"]) for row in rows),
            "maxBacklogMs": max(row.get("backlogMs", 0) for row in rows),
            "maxThermalStatus": max(row["thermalStatus"] for row in rows),
            "firstTenMedianMs": statistics.median(row["elapsedMs"] for row in rows[:10]),
            "lastTenMedianMs": statistics.median(row["elapsedMs"] for row in rows[-10:]),
        }
        print(json.dumps(result))


if __name__ == "__main__":
    summarize(sys.argv[1])
