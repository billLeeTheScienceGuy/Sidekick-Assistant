"""Summarize ModelBench output (scripts/laptop-eval/ModelBench.java, one <model>.json per model in .deps/bench/)
into committed evidence for the presentation numbers:

  docs/evaluation/ENGLISH_BENCHMARK.md   English character error, Parakeet TDT 110M vs SenseVoice, by set
  docs/evaluation/MODEL_BENCH_SUMMARY.md models, clip counts by language and set

Metric (same as ModelBench / finalize.ps1): character edits / reference characters, lowercase, no spaces or punctuation.
Natural sets ("passages", "mixed") count only clips whose caption some model matched within 60%, as in REPORT.md.
Only scores and counts are written; never transcripts.

  python scripts/summarize_model_bench.py [.deps/bench]
"""
import collections
import glob
import json
import os
import sys
from datetime import datetime

root = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
bench = sys.argv[1] if len(sys.argv) > 1 else os.path.join(root, ".deps", "bench")
rows = []
for path in sorted(glob.glob(os.path.join(bench, "*.json"))):
    name = os.path.splitext(os.path.basename(path))[0]
    if name.endswith("all"):  # parakeet110all.json repeats parakeet110 over every language set; not a separate model
        continue
    with open(path, encoding="utf-8") as f:
        rows += json.load(f)
if not rows:
    sys.exit(f"No ModelBench results in {bench}")

def cer(rs):
    edits = sum(r["characterEdits"] for r in rs)
    chars = sum(r["referenceCharacters"] for r in rs)
    return 100.0 * edits / max(1, chars)

scored_rows = [r for r in rows if r["mode"] != "lid"]
best = collections.defaultdict(lambda: 9.9)
for r in scored_rows:
    best[r["file"]] = min(best[r["file"]], r["characterEdits"] / max(1, r["referenceCharacters"]))
scored = [r for r in scored_rows if r["set"] not in ("passages", "mixed") or best[r["file"]] < 0.6]
models = sorted({r["mode"] for r in scored})
stamp = datetime.now().strftime("%Y-%m-%d")

# ---------- English ----------
CONDITION = {"fleurs": "clean read speech (LibriSpeech/FLEURS-style clips)", "scenarios": "noisy or far-field scenario clips",
             "passages": "natural long passages (YouTube captions)", "wav": "app WAV fixtures"}
en = [r for r in scored if r["language"] == "en"]
lines = ["# English benchmark (ModelBench)", "",
         f"Generated {stamp} by `scripts/summarize_model_bench.py` from the ModelBench run in `.deps/bench/` (results dated 2026-11-14, laptop, sherpa-onnx JVM).",
         "Metric: **character error rate** = character edits / reference characters (lowercase, no spaces or punctuation). Lower is better.",
         "The number in brackets is the clip count.", "",
         "| Set | Condition | Parakeet TDT 110M (`parakeet110`) | SenseVoice 2024 (`sense2024`) |", "| --- | --- | --- | --- |"]
for s in ["fleurs", "scenarios", "passages", "wav"]:
    p = [r for r in en if r["set"] == s and r["mode"] == "parakeet110"]
    v = [r for r in en if r["set"] == s and r["mode"] == "sense2024"]
    if p or v:
        lines.append(f"| {s} | {CONDITION.get(s, s)} | {cer(p):.1f}% ({len(p)}) | {cer(v):.1f}% ({len(v)}) |")
lines += ["", "All English models on the same clips:", "", "| Model | " + " | ".join(["fleurs", "scenarios", "passages"]) + " |", "| --- | --- | --- | --- |"]
for m in models:
    cells = []
    for s in ["fleurs", "scenarios", "passages"]:
        g = [r for r in en if r["set"] == s and r["mode"] == m]
        cells.append(f"{cer(g):.1f}% ({len(g)})" if g else "")
    if any(cells):
        lines.append(f"| {m} | " + " | ".join(cells) + " |")
lines += ["", "## Slide wording", "",
          "\"English (Parakeet pack): 0.5% of letters wrong in quiet, 2% noisy or far\" matches the `fleurs` and `scenarios` rows above. "
          "Say \"letters\" (character error), not \"words\". The long natural `passages` set is much harder (see table); do not quote 0.5% for it.", ""]
with open(os.path.join(root, "docs", "evaluation", "ENGLISH_BENCHMARK.md"), "w", encoding="utf-8", newline="\n") as f:
    f.write("\n".join(lines))

# ---------- all models ----------
all_models = sorted({r["mode"] for r in rows})
clips = {(r["set"], r["language"], r["file"]) for r in rows if r["mode"] != "lid"}
by_lang = collections.Counter(lang for _, lang, _ in clips)
by_set = collections.Counter(s for s, _, _ in clips)
lines = ["# Model benchmark summary (ModelBench)", "",
         f"Generated {stamp} by `scripts/summarize_model_bench.py` from `.deps/bench/` (laptop run, results dated 2026-11-14).", "",
         f"- **Models compared:** {len(all_models)} ({', '.join(all_models)}; `lid` is the Whisper-tiny language identifier).",
         f"- **Distinct test clips:** {len(clips)} (each model runs only on the languages it supports).",
         f"- **Scored model-clip results:** {len(scored_rows)} (before the natural-caption filter), {len(scored)} after it.", "",
         "| Language | Clips |", "| --- | --- |"] + [f"| {k} | {v} |" for k, v in sorted(by_lang.items())] + ["",
         "| Set | Clips |", "| --- | --- |"] + [f"| {k} | {v} |" for k, v in sorted(by_set.items())] + ["",
         "README wording \"benchmarking 16 models on about 1,380 clips\": 16 = the model list above (15 recognizers + the language identifier); "
         "the clip total is the distinct-clip count in this table. Update the README if the numbers here differ.", ""]
with open(os.path.join(root, "docs", "evaluation", "MODEL_BENCH_SUMMARY.md"), "w", encoding="utf-8", newline="\n") as f:
    f.write("\n".join(lines))
print(f"{len(all_models)} models, {len(clips)} distinct clips; wrote ENGLISH_BENCHMARK.md and MODEL_BENCH_SUMMARY.md")
