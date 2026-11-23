"""Fetch verified free speaker model and Android runtime; no phone network access."""
from pathlib import Path
import hashlib
import urllib.request

root = Path(__file__).resolve().parents[1]
items = [
    # Pinned Hugging Face revision (2024-10-14): on 2026-11-19 the maintainer replaced the file behind the release link
    # with a re-exported model (different bytes). SideKick's voice-matching bars were measured on this exact file, and
    # the app checks this checksum at run time, so the original is fetched from the revision that cannot change.
    ("app/src/main/assets/models/campplus-advanced.onnx", "https://huggingface.co/csukuangfj/speaker-embedding-models/resolve/0743f301363dec56491a490f6d6cbc9d67f9a3bf/3dspeaker_speech_campplus_sv_zh_en_16k-common_advanced.onnx", "aa3cfc16963a10586a9393f5035d6d6b57e98d358b347f80c2a30bf4f00ceba2"),
    ("app/libs/sherpa-onnx-1.13.8.aar", "https://github.com/k2-fsa/sherpa-onnx/releases/download/v1.13.8/sherpa-onnx-1.13.8.aar", "633c24321e06b1fe79feafa03ea16cbc0f8a286641e2da3559bac91bdb13bd96"),
    ("app/src/main/assets/models/voxceleb_resnet34_LM.onnx", "https://github.com/k2-fsa/sherpa-onnx/releases/download/speaker-recongition-models/wespeaker_en_voxceleb_resnet34_LM.onnx", "e9848563da86f263117134dfd7ad63c92355b37de492b55e325400c9d9c39012"),
]
for relative, url, expected in items:
    target = root / relative
    target.parent.mkdir(parents=True, exist_ok=True)
    def digest(path):
        with path.open("rb") as stream:
            return hashlib.file_digest(stream, "sha256").hexdigest()
    if not target.exists() or digest(target) != expected:
        temporary = target.with_suffix(".download")
        try:
            urllib.request.urlretrieve(url, temporary)
            if digest(temporary) != expected:
                raise RuntimeError("Speaker dependency checksum mismatch")
            temporary.replace(target)
        finally:
            temporary.unlink(missing_ok=True)
    print(f"Verified {target.name}")
