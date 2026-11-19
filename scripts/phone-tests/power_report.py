"""Average power per rail (milliwatts) from the Perfetto traces written by power-rails.sh.
Uses the middle of each trace (after the 25 s warm-up with the screen on, before stopping), where only listening runs.
Usage: python -I power_report.py <folder with power-*.pftrace> <deps dir>
"""
import sys
from pathlib import Path

sys.path.insert(0, str(Path(sys.argv[2]) / "perfetto-tools"))
from perfetto.trace_processor import TraceProcessor  # noqa: E402


def main():
    sys.stdout.reconfigure(encoding="utf-8")
    rows = {}
    for trace in sorted(Path(sys.argv[1]).glob("power-*.pftrace")):
        tp = TraceProcessor(trace=str(trace))
        q = [(r.rail, r.ts, r.uws) for r in tp.query("""
            select t.name as rail, c.ts as ts, c.value as uws
            from counter c join counter_track t on c.track_id = t.id
            where t.name like 'power.%' order by t.name, c.ts""")]
        tp.close()
        if not q:
            print(trace.name, "no power rail data"); continue
        start, end = min(x[1] for x in q), max(x[1] for x in q)
        lo, hi = start + 45e9, end - 75e9   # skip warm-up (screen on) and the stop/idle tail
        out = {}
        by = {}
        for rail, ts, uws in q:
            if lo <= ts <= hi: by.setdefault(rail, []).append((ts, uws))
        for rail, g in by.items():
            if len(g) < 2: continue
            out[rail.replace("power.rails.", "").replace("power.", "")] = (g[-1][1] - g[0][1]) / ((g[-1][0] - g[0][0]) / 1e9) / 1000
        rows[trace.stem.replace("power-", "")] = out
    rails = sorted({r for v in rows.values() for r in v})
    print(f"{'rail (mW)':28s}" + "".join(f"{m:>10s}" for m in rows))
    for r in rails:
        print(f"{r:28s}" + "".join(f"{rows[m].get(r, float('nan')):10.1f}" for m in rows))
    print(f"{'TOTAL of rails':28s}" + "".join(f"{sum(rows[m].values()):10.1f}" for m in rows))


if __name__ == "__main__":
    main()
