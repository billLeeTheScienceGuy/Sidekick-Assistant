"""Prepare ignored local demo deliverables; never include app databases, profiles or raw user audio."""
from pathlib import Path
import hashlib
import json
import re
import shutil
import subprocess
import zipfile

root = Path(__file__).resolve().parents[1]
version = re.search(r'versionName = "([^"]+)"', (root / 'app/build.gradle.kts').read_text()).group(1)
apk = root / 'app/build/outputs/apk/debug/app-debug.apk'
metadata = json.loads(apk.with_name('output-metadata.json').read_text())
assert metadata['elements'][0]['versionName'] == version, 'Rebuild the APK first'
optional = {line.split('\t')[1] for line in (root / 'app/src/main/assets/model-packs.tsv').read_text().splitlines() if line}
with zipfile.ZipFile(apk) as archive:
    names = set(archive.namelist())
    assert all('assets/models/' + name not in names for name in optional), 'Optional models leaked into compact APK'
    assert 'assets/models/ggml-tiny.en.bin' in names
    assert 'assets/models/ggml-silero-v5.1.2.bin' in names
    assert not any(name.startswith('assets/') and name.lower().endswith(('.wav', '.pcm', '.db', '.jks', '.keystore')) for name in names), 'Unexpected audio/database/key asset in APK'
    assert 'assets/licenses/whisper-MIT.txt' in names
out = root / 'dist' / f'SideKick-{version}'
out.mkdir(parents=True, exist_ok=True)
shutil.copy2(apk, out / f'SideKick-{version}.apk')
for name in ['SUBMISSION_REPORT.md', 'CAPSTONE_CHECKLIST.md', 'ARCHITECTURE.md', 'PRIVACY_AND_RISK.md', 'ACCURACY_REPORT.md', 'ASR_EVALUATION.md', 'STREAMING_BENCHMARK.md', 'DEMO.md', 'MODEL_PACKS.md', 'SUMMARIES.md', 'VALIDATION.md']:
    shutil.copy2(root / 'docs' / name, out / name)
(out / 'README.txt').write_text(f'''SideKick {version} prototype

Install SideKick-{version}.apk on an Android device that permits sideloading.
This development-signed APK matches the development project; it is not a store release.
Text, Planner and Summary work without microphone access. English Fast is included.
Use Get started for language, models, voice setup and permissions. Start live notes explicitly.
See MODEL_PACKS.md for optional packs; they are separate from the compact submission folder.
See DEMO.md and SUBMISSION_REPORT.md for supported features, evaluation and limitations.
Source and reproducible build scripts: https://github.com/billLeeTheScienceGuy/Sidekick-Assistant
''', encoding='utf-8')
def digest(file):
    with file.open('rb') as stream:
        return hashlib.file_digest(stream, 'sha256').hexdigest()
(out / 'BUILD_INFO.json').write_text(json.dumps({
    'version': version,
    'versionCode': metadata['elements'][0]['versionCode'],
    'sourceCommit': subprocess.check_output(['git', 'rev-parse', 'HEAD'], cwd=root, text=True).strip(),
    'sourceHasUncommittedChanges': bool(subprocess.check_output(['git', 'status', '--porcelain'], cwd=root, text=True).strip()),
    'buildVariant': 'debug',
    'apkSha256': digest(apk),
    'signing': 'Android development key; not a production store release',
}, indent=2) + '\n', encoding='utf-8')
(out / 'SHA256SUMS.txt').write_text(''.join(f'{digest(p)}  {p.name}\n' for p in sorted(out.iterdir()) if p.is_file() and p.name != 'SHA256SUMS.txt'), encoding='utf-8')
print(f'Submission folder: {out}')
print(f'APK: {apk.stat().st_size:,} bytes; optional packs: {root / ".deps/model-packs"}')
