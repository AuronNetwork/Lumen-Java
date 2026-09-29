#!/usr/bin/env python3
"""Package the built Java mod and its complete buildable source; no game files."""
from pathlib import Path
import argparse
import hashlib
import json
import re
import shutil
import subprocess
import zipfile

root = Path(__file__).resolve().parents[1]
parser = argparse.ArgumentParser()
parser.add_argument('--output', type=Path, default=root / 'dist')
args = parser.parse_args()
out = args.output.resolve()
version = re.search(r'^mod_version=(.+)$', (root / 'gradle.properties').read_text(), re.M)[1].strip()
jar_name = f'lumen-java-{version}.jar'
jar = root / 'build/libs' / jar_name
with zipfile.ZipFile(jar) as z:
    assert z.testzip() is None
    meta = json.loads(z.read('fabric.mod.json'))
    assert meta['version'] == version and meta['depends']['minecraft'] == '26.3'
    assert meta['environment'] == 'client'
    assert all(n in z.namelist() for n in ('LICENSE-Lumen', 'NOTICE-Lumen.md', 'lumen.mixins.json', 'assets/lumen/icon.png'))
    assert not any(n.startswith(('net/minecraft/', 'com/mojang/')) or (n.endswith('.class') and 'Test' in n) for n in z.namelist())
out.mkdir(parents=True, exist_ok=True)
shutil.copy2(jar, out / jar_name)
for name in ('README.md', 'LICENSE', 'NOTICE.md', 'TESTING.md'):
    shutil.copy2(root / name, out / name)

source_name = f'Lumen-Java-{version}-Source.zip'
files = [root / n for n in ('.gitignore', '.gitattributes', 'README.md', 'LICENSE', 'NOTICE.md', 'TESTING.md', 'build.gradle',
                           'settings.gradle', 'gradle.properties', 'gradlew', 'gradlew.bat')]
for directory in ('src', 'gradle', 'scripts', '.github'):
    files.extend(p for p in (root / directory).rglob('*') if p.is_file() and '__pycache__' not in p.parts)
try:
    commit = subprocess.check_output(['git', '-C', str(root), 'rev-parse', 'HEAD'], text=True).strip()
except (OSError, subprocess.CalledProcessError):
    commit = 'unavailable'
with zipfile.ZipFile(out / source_name, 'w', zipfile.ZIP_DEFLATED) as z:
    for p in sorted(files):
        rel = p.relative_to(root).as_posix()
        assert not rel.endswith(('.class', '.exe', '.dll'))
        assert not rel.endswith('.jar') or rel == 'gradle/wrapper/gradle-wrapper.jar'
        z.write(p, 'Lumen-Java/' + rel)
    z.writestr('Lumen-Java/RELEASE-COMMIT.txt', commit + '\n')
with zipfile.ZipFile(out / source_name) as z:
    assert z.testzip() is None
    for p in files:
        assert z.read('Lumen-Java/' + p.relative_to(root).as_posix()) == p.read_bytes()
sums = ''.join(hashlib.sha256((out / n).read_bytes()).hexdigest() + '  ' + n + '\n' for n in (jar_name, source_name))
(out / 'SHA256SUMS.txt').write_text(sums, encoding='utf-8')
print(f'JAVA_PACKAGE_OK {version}; source files={len(files)}; source commit={commit}')
print(sums, end='')
