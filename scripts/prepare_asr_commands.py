"""Host-only synthetic public test sentences. Never uploads microphone/user audio.

Requires edge-tts and imageio-ffmpeg; this is opt-in test tooling, not an app dependency.
"""
import asyncio, hashlib, json, subprocess, sys
from pathlib import Path
root = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(root / '.deps/korean-tools'))
import edge_tts, imageio_ffmpeg
target = root / '.deps/asr-commands'
target.mkdir(parents=True, exist_ok=True)
cases = {
 'en': ('en-US-AriaNeural', ["I'll attend a meeting tomorrow at 6pm.", 'Move the meeting to tomorrow at 7pm.', 'Cancel the meeting.', "Don't cancel the meeting."]),
 'ko': ('ko-KR-SunHiNeural', ['내일 오후 6시에 회의가 있어요.', '회의를 내일 오후 7시로 변경해줘.', '회의를 취소해줘.', '회의를 취소하지 마세요.']),
 'zh': ('zh-CN-XiaoxiaoNeural', ['明天下午六点有会议。', '把会议改到明天下午七点。', '取消会议。', '不要取消会议。']),
 'fr': ('fr-FR-DeniseNeural', ['Je participerai à une réunion demain à 18h.', 'Déplace la réunion à demain à 19h.', 'Annule la réunion.', "N’annule pas la réunion."]),
 'de': ('de-DE-KatjaNeural', ['Ich nehme morgen um 18 Uhr an einem Treffen teil.', 'Verschiebe das Treffen auf morgen um 19 Uhr.', 'Sag das Treffen ab.', 'Sag das Treffen nicht ab.']),
 'ru': ('ru-RU-SvetlanaNeural', ['Я пойду на встречу завтра в 18:00.', 'Перенеси встречу на завтра в 19:00.', 'Отмени встречу.', 'Не отмени встречу.']),
 'ar': ('ar-SA-ZariyahNeural', ['سأحضر الاجتماع غدا الساعة السادسة مساء.', 'انقل الاجتماع إلى غدا الساعة السابعة مساء.', 'ألغي الاجتماع.', 'لا تلغي الاجتماع.']),
}
async def main():
 manifest = []
 for code, (voice, texts) in cases.items():
  voices = [voice, 'ar-SA-HamedNeural'] if code == 'ar' else [voice]
  extra = ['저는 내일 오후 8시에 파티에 갈 거예요.', '저는 내일 오후 여덟 시에 파티에 갈 거예요.'] if code == 'ko' else []
  for selected in voices:
   for index, reference in enumerate(texts + extra):
    name = f'{code}-{selected}-{index}'
    mp3 = target / (name + '.mp3'); wav = target / (name + '.wav')
    if not wav.exists():
     await edge_tts.Communicate(reference, selected).save(str(mp3))
     subprocess.run([imageio_ffmpeg.get_ffmpeg_exe(), '-y', '-loglevel', 'error', '-i', str(mp3), '-ar', '16000', '-ac', '1', str(wav)], check=True)
    manifest.append(dict(code=code, voice=selected, file=wav.name, reference=reference, action=['create','move','cancel','negative','create','create'][index], hour=20 if index >= 4 else 18 if index == 0 else 19, sha256=hashlib.sha256(wav.read_bytes()).hexdigest()))
  print(f'Prepared {code} commands', flush=True)
 (target / 'asr-commands.json').write_text(json.dumps(manifest, ensure_ascii=False, indent=2), encoding='utf-8')
asyncio.run(main())
