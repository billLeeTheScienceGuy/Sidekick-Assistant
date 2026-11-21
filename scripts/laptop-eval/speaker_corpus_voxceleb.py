"""VoxCeleb1 corpus (real-world interview speech, 1,251 people; the owner downloads it after accepting the dataset's
terms at robots.ox.ac.uk/~vgg/data/voxceleb/). Reads vox1_test_wav.zip and/or vox1_dev_wav*.zip from
.deps/speaker-public/data/voxceleb/ without unpacking everything: per person up to 8 clips, each from a different
video where possible. Output: 16 kHz mono PCM16 WAV + manifest.tsv (group "voxceleb").

Usage: python -I speaker_corpus_voxceleb.py <deps dir> <out dir>
"""
import io
import sys
import zipfile
from collections import defaultdict
from pathlib import Path

sys.path.insert(0, str(Path(sys.argv[1]) / "audio-tools"))
import numpy as np  # noqa: E402
import soundfile as sf  # noqa: E402


def main():
    deps, out = Path(sys.argv[1]), Path(sys.argv[2])
    out.mkdir(parents=True, exist_ok=True)
    archives = sorted((deps / "speaker-public/data/voxceleb").glob("*.zip"))
    if not archives: sys.exit("No VoxCeleb zip files in .deps/speaker-public/data/voxceleb/")
    rows = []
    for archive in archives:
        with zipfile.ZipFile(archive) as z:
            by_person = defaultdict(lambda: defaultdict(list))
            for n in z.namelist():
                parts = n.split("/")
                if n.endswith(".wav") and len(parts) >= 4:
                    by_person[parts[-3]][parts[-2]].append(n)
            for person, videos in sorted(by_person.items()):
                picks, round_ = [], 0
                while len(picks) < 8 and any(len(v) > round_ for v in videos.values()):
                    for video in sorted(videos):
                        if len(videos[video]) > round_ and len(picks) < 8: picks.append(sorted(videos[video])[round_])
                    round_ += 1
                for n in picks:
                    audio, rate = sf.read(io.BytesIO(z.read(n)), dtype="float32", always_2d=True)
                    audio = audio.mean(axis=1)
                    if rate != 16000:
                        k = int(round(len(audio) * 16000 / rate))
                        audio = np.interp(np.linspace(0, len(audio) - 1, k), np.arange(len(audio)), audio).astype(np.float32)
                    audio *= min(1.0, .9 / (np.max(np.abs(audio)) + 1e-9))
                    target = "vox-" + n.replace("/", "-")
                    sf.write(str(out / target), audio, 16000, subtype="PCM_16")
                    rows.append((target, f"vox-{person}", "voxceleb"))
    with open(out / "manifest.tsv", "w", encoding="utf-8") as f:
        for r in rows: f.write("\t".join(r) + "\n")
    print(f"clips={len(rows)} people={len({r[1] for r in rows})}")


if __name__ == "__main__":
    main()
