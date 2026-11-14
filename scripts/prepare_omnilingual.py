"""Prepare verified Omnilingual ONNX assets; no phone network access is required."""
from pathlib import Path
import hashlib
import tarfile
import urllib.request

root = Path(__file__).resolve().parents[1]
archive = root / ".deps/omnilingual-v2-int8.tar.bz2"
items = {
    "model.int8.onnx": ("omni-model.int8.onnx", "e3042b2f3b3ef0af2211bf99d2b4bf94a21f5ac0e9898827e7dd6d003a860e91"),
    "tokens.txt": ("omni-tokens.txt", "7d99997ef207ff14c2cfe825f2aa037528ea250113cc3c6392bfe49326884ba6"),
}
destination = root / "app/src/main/assets/models"
destination.mkdir(parents=True, exist_ok=True)
def digest(path):
    with path.open("rb") as stream:
        return hashlib.file_digest(stream, "sha256").hexdigest()
if all((destination / name).exists() and digest(destination / name) == sha for name, sha in items.values()):
    print("Verified Omnilingual assets already present.")
else:
    archive.parent.mkdir(parents=True, exist_ok=True)
    if not archive.exists():
        urllib.request.urlretrieve("https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/sherpa-onnx-omnilingual-asr-1600-languages-300M-ctc-v2-int8-2026-02-05.tar.bz2", archive)
    with tarfile.open(archive) as source:
        for member in source.getmembers():
            entry = items.get(Path(member.name).name)
            if not entry or not member.isfile():
                continue
            name, expected = entry
            temporary = destination / (name + ".download")
            try:
                with source.extractfile(member) as stream, temporary.open("wb") as output:
                    import shutil
                    shutil.copyfileobj(stream, output)
                if digest(temporary) != expected:
                    raise RuntimeError("Omnilingual checksum mismatch")
                temporary.replace(destination / name)
            finally:
                temporary.unlink(missing_ok=True)
    if not all((destination / name).exists() and digest(destination / name) == sha for name, sha in items.values()):
        raise RuntimeError("Omnilingual archive is incomplete")
    print("Verified Omnilingual model and tokens.")
