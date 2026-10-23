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

vad = target.with_name("ggml-silero-v5.1.2.bin")
vad_hash = "29940d98d42b91fbd05ce489f3ecf7c72f0a42f027e4875919a28fb4c04ea2cf"
if not vad.exists() or digest(vad) != vad_hash:
    temporary = vad.with_suffix(".download")
    urllib.request.urlretrieve("https://huggingface.co/ggml-org/whisper-vad/resolve/9ffd54a1e1ee413ddf265af9913beaf518d1639b/ggml-silero-v5.1.2.bin", temporary)
    if digest(temporary) != vad_hash:
        raise RuntimeError("Speech detector checksum mismatch")
    temporary.replace(vad)
print("Verified speech detector ready for APK packaging.")
