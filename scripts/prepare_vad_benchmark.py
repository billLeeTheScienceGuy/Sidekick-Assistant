"""Prepare the optional MIT Silero model for instrumentation benchmarks only."""
import hashlib
from pathlib import Path
import urllib.request

URL = "https://huggingface.co/ggml-org/whisper-vad/resolve/9ffd54a1e1ee413ddf265af9913beaf518d1639b/ggml-silero-v5.1.2.bin"
SHA256 = "29940d98d42b91fbd05ce489f3ecf7c72f0a42f027e4875919a28fb4c04ea2cf"
target = Path(__file__).resolve().parents[1] / "app/src/androidTest/assets/ggml-silero-v5.1.2.bin"
data = urllib.request.urlopen(URL, timeout=60).read()
if hashlib.sha256(data).hexdigest() != SHA256:
    raise RuntimeError("VAD model checksum mismatch")
target.parent.mkdir(parents=True, exist_ok=True)
target.write_bytes(data)
print(f"Verified VAD model: {target}")
