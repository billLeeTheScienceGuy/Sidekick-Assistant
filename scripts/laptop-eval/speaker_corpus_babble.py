"""Third corpus: background talking ("babble"), the realistic noise a noise remover cannot take out.

The first 12 readings of every LibriSpeech test-clean person, each mixed at 5 dB with the same babble track: 6 people
talking at once, taken from LibriSpeech dev-like readers that are never evaluated (test-other readers beyond the 6
clips each that form the stranger cohort). One shared room, like a busy cafe.

Usage: python -I speaker_corpus_babble.py <deps dir> <corpus dir (first corpus)> <out dir>
"""
import sys
from collections import defaultdict
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[2] / ".deps" / "audio-tools"))
import numpy as np  # noqa: E402
import soundfile as sf  # noqa: E402


def babble(deps: Path) -> np.ndarray:
    root = deps / "speaker-public/data/LibriSpeech/test-other"
    people = sorted(p for p in root.iterdir() if p.is_dir())[:6]
    tracks = []
    for person in people:
        # Readings 20+ of each person: never part of the corpus (8 per person) or the cohort (first 6).
        files = sorted(person.rglob("*.flac"))[20:40]
        audio = np.concatenate([sf.read(str(f), dtype="float32")[0] for f in files])[: 16000 * 60]
        tracks.append(audio / (np.sqrt(np.mean(audio ** 2)) + 1e-9))
    n = min(len(t) for t in tracks)
    mix = np.sum([t[:n] for t in tracks], axis=0)
    return (mix / np.sqrt(np.mean(mix ** 2))).astype(np.float32)


def main():
    deps, first, out = Path(sys.argv[1]), Path(sys.argv[2]), Path(sys.argv[3])
    out.mkdir(parents=True, exist_ok=True)
    noise = babble(deps)
    taken = defaultdict(int); rows = []
    for line in open(first / "manifest.tsv", encoding="utf-8"):
        name, spk, group = line.rstrip("\n").split("\t")
        if group != "librispeech-test-clean": continue
        taken[spk] += 1
        if taken[spk] > 12: continue
        audio, _ = sf.read(str(first / name), dtype="float32")
        speech = np.sqrt(np.mean(audio ** 2)) + 1e-9
        mixed = audio + np.resize(noise, len(audio)) * speech / (10 ** (5 / 20))
        mixed *= min(1.0, .9 / (np.max(np.abs(mixed)) + 1e-9))
        target = name.replace("ls-test-clean-", "ls-babble-")
        sf.write(str(out / target), mixed.astype(np.float32), 16000, subtype="PCM_16")
        rows.append((target, spk, "librispeech-babble"))
    with open(out / "manifest.tsv", "w", encoding="utf-8") as f:
        for r in rows: f.write("\t".join(r) + "\n")
    print(f"clips={len(rows)}")


if __name__ == "__main__":
    main()
