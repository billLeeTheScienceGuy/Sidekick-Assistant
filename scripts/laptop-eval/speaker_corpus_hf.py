"""Sixth corpus: LibriSpeech train-clean-100 (251 people, CC BY 4.0) from the openslr/librispeech_asr parquet files on
Hugging Face (fast mirror of openslr.org). Per person 8 readings, clean and in the shared noisy room, like corpus4.

Usage: python -I speaker_corpus_hf.py <deps dir> <parquet dir> <out dir>
"""
import io
import sys
from collections import defaultdict
from pathlib import Path

here = Path(__file__).resolve().parent
sys.path.insert(0, str(here))
sys.path.insert(0, str(Path(sys.argv[1]) / "pyarrow-tools"))
import speaker_corpus as base  # noqa: E402  (room conditions; puts audio-tools on the path)
import numpy as np  # noqa: E402
import pyarrow.parquet as pq  # noqa: E402
import soundfile as sf  # noqa: E402


def main():
    deps, parquet_dir, out = Path(sys.argv[1]), Path(sys.argv[2]), Path(sys.argv[3])
    out.mkdir(parents=True, exist_ok=True)
    taken = defaultdict(int); rows = []
    for f in sorted(parquet_dir.glob("*.parquet")):
        table = pq.read_table(f, columns=["audio", "speaker_id", "id"])
        for audio, spk, uid in zip(table.column("audio").to_pylist(), table.column("speaker_id").to_pylist(), table.column("id").to_pylist()):
            if taken[spk] >= 8: continue
            taken[spk] += 1
            data, rate = sf.read(io.BytesIO(audio["bytes"]), dtype="float32", always_2d=True)
            data = data.mean(axis=1)
            assert rate == 16000
            for kind, group in (("clean", "librispeech-train-clean-100"), ("noisy", "librispeech-noisy6")):
                name = f"ls-{'train100' if kind == 'clean' else 'noisy6'}-{uid}.wav"
                if not (out / name).exists():
                    sf.write(str(out / name), base.condition(np.clip(data, -1, 1), kind), 16000, subtype="PCM_16")
                rows.append((name, f"ls{spk}", group))
    with open(out / "manifest.tsv", "w", encoding="utf-8") as fh:
        for r in rows: fh.write("\t".join(r) + "\n")
    print(f"clips={len(rows)} people={len(taken)}")


if __name__ == "__main__":
    main()
