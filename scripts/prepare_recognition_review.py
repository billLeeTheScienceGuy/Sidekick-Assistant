"""Freeze small public speech selections before 0.33 evaluation; no private audio.
ASCEND: Lovenia et al., LREC 2022, CC BY-SA 4.0; FLEURS: Google, CC BY 4.0.
Audio is test-only. Preserve source licenses/attribution for derived evaluation data.
"""
from pathlib import Path
import hashlib,json,urllib.request,subprocess,sys
root=Path(__file__).resolve().parents[1]
sys.path.insert(0,str(root/'.deps/korean-tools'))
import imageio_ffmpeg
out=root/'.deps/recognition-review';out.mkdir(parents=True,exist_ok=True)
with urllib.request.urlopen('https://datasets-server.huggingface.co/first-rows?dataset=CAiRE%2FASCEND&config=main&split=test',timeout=30) as r: data=json.load(r)
clips=[];counts={} 
for item in data['rows']:
 row=item['row'];speaker=row['original_speaker_id']
 if row['language']!='mixed' or not 1.2<=row['duration']<=8 or counts.get(speaker,0)>=6:continue
 name='ascend-'+str(item['row_idx'])+'.wav';dest=out/name
 if not dest.exists():
  raw=out/(name+'.download')
  urllib.request.urlretrieve(row['audio'][0]['src'],raw)
  subprocess.run([imageio_ffmpeg.get_ffmpeg_exe(),'-y','-loglevel','error','-i',str(raw),'-ar','16000','-ac','1','-c:a','pcm_s16le',str(dest)],check=True)
  raw.unlink()
 clips.append(dict(file=name,reference=row['transcription'],code='zh-en',dataset='ASCEND',split='test',row=item['row_idx'],speaker=speaker,
  source='https://huggingface.co/datasets/CAiRE/ASCEND',revision='737e9800ae31be9932ba8464c80366559bd28424',license='CC BY-SA 4.0',sha256=hashlib.sha256(dest.read_bytes()).hexdigest()))
 counts[speaker]=counts.get(speaker,0)+1
 if len(clips)==6:break
assert len(clips)==6
public=json.loads((root/'.deps/robustness-fixtures/robustness.json').read_text(encoding='utf-8'))
seen=set()
for row in public['clips']:
 if row['code'] in seen:continue
 seen.add(row['code']);dest=root/'.deps/robustness-fixtures'/row['file']
 clips.append(dict(file=row['file'],reference=row['reference'],code=row['code'],dataset='FLEURS',source='https://huggingface.co/datasets/google/fleurs',license='CC BY 4.0',sha256=hashlib.sha256(dest.read_bytes()).hexdigest()))
report=dict(durationRangeSeconds=[1.2,8],conditions='First six eligible mixed ASCEND test clips without speaker balancing (limited speaker coverage), and first frozen FLEURS validation clip per supported language. Frozen before decoding; small development evaluation, not population accuracy.',clips=clips)
frozen=root/'docs/evaluation/recognition-selection-033.json'
if frozen.exists():assert json.loads(frozen.read_text(encoding='utf-8'))==report,'Frozen selection changed'
else:frozen.write_text(json.dumps(report,ensure_ascii=False,indent=2),encoding='utf-8')
(out/'recognition-review.json').write_text(json.dumps(report,ensure_ascii=False,indent=2),encoding='utf-8')
print('Prepared',len(clips),'public clips; ASCEND speaker coverage',counts)
