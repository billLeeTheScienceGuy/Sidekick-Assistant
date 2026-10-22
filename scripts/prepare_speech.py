"""Download a pinned-by-hash, MIT-licensed speech model for APK packaging."""
from pathlib import Path
import hashlib
import urllib.request

EXPECTED = "921e4cf8686fdd993dcd081a5da5b6c365bfde1162e72b08d75ac75289920b1f"
target = Path(__file__).resolve().parents[1] / "app/src/main/assets/models/ggml-tiny.en.bin"
def digest(path):
    with path.open("rb") as stream:
        return hashlib.file_digest(stream, "sha256").hexdigest()

target.parent.mkdir(parents=True, exist_ok=True)
if target.exists() and digest(target) == EXPECTED:
    print("Verified speech model already present.")
else:
    temporary = target.with_suffix(".download")
    urllib.request.urlretrieve("https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-tiny.en.bin", temporary)
    if digest(temporary) != EXPECTED:
        raise RuntimeError("Model checksum mismatch; download not installed")
    temporary.replace(target)
    print("Verified speech model ready for APK packaging.")
