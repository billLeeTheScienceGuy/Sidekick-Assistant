"""Host-only public-fixture evaluation of an optional Arabic model; no user audio.

NVIDIA FastConformer Arabic, CC BY 4.0; ONNX export by OpenVoiceOS.
Experimental: no production download, runtime dependency or model routing is added.
Dependencies: .deps/arabic-tools, onnx-asr[cpu]==0.12.0, onnxruntime==1.23.2,
huggingface-hub==0.36.0 and hf-xet==1.2.0 for resumable public downloads.
"""
from pathlib import Path
import hashlib
import json
import sys
import time

root = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(root/'.deps/arabic-tools'))
from huggingface_hub import hf_hub_download
target = root/'.deps/nemo-ar'
target.mkdir(parents=True, exist_ok=True)
revision = '41ddc50c387c4b0f94cdc3c781f33f177fdf19ff'
base = f'https://huggingface.co/OpenVoiceOS/stt_ar_fastconformer_hybrid_large_pc_v1.0_onnx/resolve/{revision}/'
expected = '8a8f9bf632e26dc9e19f44a253db3cc8bb25678e36a3535c73407bc4be5e2533'
def sha(path):
    with path.open('rb') as stream: return hashlib.file_digest(stream, 'sha256').hexdigest()
for name in ['model.onnx', 'config.json', 'vocab.txt']:
    path = target/name
    if path.exists() and (name != 'model.onnx' or sha(path) == expected): continue
    hf_hub_download('OpenVoiceOS/stt_ar_fastconformer_hybrid_large_pc_v1.0_onnx', name,
        revision=revision, local_dir=target, cache_dir=root/'.deps/hf-cache', token=False)
    if name == 'model.onnx' and sha(path) != expected: raise ValueError('Arabic model checksum mismatch')
print('Verified public Arabic model; loading local CPU runtime.', flush=True)
import onnx_asr
import onnxruntime as ort
options = ort.SessionOptions(); options.intra_op_num_threads = 2; options.inter_op_num_threads = 1
model = onnx_asr.load_model('nemo-conformer-ctc', path=target, sess_options=options)
commands = json.loads((root/'.deps/asr-commands/asr-commands.json').read_text(encoding='utf-8'))
readings = json.loads((root/'docs/evaluation/robustness-selection.json').read_text(encoding='utf-8'))['clips']
rows = []
for case in [c for c in commands if c['code'] == 'ar'] + [c for c in readings if c['code'] == 'ar']:
    source = root/('.deps/asr-commands' if 'action' in case else '.deps/robustness-fixtures')/case['file']
    started = time.monotonic()
    text = model.recognize(str(source))
    rows.append(dict(file=case['file'], reference=case['reference'], recognized=text, elapsedMs=round((time.monotonic()-started)*1000), action=case.get('action')))
    print(case['file'], rows[-1]['elapsedMs'], 'ms', flush=True)
report = dict(source='https://huggingface.co/nvidia/stt_ar_fastconformer_hybrid_large_pc_v1.0',
    export='https://huggingface.co/OpenVoiceOS/stt_ar_fastconformer_hybrid_large_pc_v1.0_onnx', revision=revision,
    license='CC-BY-4.0', modelSha256=expected, conditions='Host CPU, public/synthetic clips only; not Pixel latency or natural-conversation evidence', results=rows)
(target/'evaluation.json').write_text(json.dumps(report, ensure_ascii=False, indent=2)+'\n',encoding='utf-8')
print('Saved host-only Arabic evaluation.', flush=True)
