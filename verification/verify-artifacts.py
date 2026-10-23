"""Read-only input checks; writes a reviewable JSON report alongside test logs."""
from pathlib import Path
import difflib
import hashlib
import json
import re
import subprocess
import zipfile
import xml.etree.ElementTree as ET

task = Path(__file__).resolve().parents[2]
repo = task / 'repo'
folder = task / 'verification'
original = task / 'baseline.jar'
rebuilt = task / 'mcfx-rebuilt-baseline.jar'
candidate = repo / 'build/libs/EcoEnchants-2026.27-1-all.jar'

def sha(p):
    return hashlib.sha256(p.read_bytes()).hexdigest()

def javap(p, cls, verbose=False):
    return subprocess.check_output(['D:/java/bin/javap.exe', '-p', '-v' if verbose else '-c',
        '-s', '-constants', '-classpath', str(p), cls], text=True, encoding='utf-8', errors='replace')

def instructions(p, cls):
    return re.sub(r'#\d+', '#CP', javap(p, cls))

result = {'baselineSha256': sha(original), 'candidateSha256': sha(candidate),
          'sourceBranch': 'mcfx', 'sourceCommit': '84e4e1fd6d0760530eed521cbf3e1bc27b5cb18e'}
with zipfile.ZipFile(original) as a, zipfile.ZipFile(rebuilt) as b, zipfile.ZipFile(candidate) as c:
    project = [n for n in a.namelist() if n.startswith('com/willfp/ecoenchants/') and n.endswith('.class') and '/libreforge/' not in n]
    equal = [n for n in project if a.read(n) == b.read(n)]
    differing = [n for n in project if a.read(n) != b.read(n)]
    checks = {n: instructions(original, n[:-6].replace('/', '.')) == instructions(rebuilt, n[:-6].replace('/', '.')) for n in differing}
    assert len(project) == 178 and len(equal) == 175 and all(checks.values())
    result['baselineSourceEvidence'] = {'classes': len(project), 'byteIdentical': len(equal), 'instructionEquivalent': checks}
    diffs = []
    for n in differing:
        cls = n[:-6].replace('/', '.')
        diffs.extend(difflib.unified_diff(javap(original, cls, True).splitlines(True), javap(rebuilt, cls, True).splitlines(True), fromfile='production/' + cls, tofile='mcfx-rebuild/' + cls))
    (folder / 'baseline-debug-diff.txt').write_text(''.join(diffs), encoding='utf-8')
    changed = [n for n in a.namelist() if n not in c.namelist() or a.read(n) != c.read(n)]
    result['candidateChangedEntries'] = changed
    result['candidateAddedEntries'] = [n for n in c.namelist() if n not in a.namelist()]
    result['effectiveChangedClasses'] = [n for n in changed if n.endswith('.class') and instructions(original, n[:-6].replace('/', '.')) != instructions(candidate, n[:-6].replace('/', '.'))]
    result['unchangedClassCount'] = sum(a.read(n) == c.read(n) for n in a.namelist() if n.endswith('.class') and n in c.namelist())
    resource_checks = {}
    paths = subprocess.check_output(['git', '-C', str(repo), 'ls-tree', '-r', '--name-only', result['sourceCommit']], text=True).splitlines()
    for p in paths:
        if '/src/main/resources/' not in p:
            continue
        n = p.split('/src/main/resources/', 1)[1]
        source = subprocess.check_output(['git', '-C', str(repo), 'show', result['sourceCommit'] + ':' + p]).decode('utf-8-sig').replace('\r\n', '\n')
        for token, value in {'${pluginName}': 'EcoEnchants', '${version}': '2026.27', '${libreforgeVersion}': '2026.27'}.items():
            source = source.replace(token, value)
        before = a.read(n).decode('utf-8-sig').replace('\r\n', '\n')
        after = c.read(n).decode('utf-8-sig').replace('\r\n', '\n')
        if n == 'plugin.yml':
            after = after.replace('version: 2026.27-1\n', 'version: 2026.27\n')
        resource_checks[n] = {'productionVsMcfx': before == source, 'candidateVsProductionExceptVersion': after == before}
    assert all(all(r.values()) for r in resource_checks.values())
    result['resources'] = resource_checks

result['tests'] = [{k: ET.parse(p).getroot().attrib[k] for k in ['name', 'tests', 'failures', 'errors', 'skipped']}
                   for p in (repo / 'eco-core/core-plugin/build/test-results/test').glob('TEST-*.xml')]
assert sum(int(r['tests']) for r in result['tests']) == 9
assert all(int(r['failures']) == int(r['errors']) == int(r['skipped']) == 0 for r in result['tests'])
provided = Path('C:/Users/Administrator/Desktop/eco系列')
result['suppliedInputs'] = [{'file': p.name, 'sha256': sha(p), 'bytes': p.stat().st_size} for p in provided.iterdir() if p.is_file()]
lab = task / 'lab-runtime'
result['runtimeArtifacts'] = [{'file': str(p.relative_to(lab)), 'sha256': sha(p)}
    for p in list((lab / 'plugins').glob('*.jar')) + list((lab / 'versions').rglob('*.jar'))]
(folder / 'artifact-verification.json').write_text(json.dumps(result, ensure_ascii=False, indent=2), encoding='utf-8')
print(json.dumps({k: v for k, v in result.items() if k in ['baselineSha256', 'candidateSha256', 'effectiveChangedClasses', 'unchangedClassCount', 'tests']}, ensure_ascii=False, indent=2))
