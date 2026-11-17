"""Seventh corpus: Mozilla Common Voice (CC0) in the app's languages, real volunteers on their own phones and laptops.
Reads every archive in .deps/speaker-public/data/commonvoice/ (.tar.gz / .tgz / .tar / .zip), finds its clip list
(.tsv with client_id and path columns), and takes up to 8 clips from each speaker (anonymous client id). Output:
16 kHz mono PCM16 WAV + manifest.tsv, group "cv-<language>" so results can be reported per language.

Usage: python -I speaker_corpus_commonvoice.py <deps dir> <out dir>
"""
import csv
import io
import re
import sys
import tarfile
import zipfile
from collections import defaultdict
from pathlib import Path

sys.path.insert(0, str(Path(sys.argv[1]) / "audio-tools"))
import av  # noqa: E402
import numpy as np  # noqa: E402
import soundfile as sf  # noqa: E402

csv.field_size_limit(10_000_000)
LANGS = {"ko": "ko", "ar": "ar", "ru": "ru", "zh-tw": "zh", "zh-cn": "zh", "zh-hk": "zh", "en": "en", "fr": "fr", "de": "de"}


def decode(data: bytes) -> np.ndarray:
    with av.open(io.BytesIO(data)) as container:
        resampler = av.AudioResampler(format="flt", layout="mono", rate=16000)
        chunks = [o.to_ndarray().reshape(-1) for f in container.decode(audio=0) for o in resampler.resample(f)]
        chunks += [o.to_ndarray().reshape(-1) for o in resampler.resample(None)]
    return np.concatenate(chunks).astype(np.float32) if chunks else np.zeros(0, np.float32)


class Archive:
    def __init__(self, path: Path):
        self.zip = zipfile.ZipFile(path) if path.suffix == ".zip" else None
        self.tar = None if self.zip else tarfile.open(path)
        self.names = self.zip.namelist() if self.zip else [m.name for m in self.tar.getmembers() if m.isfile()]
    def read(self, name: str) -> bytes:
        return self.zip.read(name) if self.zip else self.tar.extractfile(name).read()


def language(path: Path, names) -> str | None:
    text = (path.name + " " + " ".join(names[:50])).lower()
    for key, lang in sorted(LANGS.items(), key=lambda kv: -len(kv[0])):
        if re.search(rf"(^|[^a-z]){re.escape(key)}([^a-z]|$)", text): return lang
    return None


def main():
    deps, out = Path(sys.argv[1]), Path(sys.argv[2])
    out.mkdir(parents=True, exist_ok=True)
    rows = []
    for path in sorted((deps / "speaker-public/data/commonvoice").iterdir()):
        if not path.is_file() or not re.search(r"\.(tar\.gz|tgz|tar|zip)$", path.name): continue
        archive = Archive(path)
        lang = language(path, archive.names) or "xx"
        tsvs = [n for n in archive.names if n.endswith(".tsv") and re.search(r"(validated|train|test|dev|other|ss-corpus)", n.split("/")[-1])]
        by_name = {n.split("/")[-1]: n for n in archive.names if n.lower().endswith((".mp3", ".wav"))}
        per = defaultdict(list)
        for t in tsvs:
            for r in csv.DictReader(io.StringIO(archive.read(t).decode("utf-8", "replace")), delimiter="\t"):
                cid = r.get("client_id"); clip = (r.get("path") or r.get("audio_file") or "").split("/")[-1]
                if cid and clip in by_name and len(per[cid]) < 8 and clip not in per[cid]: per[cid].append(clip)
        people = {cid: clips for cid, clips in per.items() if len(clips) >= 1}
        for cid, clips in people.items():
            for clip in clips:
                target = f"cv-{lang}-{cid[:16]}-{Path(clip).stem}.wav"
                if not (out / target).exists():
                    try:
                        audio = decode(archive.read(by_name[clip]))
                    except Exception:
                        continue
                    if len(audio) < 16000: continue
                    audio *= min(1.0, .9 / (np.max(np.abs(audio)) + 1e-9))
                    sf.write(str(out / target), audio, 16000, subtype="PCM_16")
                rows.append((target, f"cv-{lang}-{cid[:16]}", f"cv-{lang}"))
        print(f"{path.name}: language {lang}, {len(people)} people")
    with open(out / "manifest.tsv", "w", encoding="utf-8") as f:
        for r in rows: f.write("\t".join(r) + "\n")
    print(f"clips={len(rows)} people={len({r[1] for r in rows})}")


if __name__ == "__main__":
    main()
