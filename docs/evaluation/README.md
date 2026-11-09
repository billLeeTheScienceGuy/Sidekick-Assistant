# Public fixture evidence

These JSON files contain public/synthetic references, recognized outputs and
measurements, not private user transcripts, recordings or biometric signatures.
FLEURS references and metadata are CC BY 4.0; attribution, pinned dataset revision,
selection rule and converted audio hashes are in `robustness-selection.json`.
Audio remains in ignored test assets and is excluded from the normal APK/bundle.
Synthetic commands use the existing reproducible speech-fixture scripts.

See ../ROBUSTNESS_EVALUATION.md for methods, development-data limitations and
failed Arabic cases. Wall times include model preparation/reuse; snapshots are
not peak memory. Powered-device tests do not measure unplugged battery life.

Regenerate the report from this repository root:

```powershell
python scripts/report_robustness.py --baseline docs/evaluation/asr-baseline.json --candidate docs/evaluation/asr-candidate.json --speakers docs/evaluation/speaker-trials.json --final docs/evaluation/asr-final.json --automatic docs/evaluation/automatic-models.json --commands docs/evaluation/automatic-commands.json --live docs/evaluation/automatic-live.json --recovery docs/evaluation/capture-recovery.json
```

Public replay uses the isolated benchmark package only, built with
`-PspeechBenchmark`. Prepare test audio with `prepare_robustness_fixtures.py` and
the existing command/voice preparation scripts. Use `RobustnessEvaluationTest`
with `-e runRobustness true` for opt-in expensive measurements; the raw Automatic
comparison took over thirteen minutes. `AutomaticLanguageTest` exposes separate
short-command, combined paced pipeline and Arabic-alternative methods so known
experiments need not be repeated for ordinary regressions.

`capture-recovery.json` comes from `scripts/test_capture_recovery.ps1`, which
requires an isolated emulator with host audio disabled and restores its assistant
role/permission. Never use that script or instrumented tests on the personal app.
The Arabic candidate is a rejected host-only experiment; its report records the
public model revision, license and weight SHA-256. It adds no app dependency.
