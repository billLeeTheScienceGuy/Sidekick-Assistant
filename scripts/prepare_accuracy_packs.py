"""Prepare the optional accuracy packs (English: Parakeet TDT 110M, Russian: GigaAM v3, Korean time check: Zipformer) as verified assets.
Files are renamed to the names in app/src/main/assets/model-packs.tsv; run package_model_packs.py afterwards."""
from pathlib import Path
import hashlib
import shutil
import tarfile
import urllib.request

root = Path(__file__).resolve().parents[1]
destination = root / "app/src/main/assets/models"
release = "https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/"
packs = {
    "sherpa-onnx-nemo-parakeet_tdt_transducer_110m-en-36000-int8": {
        "encoder.int8.onnx": ("parakeet-encoder.int8.onnx", "0f35509ddeb9b39002fb077d979a9fe74f06eb0bc4dd5c34f512f82e5111d657"),
        "decoder.int8.onnx": ("parakeet-decoder.int8.onnx", "f7c331c5504c2e593c76ed22b728e3f554af6c4a383dde862e719ced08b1da19"),
        "joiner.int8.onnx": ("parakeet-joiner.int8.onnx", "bf7dff69e9f2cdbe9943d70da358f38b361c115ba0105bae7e908e0d6ec782f6"),
        "tokens.txt": ("parakeet-tokens.txt", "450e56bd2f036fe5b6aa821865838cc5aa9d8b0106134ce9a9ba0664abe6cd10"),
    },
    "sherpa-onnx-nemo-transducer-giga-am-v3-russian-2025-12-16": {
        "encoder.int8.onnx": ("gigaam-encoder.int8.onnx", "0ec54e5f130a91c0f228d21795ede10b31c992a1f73e45dcd827e843a71a9b50"),
        "decoder.onnx": ("gigaam-decoder.onnx", "5d1cfef155d8e07f213bd2f3229b5f6c5c29355c7fa5c1b9eb62cb1925725254"),
        "joiner.onnx": ("gigaam-joiner.onnx", "fd1d02f45c2ad3d6b67cc149811ad794ab4b020ed49a0a9e2790a8619d1cddd8"),
        "tokens.txt": ("gigaam-tokens.txt", "17cc514451bcceac9c280068c71502f8448f99e9fb1456b8d0761651fd0392f2"),
    },
    "sherpa-onnx-zipformer-korean-2024-06-24": {
        "encoder-epoch-99-avg-1.int8.onnx": ("zipko-encoder.int8.onnx", "8b196d723421a0513c98ec25da2c43420c029e817f5e4a90b29ff80291c0af2b"),
        "decoder-epoch-99-avg-1.int8.onnx": ("zipko-decoder.int8.onnx", "2cc8c04ea080a657c18ebc59702e6b049cef08163eba5d68ac5bf707925cb0fb"),
        "joiner-epoch-99-avg-1.int8.onnx": ("zipko-joiner.int8.onnx", "eb654db1ea2cc9d63474855f65958b6059084692a9f2eb4f3812aceb1e416a20"),
        "tokens.txt": ("zipko-tokens.txt", "016bdf0965029263b7ad01b742366ee542ef0bef38261510e8176ff6f2e9e668"),
    },
}

def digest(path):
    with path.open("rb") as stream:
        return hashlib.file_digest(stream, "sha256").hexdigest()

destination.mkdir(parents=True, exist_ok=True)
for name, items in packs.items():
    if all((destination / target).exists() and digest(destination / target) == sha for target, sha in items.values()):
        print(f"Verified {name} already present.")
        continue
    archive = root / ".deps" / f"{name}.tar.bz2"
    archive.parent.mkdir(parents=True, exist_ok=True)
    if not archive.exists():
        urllib.request.urlretrieve(release + archive.name, archive)
    with tarfile.open(archive) as source:
        for member in source.getmembers():
            entry = items.get(Path(member.name).name)
            if not entry or not member.isfile() or Path(member.name).parent.name != name:
                continue
            target, expected = entry
            temporary = destination / (target + ".download")
            try:
                with source.extractfile(member) as stream, temporary.open("wb") as output:
                    shutil.copyfileobj(stream, output)
                if digest(temporary) != expected:
                    raise RuntimeError(f"Checksum mismatch: {member.name}")
                temporary.replace(destination / target)
            finally:
                temporary.unlink(missing_ok=True)
    if not all((destination / target).exists() and digest(destination / target) == sha for target, sha in items.values()):
        raise RuntimeError(f"{name} archive is incomplete")
    print(f"Verified {name}.")

# The Korean pack also needs the model's word-piece vocabulary for name lists (hotwords): bpe.model -> bpe.vocab.
vocab, vocab_sha = destination / "zipko-bpe.vocab", "4dbdff57db7b1e00e8feba8637dec4d2606221f614ce44336c04fd20b07f4c0f"
if not (vocab.exists() and digest(vocab) == vocab_sha):
    import sentencepiece  # python -m pip install sentencepiece
    archive = root / ".deps" / "sherpa-onnx-zipformer-korean-2024-06-24.tar.bz2"
    if not archive.exists():
        urllib.request.urlretrieve(release + archive.name, archive)
    with tarfile.open(archive) as source:
        model = source.extractfile("sherpa-onnx-zipformer-korean-2024-06-24/bpe.model").read()
    sp = sentencepiece.SentencePieceProcessor(model_proto=model)
    vocab.write_text("".join(f"{sp.id_to_piece(i)}\t{sp.get_score(i)}\n" for i in range(sp.get_piece_size())), encoding="utf-8", newline="\n")
    if digest(vocab) != vocab_sha:
        raise RuntimeError("Korean vocabulary checksum mismatch")
print("Verified zipko-bpe.vocab.")
