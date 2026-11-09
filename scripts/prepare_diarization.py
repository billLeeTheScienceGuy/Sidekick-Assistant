"""Fetch the pinned public MIT segmentation export; no account or phone networking."""
from pathlib import Path
import hashlib
import tarfile
import urllib.request

root = Path(__file__).resolve().parents[1]
cache = root / '.deps/diarization'
cache.mkdir(parents=True, exist_ok=True)
archive = cache / 'segmentation.tar.bz2'
archive_sha = '24615ee884c897d9d2ba09bb4d30da6bb1b15e685065962db5b02e76e4996488'
model_sha = 'd582f4b4c6b48205de7e0643c57df0df5615a3c176189be3fc461e9d18827b5d'
def digest(path):
    with path.open('rb') as source:
        return hashlib.file_digest(source, 'sha256').hexdigest()
if not archive.exists() or digest(archive) != archive_sha:
    temporary = archive.with_suffix('.download')
    try:
        urllib.request.urlretrieve('https://github.com/k2-fsa/sherpa-onnx/releases/download/speaker-segmentation-models/sherpa-onnx-pyannote-segmentation-3-0.tar.bz2', temporary)
        assert digest(temporary) == archive_sha, 'Segmentation archive checksum mismatch'
        temporary.replace(archive)
    finally:
        temporary.unlink(missing_ok=True)
with tarfile.open(archive) as source:
    prefix = 'sherpa-onnx-pyannote-segmentation-3-0/'
    model = source.extractfile(prefix + 'model.int8.onnx').read()
    assert hashlib.sha256(model).hexdigest() == model_sha
    target = root / 'app/src/main/assets/models/diarization.int8.onnx'
    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_bytes(model)
    license_text = source.extractfile(prefix + 'LICENSE').read()
    assert hashlib.sha256(license_text).hexdigest() == '14d7016ad68e7394d6e6b78d96cc2ae431c905287b89674cfdf021e79e62b8ba'
    (root / 'app/src/main/assets/licenses/diarization-MIT.txt').write_bytes(license_text)
print('Verified offline diarization model: 1,540,506 bytes, MIT (CNRS/pyannote).')
