"""Replay test: public voices played out loud by the laptop, recorded by the phone microphone (ReplayCaptureTest).

  build  <corpus dir> <out dir>            playlist.wav (start beep, then clips with gaps) + schedule.tsv
  cut    <out dir> <phone replay.wav>      finds the beep, cuts each clip from the phone recording into <out dir>/corpus
                                           with manifest.tsv (same speakers, group "replay"), ready for SpeakerEmbed.java

Clips: the last 4 readings of 20 LibriSpeech test-clean people (never their enrollment readings) and the owner's clips,
each at most 8 s, 1.5 s apart.
"""
import sys
from collections import defaultdict
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[2] / ".deps" / "audio-tools"))
import numpy as np  # noqa: E402
import soundfile as sf  # noqa: E402

RATE = 16000
GAP = int(1.5 * RATE)
LEAD = 3 * RATE


def beep():
    t = np.arange(int(.5 * RATE)) / RATE
    return (.5 * np.sin(2 * np.pi * 1000 * t)).astype(np.float32)


def build(corpus: Path, out: Path, enroll: bool = False):
    rows = [l.rstrip("\n").split("\t") for l in open(corpus / "manifest.tsv", encoding="utf-8") if l.strip()]
    clean = defaultdict(list)
    for name, spk, group in rows:
        if group == "librispeech-test-clean":
            clean[spk].append(name)
    if enroll:
        # Setup through the same microphone: each person's first 3 readings (setup) and last 4 (turns).
        pick = [(n, s) for s in sorted(clean)[:20] for n in clean[s][:3] + clean[s][-4:]]
    else:
        pick = [(n, s) for s in sorted(clean)[:20] for n in clean[s][-4:]]
    pick += [(n, s) for n, s, g in rows if g == "owner"]
    parts = [np.zeros(LEAD, np.float32), beep(), np.zeros(2 * RATE, np.float32)]
    at = sum(len(p) for p in parts)
    schedule = []
    for name, spk in pick:
        audio, _ = sf.read(str(corpus / name), dtype="float32")
        audio = audio[: 8 * RATE] * (0.5 / (np.max(np.abs(audio)) + 1e-9))
        schedule.append((name, spk, at - LEAD, len(audio)))  # offset from the beep start
        parts += [audio.astype(np.float32), np.zeros(GAP, np.float32)]
        at += len(audio) + GAP
    out.mkdir(parents=True, exist_ok=True)
    sf.write(str(out / "playlist.wav"), np.concatenate(parts), RATE, subtype="PCM_16")
    with open(out / "schedule.tsv", "w", encoding="utf-8") as f:
        for row in schedule:
            f.write("\t".join(map(str, row)) + "\n")
    print(f"clips={len(schedule)} seconds={at / RATE:.0f}")


def cut(out: Path, recording: Path):
    audio, rate = sf.read(str(recording), dtype="float32")
    assert rate == RATE
    ref = beep()
    # Matched filter for the 1 kHz beep: strongest normalized correlation in the first 2 minutes.
    head = audio[: 120 * RATE]
    corr = np.correlate(head, ref, mode="valid")
    energy = np.sqrt(np.convolve(head ** 2, np.ones(len(ref)), mode="valid")) + 1e-9
    start = int(np.argmax(np.abs(corr) / energy))
    print(f"beep at {start / RATE:.2f}s")
    corpus = out / "corpus"; corpus.mkdir(exist_ok=True)
    rows = []
    for line in open(out / "schedule.tsv", encoding="utf-8"):
        name, spk, offset, length = line.rstrip("\n").split("\t")
        a = start + int(offset); b = a + int(length)
        if b > len(audio):
            break
        clip = audio[a:b]
        target = f"replay-{name}"
        sf.write(str(corpus / target), clip, RATE, subtype="PCM_16")
        rows.append((target, spk, "replay"))
    with open(corpus / "manifest.tsv", "w", encoding="utf-8") as f:
        for r in rows:
            f.write("\t".join(r) + "\n")
    level = 20 * np.log10(np.sqrt(np.mean(audio[start:] ** 2)) + 1e-9)
    print(f"cut={len(rows)} level={level:.1f} dBFS")


if __name__ == "__main__":
    if sys.argv[1] in ("build", "build-enroll"):
        build(Path(sys.argv[2]), Path(sys.argv[3]), sys.argv[1] == "build-enroll")
    else:
        cut(Path(sys.argv[2]), Path(sys.argv[3]))
