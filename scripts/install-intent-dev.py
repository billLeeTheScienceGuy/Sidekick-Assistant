"""Copy the verified model into a debuggable app over adb, without bundling it in APKs."""
import argparse
import hashlib
from pathlib import Path
import re
import subprocess

parser = argparse.ArgumentParser()
parser.add_argument("--adb", required=True)
parser.add_argument("--package", default="com.sidekick.app")
args = parser.parse_args()
if not re.fullmatch(r"[a-zA-Z0-9_.]+", args.package):
    raise ValueError("Invalid Android package")
model = Path(__file__).resolve().parents[1] / ".deps/Qwen3-0.6B.Q4_K_M.gguf"
expected = "7af3fdf842f87b24672f8a7f1dd50404043f0bfb71093ff91c31d2b49df4631d"
with model.open("rb") as stream:
    if hashlib.file_digest(stream, "sha256").hexdigest() != expected:
        raise ValueError("Intent model checksum mismatch")
subprocess.run([args.adb, "shell", "run-as", args.package, "mkdir", "-p", "no_backup"], check=True)
temporary = "no_backup/qwen3.import"
command = f"run-as {args.package} sh -c 'cat > {temporary}'"
with model.open("rb") as stream:
    subprocess.run([args.adb, "exec-in", command], stdin=stream, check=True)
digest = subprocess.check_output([args.adb, "shell", "run-as", args.package, "sha256sum", temporary], text=True).split()[0]
if digest != expected:
    subprocess.run([args.adb, "shell", "run-as", args.package, "rm", temporary], check=True)
    raise ValueError("Transferred model checksum mismatch")
subprocess.run([args.adb, "shell", "run-as", args.package, "mv", temporary, "no_backup/qwen3-0.6b-q4_k_m.gguf"], check=True)
subprocess.run([args.adb, "shell", "run-as", args.package, "chmod", "600", "no_backup/qwen3-0.6b-q4_k_m.gguf"], check=True)
print(f"Verified intent model installed for {args.package}. Restart the app to detect it.")
