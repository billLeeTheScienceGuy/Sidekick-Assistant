"""Fetch six public FLEURS examples for opt-in device evaluation, never app assets.

Dataset source: https://huggingface.co/datasets/google/fleurs (CC BY 4.0).
The manifest records each retrieved file hash and reference; no private audio is used.
"""
import hashlib
import json
from pathlib import Path
import urllib.request
import urllib.error
import struct
import wave

target = Path(__file__).resolve().parents[1] / ".deps/language-fixtures"
target.mkdir(parents=True, exist_ok=True)
manifest = []
for code, config in [("ko", "ko_kr"), ("zh", "cmn_hans_cn"), ("fr", "fr_fr"), ("de", "de_de"), ("ru", "ru_ru"), ("ar", "ar_eg")]:
    split = "validation"
    url = f"https://datasets-server.huggingface.co/first-rows?dataset=google%2Ffleurs&config={config}&split={split}"
    with urllib.request.urlopen(url) as response:
        row = json.load(response)["rows"][0]["row"]
    wav = target / f"{code}.wav"
    urllib.request.urlretrieve(row["audio"][0]["src"], wav)
    # The viewer exports IEEE-float WAV. Convert only these public fixtures to PCM16.
    data = wav.read_bytes()
    offset, samples = 12, None
    while offset + 8 <= len(data):
        size = struct.unpack_from("<I", data, offset + 4)[0]
        if data[offset:offset + 4] == b"fmt ":
            tag, channels, rate = struct.unpack_from("<HHI", data, offset + 8)
            bits = struct.unpack_from("<H", data, offset + 22)[0]
        if data[offset:offset + 4] == b"data":
            samples = data[offset + 8:offset + 8 + size]
        offset += 8 + size + size % 2
    if tag == 3:
        assert channels == 1 and rate == 16000 and bits == 32 and samples is not None
        pcm = b"".join(struct.pack("<h", max(-32768, min(32767, round(value[0] * 32767)))) for value in struct.iter_unpack("<f", samples))
        with wave.open(str(wav), "wb") as stream:
            stream.setnchannels(1); stream.setsampwidth(2); stream.setframerate(16000); stream.writeframes(pcm)
    manifest.append(dict(code=code, config=config, reference=row["transcription"],
                         source="https://huggingface.co/datasets/google/fleurs", split=split, row=0,
                         sha256=hashlib.sha256(wav.read_bytes()).hexdigest()))
    print(f"Prepared public {code} fixture")
(target / "languages.json").write_text(json.dumps(manifest, ensure_ascii=False, indent=2), encoding="utf-8")
