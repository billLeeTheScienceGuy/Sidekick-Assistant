"""Builds the laptop speaker-evaluation corpus: 16 kHz mono PCM16 WAV files plus manifest.tsv (file, speaker, group).

Sources (all local, nothing uploaded):
  librispeech  .deps/speaker-public/data/LibriSpeech/test-{clean,other}/<speaker>/<chapter>/*.flac  (public, 73 people)
  cs-fleurs    .deps/cs-fleurs/*_<initials>.wav                                                   (public)
  owner        .deps/my-voice/m4a/*.m4a  (the phone owner's own clips; stay on this laptop, never committed)

Usage: python -I speaker_corpus.py <deps dir> <out dir>
"""
import concurrent.futures as cf
import os
import re
import sys
from pathlib import Path

sys.path.insert(0, str(Path(sys.argv[1]) / "audio-tools"))
import av  # noqa: E402
import numpy as np  # noqa: E402
import soundfile as sf  # noqa: E402


def decode(path: Path) -> np.ndarray:
    if path.suffix.lower() in (".flac", ".wav"):
        data, rate = sf.read(str(path), dtype="float32", always_2d=True)
        data = data.mean(axis=1)
    else:
        with av.open(str(path)) as container:
            resampler = av.AudioResampler(format="flt", layout="mono", rate=16000)
            chunks = []
            for frame in container.decode(audio=0):
                for out in resampler.resample(frame):
                    chunks.append(out.to_ndarray().reshape(-1))
            for out in resampler.resample(None):
                chunks.append(out.to_ndarray().reshape(-1))
            return np.concatenate(chunks).astype(np.float32)
    if rate != 16000:
        n = int(round(len(data) * 16000 / rate))
        data = np.interp(np.linspace(0, len(data) - 1, n), np.arange(len(data)), data).astype(np.float32)
    return data


ROOM = None


def room():
    """One fixed room for every clip, like one café or meeting room: the same pink noise and the same echo.
    Shared room sound makes different voices look more alike, the hardest case for keeping strangers out."""
    global ROOM
    if ROOM is None:
        rng = np.random.default_rng(20261116)
        white = rng.standard_normal(16000 * 40)
        spectrum = np.fft.rfft(white)
        spectrum /= np.sqrt(np.maximum(np.arange(len(spectrum)), 1))
        pink = np.fft.irfft(spectrum, len(white)).astype(np.float32)
        pink /= np.sqrt(np.mean(pink ** 2))
        t = np.arange(int(16000 * 0.4)) / 16000
        rir = rng.standard_normal(len(t)) * np.exp(-6.9 * t / 0.5)  # RT60 about 0.5 s
        rir[0] = 1.0
        rir = (rir / np.sqrt(np.sum(rir ** 2))).astype(np.float32)
        ROOM = (pink, rir)
    return ROOM


def condition(audio: np.ndarray, kind: str) -> np.ndarray:
    pink, rir = room()
    if kind in ("far", "far-noisy"):
        audio = np.convolve(audio, rir)[: len(audio)].astype(np.float32)
    if kind in ("noisy", "far-noisy"):
        speech = np.sqrt(np.mean(audio ** 2)) + 1e-9
        noise = np.resize(pink, len(audio)) * speech / (10 ** (10 / 20))  # 10 dB SNR
        audio = audio + noise
    peak = np.max(np.abs(audio)) + 1e-9
    return (audio * min(1.0, 0.9 / peak)).astype(np.float32)


def write(job):
    source, target, kind = job
    if target.exists():
        return
    audio = condition(np.clip(decode(source), -1, 1), kind)
    sf.write(str(target), (audio * 32767).astype(np.int16), 16000, subtype="PCM_16")


def main():
    deps, out = Path(sys.argv[1]), Path(sys.argv[2])
    out.mkdir(parents=True, exist_ok=True)
    rows, jobs = [], []
    libri = deps / "speaker-public/data/LibriSpeech"
    # Per person: 30 readings from test-clean (each in 3 rooms), 8 from test-other (the stranger cohort).
    for split, cap in (("test-clean", 30), ("test-other", 8)):
        taken = {}
        for flac in sorted((libri / split).rglob("*.flac")):
            speaker = flac.parts[-3]
            taken[speaker] = taken.get(speaker, 0) + 1
            if taken[speaker] > cap:
                continue
            name = f"ls-{split}-{flac.stem}.wav"
            rows.append((name, f"ls{speaker}", f"librispeech-{split}"))
            jobs.append((flac, out / name, "clean"))
            if split == "test-clean":
                for kind in ("noisy", "far"):
                    variant = f"ls-{kind}-{flac.stem}.wav"
                    rows.append((variant, f"ls{speaker}", f"librispeech-{kind}"))
                    jobs.append((flac, out / variant, kind))
    for wav in sorted((deps / "cs-fleurs").glob("*.wav")):
        m = re.match(r"cs-([a-z]+)_\d+_([A-Z]+)\.wav", wav.name)
        if m:
            rows.append((wav.name, f"cs-{m.group(1)}-{m.group(2)}", "cs-fleurs"))
            jobs.append((wav, out / wav.name, "clean"))
    for i, clip in enumerate(sorted((deps / "my-voice/m4a").glob("*.m4a"))):
        name = f"owner-{i}.wav"
        rows.append((name, "OWNER", "owner"))
        jobs.append((clip, out / name, "clean"))
    with cf.ProcessPoolExecutor(max_workers=os.cpu_count()) as pool:
        list(pool.map(write, jobs, chunksize=16))
    with open(out / "manifest.tsv", "w", encoding="utf-8") as f:
        for row in rows:
            f.write("\t".join(row) + "\n")
    speakers = {r[1] for r in rows}
    print(f"clips={len(rows)} speakers={len(speakers)} owner={sum(r[1] == 'OWNER' for r in rows)}")


if __name__ == "__main__":
    main()
