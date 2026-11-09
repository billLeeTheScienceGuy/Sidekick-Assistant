"""Freeze a small public FLEURS validation sample. Test assets only; never private audio.

CC BY 4.0, Google FLEURS: https://huggingface.co/datasets/google/fleurs
Selection is deterministic by row order, duration and published gender labels.
Once frozen, IDs/references/PCM hashes must agree; failures are not silently replaced.
"""
from pathlib import Path
import hashlib
import json
import math
import struct
import urllib.request
import wave

root = Path(__file__).resolve().parents[1]
target = root / '.deps/robustness-fixtures'
target.mkdir(parents=True, exist_ok=True)
selection = root / 'docs/evaluation/robustness-selection.json'
configs = dict(en='en_us', ko='ko_kr', zh='cmn_hans_cn', fr='fr_fr', de='de_de', ru='ru_ru', ar='ar_eg')

def fetch_json(url):
    with urllib.request.urlopen(url, timeout=60) as response:
        return json.load(response)

def digest(path):
    with path.open('rb') as stream:
        return hashlib.file_digest(stream, 'sha256').hexdigest()

def write_pcm(data, path):
    if data[:4] != b'RIFF' or data[8:12] != b'WAVE':
        raise ValueError('Expected public WAV fixture')
    offset, audio, format_info = 12, None, None
    while offset + 8 <= len(data):
        size = struct.unpack_from('<I', data, offset + 4)[0]
        block = data[offset + 8:offset + 8 + size]
        if len(block) != size:
            raise ValueError('Truncated fixture')
        if data[offset:offset + 4] == b'fmt ':
            tag, channels, rate = struct.unpack_from('<HHI', block)
            bits = struct.unpack_from('<H', block, 14)[0]
            format_info = tag, channels, rate, bits
        elif data[offset:offset + 4] == b'data':
            audio = block
        offset += 8 + size + size % 2
    if format_info not in [(3, 1, 16000, 32), (1, 1, 16000, 16)] or audio is None:
        raise ValueError(f'Unsupported public fixture format: {format_info}')
    if format_info[0] == 3:
        values = [v[0] for v in struct.iter_unpack('<f', audio)]
        if not all(math.isfinite(v) for v in values):
            raise ValueError('Nonfinite public fixture')
        audio = b''.join(struct.pack('<h', max(-32768, min(32767, round(v * 32767)))) for v in values)
    if not 48_000 <= len(audio) // 2 <= 160_000:
        raise ValueError('Fixture must fit a 3–10 second live window')
    with wave.open(str(path), 'wb') as out:
        out.setnchannels(1); out.setsampwidth(2); out.setframerate(16000); out.writeframes(audio)

frozen = json.loads(selection.read_text(encoding='utf-8')) if selection.exists() else None
entries = []
revision = frozen['datasetRevision'] if frozen else fetch_json('https://huggingface.co/api/datasets/google/fleurs')['sha']
for code, config in configs.items():
    old = [e for e in frozen['clips'] if e['code'] == code] if frozen else []
    if old and all((target/e['file']).is_file() and digest(target/e['file']) == e['sha256'] for e in old):
        entries.extend(old); print(f'Verified cached {code}: {len(old)} public clips', flush=True); continue
    url = f'https://datasets-server.huggingface.co/first-rows?dataset=google%2Ffleurs&config={config}&split=validation'
    rows = [e for e in fetch_json(url)['rows'] if 10 <= e['row_idx'] < 100]
    if old:
        wanted = {e['row']: e for e in old}
        chosen = [entry for entry in rows if entry['row_idx'] in wanted]
        if len(chosen) != len(old):
            raise ValueError('Frozen dataset rows are no longer available')
    else:
        chosen = []
        for gender in (0, 1):
            candidates = [e for e in rows if e['row']['gender'] == gender and 48_000 <= e['row']['num_samples'] <= 160_000]
            chosen.extend(candidates[:2])
        for candidate in rows:
            if len(chosen) >= 4:
                break
            if candidate not in chosen and 48_000 <= candidate['row']['num_samples'] <= 160_000:
                chosen.append(candidate)
        if len(chosen) != 4:
            raise ValueError(f'Insufficient bounded {code} fixtures')
        chosen.sort(key=lambda e: e['row_idx'])
    for entry in chosen:
        row = entry['row']; index = entry['row_idx']; name = f'robust-{code}-{index}.wav'
        if old and (row['id'] != wanted[index]['id'] or row['transcription'] != wanted[index]['reference']):
            raise ValueError('Frozen dataset identity/reference changed')
        with urllib.request.urlopen(row['audio'][0]['src'], timeout=60) as response:
            data = response.read(5_000_001)
        if len(data) > 5_000_000:
            raise ValueError('Unexpected fixture size')
        path = target/name
        write_pcm(data, path)
        sha = digest(path)
        if old and sha != wanted[index]['sha256']:
            path.unlink(); raise ValueError('Frozen PCM changed')
        entries.append(dict(code=code, config=config, split='validation', row=index, id=row['id'], gender=row['gender'],
            samples=row['num_samples'], file=name, reference=row['transcription'], sha256=sha))
    print(f'Prepared {code}: {len(chosen)} public clips', flush=True)
manifest = dict(source='https://huggingface.co/datasets/google/fleurs', license='CC-BY-4.0', datasetRevision=revision,
    selection='Rows 10–99 of validation split; first two 3–10s clips per available published gender label, then fill to four by row order, before inference. Test-split viewer unavailable (scan size limit). Not a blind holdout, population sample or speaker-ID corpus.', clips=entries)
if not frozen:
    selection.parent.mkdir(parents=True, exist_ok=True)
    selection.write_text(json.dumps(manifest, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
else:
    assert entries == frozen['clips'], 'Frozen manifest changed'
(target/'robustness.json').write_text(json.dumps(manifest, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
print(f'Verified {len(entries)} public test clips; production app assets unchanged.', flush=True)
