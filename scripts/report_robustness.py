"""Summarize public replay JSON without turning fixture results into field-accuracy claims."""
import argparse
import hashlib
import json
from pathlib import Path

parser = argparse.ArgumentParser()
parser.add_argument('--baseline', type=Path, required=True)
parser.add_argument('--candidate', type=Path)
parser.add_argument('--final', type=Path)
parser.add_argument('--automatic', type=Path)
parser.add_argument('--commands', type=Path)
parser.add_argument('--live', type=Path)
parser.add_argument('--recovery', type=Path)
parser.add_argument('--speakers', type=Path, required=True)
parser.add_argument('--output', type=Path, default=Path('docs/ROBUSTNESS_EVALUATION.md'))
args = parser.parse_args()
baseline = json.loads(args.baseline.read_text(encoding='utf-8'))
speakers = json.loads(args.speakers.read_text(encoding='utf-8'))
candidate = json.loads(args.candidate.read_text(encoding='utf-8')) if args.candidate else None
assert baseline['completed'] and speakers['completed']
if candidate:
    assert candidate['completed'] and baseline['dataset'] == candidate['dataset'], 'Different public selection'

def aggregate(report, code, condition):
    rows = [r for r in report['results'] if r['language'] == code and r['condition'] == condition]
    if not rows:
        return '—'
    cer = sum(r['characterEdits'] for r in rows) / sum(r['referenceCharacters'] for r in rows)
    rtf = sum(r['elapsedMs'] for r in rows) / sum(r['audioMs'] for r in rows)
    return f'{cer:.1%} / {rtf:.2f}'

lines = ['# Public-speech robustness evaluation', '',
    'Physical Pixel 7a, local CPU inference, USB connected, no microphone capture.',
    'Twenty-eight new public FLEURS validation readings (four per selected language),',
    'each replayed clean and with deterministic 10 dB stationary white noise.',
    'The selection and converted PCM hashes were frozen before inference. The dataset',
    'viewer could not serve the test split because of a row-group scan-size limit;',
    'validation data is explicitly used instead. This is not a blind holdout, user',
    'study, representative dialect sample or measured room-noise recording.', '',
    '[FLEURS dataset and CC BY 4.0 attribution](https://huggingface.co/datasets/google/fleurs).',
    'See evaluation/robustness-selection.json for revision, rows, references and hashes.',
    'The bounded selection has uneven gender coverage: the available Arabic clips',
    'have female labels and German clips male labels. Published labels do not prove',
    'distinct speakers. No population or demographic fairness result is claimed.', '',
    '## Recognition', '',
    'Cells show micro-averaged normalized character error rate / processing-time-to-audio ratio.',
    'NFKC, case folding and removal of non-letter/digit characters are applied.',
    'Number spelling, dialect, Chinese script variants and diacritics can still affect scores.',
    'Calls include model preparation/switching and warm reuse; PSS is a post-call',
    'whole-process snapshot. This is not peak model memory or battery measurement.', '',
    '| Language | Adaptive clean | Adaptive 10 dB noise | Candidate Omni clean | Candidate Omni noise |',
    '|---|---:|---:|---:|---:|']
for code in ['en', 'ko', 'zh', 'fr', 'de', 'ru', 'ar']:
    values = [aggregate(baseline, code, cond) for cond in ['clean', 'white-noise-10dB']]
    values += [aggregate(candidate, code, cond) if candidate else '—' for cond in ['clean', 'white-noise-10dB']]
    lines.append('| ' + ' | '.join([code] + values) + ' |')
lines += ['', 'These paired candidate clips are development comparisons, not independent',
    'validation after tuning. Short command negation/correction fixtures must also pass',
    'before any routing change. Character accuracy does not establish event/action safety.', '',
    '## Speaker attribution', '',
    'One public JFK voice was enrolled from three overlapping windows; clean, lower-gain',
    'and noisy excerpts of that same recording are controls, not independent owner recall.',
    'Each unrelated modern FLEURS clip was replayed clean, noisy, and simultaneously',
    'mixed with JFK at equal energy. No invented identity or consent is assigned.', '',
    '| Condition | Audio trials | Emitted regions | USER regions |', '|---|---:|---:|---:|']
for condition in sorted({r['condition'] for r in speakers['results']}):
    rows = [r for r in speakers['results'] if r['condition'] == condition]
    trials = len({r['file'] for r in rows})
    lines.append(f'| {condition} | {trials} | {len(rows)} | {sum(r["speaker"] == "USER" for r in rows)} |')
false_owners = [r for r in speakers['results'] if not r['expectedOwner'] and r['speaker'] == 'USER']
lines += ['', f'Unfamiliar/overlap regions assigned to USER: **{len(false_owners)}**.',
    'UNKNOWN is a valid conservative result. These controls do not measure diarization',
    'error rate, unfamiliar-owner generalization, authentication or multilingual consent.', '',
    '## Reproduce', '',
    '1. Run `python scripts/prepare_robustness_fixtures.py`; only test assets are populated.',
    '2. Build/install the isolated benchmark app and its test APK with `-PspeechBenchmark`.',
    '3. Run RobustnessEvaluationTest with `-e runRobustness true`. Never instrument',
    '   the personal com.sidekick.app package. These tests open no microphone.',
    '4. Retrieve files/robust-asr.json, robust-candidate.json and robust-speakers.json',
    '   using adb exec-out run-as on com.sidekick.app.benchmark, then run this script.',
    '5. Keep failures and the frozen selection; do not replace clips after seeing output.', '',
    'Input report SHA-256:', '']
for name in ['baseline', 'candidate', 'speakers', 'final', 'automatic', 'commands', 'live', 'recovery']:
    path = getattr(args, name)
    if path:
        lines.append(f'- {name}: `{hashlib.sha256(path.read_bytes()).hexdigest()}`')
if args.final:
    final = json.loads(args.final.read_text(encoding='utf-8-sig'))
    assert final['completed'] and final['dataset'] == baseline['dataset']
    lines += ['', '## Final selected-language routing', '',
        'Adaptive Automatic and Arabic use Omnilingual. French, German and Russian',
        'use it when the detected speech span reaches five seconds; quiet padding does',
        'not qualify. Short selected-language paths retain their measured Whisper',
        'or SenseVoice choices. Fast/Larger model remain explicit Whisper options.', '',
        '| Language | Final clean CER / work ratio | Final noisy CER / work ratio |',
        '|---|---:|---:|']
    for code in ['en', 'ko', 'zh', 'fr', 'de', 'ru', 'ar']:
        lines.append('| ' + ' | '.join([code] + [aggregate(final, code, c) for c in ['clean', 'white-noise-10dB']]) + ' |')
    lines += ['', 'This same-selection regression measures the implemented route after development;',
        'it is not a new holdout. Post-call memory snapshots cannot establish peak memory.']
if args.automatic:
    automatic = json.loads(args.automatic.read_text(encoding='utf-8-sig'))
    assert automatic['completed'] and automatic['dataset'] == baseline['dataset']
    lines += ['', '## Automatic-language acoustic comparison', '',
        'The same 28 clips were decoded clean/noisy with multilingual Whisper Tiny',
        'in detection mode and with Omnilingual, without a forced language: 112 calls.',
        'These raw-model measurements predate short-utterance refinement.', '',
        '| Language | Whisper clean | Whisper noisy | Omni clean | Omni noisy |',
        '|---|---:|---:|---:|---:|']
    for code in ['en', 'ko', 'zh', 'fr', 'de', 'ru', 'ar']:
        values = []
        for backend in ['whisper-auto', 'omni-auto']:
            subset = {'results': [r for r in automatic['results'] if r['backend'] == backend]}
            values += [aggregate(subset, code, c) for c in ['clean', 'white-noise-10dB']]
        lines.append('| ' + ' | '.join([code] + values) + ' |')
if args.commands:
    commands = json.loads(args.commands.read_text(encoding='utf-8-sig'))['results']
    assert len(commands) == 34 and all(r['safe'] for r in commands)
    lines += ['', '## Short Automatic commands: measured limits', '',
        f"Final hybrid route: **{sum(r['passed'] for r in commands)}/34** synthetic commands had the expected action/time;",
        'the earlier Omni-only pass recognized 22/34. These are development fixtures,',
        'not a natural-conversation success rate. Every final fixture rejected wrong',
        'actions/times. Three unresolved Arabic clock cases remain visible below.', '',
        'Queue-clear speech under five seconds may be read again from the original PCM',
        'using installed SenseVoice/Whisper packs, guided by conservative script and',
        'grammatical clues in the primary output. The app does not choose a decoder',
        'based on whether its output makes an event. Missing optional packs keep the',
        'primary reading; the 31/34 result used all optional packs. No cloud is used.', '',
        '| Unresolved fixture | Actual recognition |', '|---|---|']
    for r in commands:
        if not r['passed']:
            lines.append(f"| {r['file']} | {r['recognized'].replace('|', '/')} |")
    lines += ['', 'Whisper Tiny/Base Arabic alternatives were also tried (16 calls); neither',
        'solved the short-clock problem. A host-only 458 MB NVIDIA Arabic FastConformer',
        'CTC export was evaluated on eight commands and four public readings. It',
        'still malformed clock words and showed no reliable aggregate command benefit,',
        'so it is not shipped. Host runtime speed is not Pixel performance.', '',
        '[NVIDIA source model, CC BY 4.0](https://huggingface.co/nvidia/stt_ar_fastconformer_hybrid_large_pc_v1.0),',
        '[public ONNX export](https://huggingface.co/OpenVoiceOS/stt_ar_fastconformer_hybrid_large_pc_v1.0_onnx).',
        'The frozen export revision/hash and candidate outputs are in evaluation/arabic-candidate.json.']
if args.live:
    live = json.loads(args.live.read_text(encoding='utf-8-sig'))
    lines += ['', '## Paced combined pipeline', '',
        f"Seven public languages with quiet gaps emitted {len(live['results'])} regions; final drain {live['queueDrainMs']/1000:.3f} seconds.",
        'The physical Pixel ran real-time-paced VAD, neural speaker partitioning,',
        'anonymous attribution and Automatic ASR together without opening a microphone.',
        'No owner was enrolled in this isolated profile. This does not test within-sentence',
        'code switching, user voice matching, live acoustics or unplugged endurance.']
if args.recovery:
    recovery = json.loads(args.recovery.read_text(encoding='utf-8-sig'))
    assert recovery['completed'] and recovery['regrantDidNotRearm']
    lines += ['', '## Silent capture and interruption recovery', '',
        'A no-host-audio emulator completed six minutes screen-off without ASR chunks',
        'or a false stall. Digital zero samples are valid silence; a five-second absence',
        'of PCM reads or a native read error explicitly stops capture. Global microphone',
        'mute also stopped capture and produced a generic private interruption notice.',
        f"A verified own-process kill recovered the armed assistant session after {recovery['processRecoveryObservedMs']/1000:.3f} seconds (host observation).",
        f"Microphone permission revocation stopped and disarmed it after {recovery['revocationStoppedAndDisarmedMs']/1000:.3f} seconds.",
        'Granting permission again did not restart it. This is not a force-stop bypass',
        'or a guarantee across device vendors. Capture permission checks run four times',
        'per second instead of querying recording configuration on every PCM poll.',
        'Run scripts/test_capture_recovery.ps1 only against a no-audio emulator.']
args.output.parent.mkdir(parents=True, exist_ok=True)
args.output.write_text('\n'.join(lines) + '\n', encoding='utf-8')
print(args.output)
