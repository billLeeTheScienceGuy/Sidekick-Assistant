"""Second corpus for owner-matching evaluation (same format as speaker_corpus.py):

  far     realistic distance (about 2-3 m): RT60 .4 s with the direct sound +3 dB over the echo, for the first
          12 readings of every LibriSpeech test-clean person (one shared room)
  fleurs  stranger-only voices in 6 languages (.deps/fleurs/fleurs-<lang>-*.wav, one label per clip; never enrolled)

Usage: python -I speaker_corpus_extra.py <deps dir> <corpus dir (first corpus)> <out dir>
"""
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
sys.path.insert(0, str(Path(__file__).resolve().parents[2] / ".deps" / "audio-tools"))
import numpy as np  # noqa: E402
import soundfile as sf  # noqa: E402


def realistic_room():
    rng = np.random.default_rng(20261117)
    t = np.arange(int(16000 * 0.35)) / 16000
    tail = rng.standard_normal(len(t)) * np.exp(-6.9 * t / 0.4)
    tail[:int(.003 * 16000)] = 0  # first reflections arrive a few ms after the direct sound
    tail *= np.sqrt(1 / (2 * np.sum(tail ** 2)))  # echo energy half the direct energy: +3 dB direct-to-reverberant
    rir = tail.astype(np.float32); rir[0] = 1.0
    return rir


def main():
    deps, first, out = Path(sys.argv[1]), Path(sys.argv[2]), Path(sys.argv[3])
    out.mkdir(parents=True, exist_ok=True)
    rows = []
    rir = realistic_room()
    taken = {}
    for line in open(first / "manifest.tsv", encoding="utf-8"):
        name, spk, group = line.rstrip("\n").split("\t")
        if group != "librispeech-test-clean": continue
        taken[spk] = taken.get(spk, 0) + 1
        if taken[spk] > 12: continue
        audio, _ = sf.read(str(first / name), dtype="float32")
        wet = np.convolve(audio, rir)[: len(audio)]
        wet *= min(1.0, .9 / (np.max(np.abs(wet)) + 1e-9))
        target = name.replace("ls-test-clean-", "ls-far2-")
        sf.write(str(out / target), wet.astype(np.float32), 16000, subtype="PCM_16")
        rows.append((target, spk, "librispeech-far2"))
    for wav in sorted((deps / "fleurs").glob("fleurs-*.wav")):
        lang = wav.name.split("-")[1]
        audio, rate = sf.read(str(wav), dtype="float32", always_2d=True)
        audio = audio.mean(axis=1)
        assert rate == 16000
        audio *= min(1.0, .9 / (np.max(np.abs(audio)) + 1e-9))
        sf.write(str(out / wav.name), audio, 16000, subtype="PCM_16")
        rows.append((wav.name, f"fl-{wav.stem}", f"fleurs-{lang}"))
    with open(out / "manifest.tsv", "w", encoding="utf-8") as f:
        for r in rows: f.write("\t".join(r) + "\n")
    print(f"clips={len(rows)}")


if __name__ == "__main__":
    main()
