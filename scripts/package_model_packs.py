"""Package verified offline weights. Run preparation scripts first; output stays ignored."""
from pathlib import Path
import hashlib
import zipfile

root = Path(__file__).resolve().parents[1]
assets = root / 'app/src/main/assets'
groups = {}
for line in (assets / 'model-packs.tsv').read_text().splitlines():
    pack, name, size, sha = line.split('\t')
    groups.setdefault(pack, []).append((name, int(size), sha))
out = root / '.deps/model-packs'
out.mkdir(parents=True, exist_ok=True)
for pack, entries in groups.items():
    target = out / f'{pack}.skmodels'
    temporary = target.with_suffix('.partial')
    try:
        with zipfile.ZipFile(temporary, 'w', compression=zipfile.ZIP_STORED) as archive:
            archive.writestr('sidekick-pack.txt', f'SideKick model pack v1\n{pack}\n')
            license_name = {'whisper': 'whisper-MIT.txt', 'sensevoice': 'sensevoice.txt', 'omni': 'omnilingual.txt', 'english': 'parakeet.txt'}[pack]
            archive.writestr('LICENSE.txt', (assets / 'licenses' / license_name).read_text(encoding='utf-8'))
            for name, size, expected in entries:
                source = assets / 'models' / name
                if source.stat().st_size != size:
                    raise ValueError(f'Wrong size: {name}')
                digest = hashlib.sha256()
                with source.open('rb') as stream, archive.open(name, 'w') as output:
                    while chunk := stream.read(1024 * 1024):
                        digest.update(chunk)
                        output.write(chunk)
                if digest.hexdigest() != expected:
                    raise ValueError(f'Wrong checksum: {name}')
        temporary.replace(target)
        print(f'{target.name}: {target.stat().st_size:,} bytes')
    finally:
        temporary.unlink(missing_ok=True)
