"""Fourth corpus: more public voices, no sign-up. LibriSpeech dev-clean, dev-other and train-clean-100 (CC BY 4.0):
about 324 more people, 8 readings each (3 for setup, 5 as turns), plus the same readings in the shared noisy room.

Usage: python -I speaker_corpus_more.py <deps dir> <out dir>
"""
import concurrent.futures as cf
import os
import sys
from pathlib import Path

here = Path(__file__).resolve().parent
sys.path.insert(0, str(here))
import speaker_corpus as base  # noqa: E402  (decode, room conditions, writer)


def main():
    deps, out = Path(sys.argv[1]), Path(sys.argv[2])
    out.mkdir(parents=True, exist_ok=True)
    libri = deps / "speaker-public/data/LibriSpeech"
    rows, jobs = [], []
    for split in ("dev-clean", "dev-other", "train-clean-100"):
        root = libri / split
        if not root.is_dir(): continue
        for person in sorted(p for p in root.iterdir() if p.is_dir()):
            flacs = sorted(person.rglob("*.flac"))[:8]
            for flac in flacs:
                name = f"ls-{split}-{flac.stem}.wav"
                rows.append((name, f"ls{person.name}", f"librispeech-{split}"))
                jobs.append((flac, out / name, "clean"))
                noisy = f"ls-noisy4-{flac.stem}.wav"
                rows.append((noisy, f"ls{person.name}", "librispeech-noisy4"))
                jobs.append((flac, out / noisy, "noisy"))
    with cf.ProcessPoolExecutor(max_workers=os.cpu_count()) as pool:
        list(pool.map(base.write, jobs, chunksize=16))
    with open(out / "manifest.tsv", "w", encoding="utf-8") as f:
        for r in rows: f.write("\t".join(r) + "\n")
    print(f"clips={len(rows)} people={len({r[1] for r in rows})}")


if __name__ == "__main__":
    main()
