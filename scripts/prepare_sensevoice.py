"""Prepare verified SenseVoice ONNX assets; no phone network access is required."""
from pathlib import Path
import hashlib
import tarfile
import urllib.request

root = Path(__file__).resolve().parents[1]
archive = root / ".deps/sensevoice-int8.tar.bz2"
items = {
    "model.int8.onnx": ("sensevoice-ko.int8.onnx", "c71f0ce00bec95b07744e116345e33d8cbbe08cef896382cf907bf4b51a2cd51"),
    "tokens.txt": ("sensevoice-tokens.txt", "f449eb28dc567533d7fa59be34e2abca8784f771850c78a47fb731a31429a1dc"),
}
destination = root / "app/src/main/assets/models"
destination.mkdir(parents=True, exist_ok=True)
def digest(path):
    with path.open("rb") as stream:
        return hashlib.file_digest(stream, "sha256").hexdigest()
if all((destination / name).exists() and digest(destination / name) == sha for name, sha in items.values()):
    print("Verified SenseVoice assets already present.")
else:
    archive.parent.mkdir(parents=True, exist_ok=True)
    if not archive.exists():
        urllib.request.urlretrieve("https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/sherpa-onnx-sense-voice-zh-en-ja-ko-yue-int8-2024-07-17.tar.bz2", archive)
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
                    raise RuntimeError("SenseVoice checksum mismatch")
                temporary.replace(destination / name)
            finally:
                temporary.unlink(missing_ok=True)
    if not all((destination / name).exists() and digest(destination / name) == sha for name, sha in items.values()):
        raise RuntimeError("SenseVoice archive is incomplete")
    print("Verified SenseVoice model and tokens.")
