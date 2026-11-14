"""Synthetic code-switch regression fixtures; never user audio or production assets.

Joins fixed existing synthetic commands to a newly generated English venue phrase.
Two synthetic voices and concatenated speech are not natural bilingual validation.
"""
from pathlib import Path
import asyncio, hashlib, json, subprocess, sys, wave, struct
root = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(root / '.deps/korean-tools'))
import edge_tts, imageio_ffmpeg
out = root / '.deps/bilingual-fixtures'
out.mkdir(parents=True, exist_ok=True)

def pcm(path):
    with wave.open(str(path)) as w:
        assert w.getframerate() == 16000 and w.getnchannels() == 1 and w.getsampwidth() == 2
        data = w.readframes(w.getnframes())
    values = [x[0] for x in struct.iter_unpack('<h', data)]
    voiced = [i for i,v in enumerate(values) if abs(v) >= 180]
    assert voiced
    # Trim outer silence only; preserve each utterance and 80 ms of its edges.
    return data[max(0, voiced[0]-1280)*2:min(len(values), voiced[-1]+1281)*2]

async def main():
    english = out / 'venue.wav'
    if not english.exists():
        mp3 = out / 'venue.mp3'
        await edge_tts.Communicate('at The James', 'en-US-AriaNeural').save(str(mp3))
        subprocess.run([imageio_ffmpeg.get_ffmpeg_exe(),'-y','-loglevel','error','-i',str(mp3),'-ar','16000','-ac','1',str(english)],check=True)
    venue = pcm(english)
    rows = json.loads((root / '.deps/asr-commands/asr-commands.json').read_text(encoding='utf-8'))
    manifest=[]
    for code in ['ko','zh','fr','de','ru','ar']:
        row = next(x for x in rows if x['code']==code and x['action']=='create')
        original = pcm(root / '.deps/asr-commands' / row['file'])
        for order in ['before','after']:
            pieces = [venue, original] if order=='before' else [original, venue]
            data = pieces[0] + bytes(1600) + pieces[1]
            name=f'bilingual-{code}-{order}.wav'
            with wave.open(str(out/name),'wb') as w:
                w.setnchannels(1);w.setsampwidth(2);w.setframerate(16000);w.writeframes(data)
            reference = ('at The James. '+row['reference']) if order=='before' else (row['reference']+' at The James.')
            manifest.append(dict(file=name, languages=['en',code], reference=reference, audioMs=len(data)*1000//32000,
                sha256=hashlib.sha256((out/name).read_bytes()).hexdigest(), sourceCommand=row['file']))
    (out/'bilingual.json').write_text(json.dumps(dict(conditions='Concatenated synthetic voices; exploratory code-switch regression, not natural bilingual accuracy', clips=manifest),ensure_ascii=False,indent=2),encoding='utf-8')
    print('Prepared',len(manifest),'synthetic bilingual fixtures.')
asyncio.run(main())
