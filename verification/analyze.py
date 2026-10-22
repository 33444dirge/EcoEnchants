"""Recompute medians from exact-JAR platform-thread benchmark logs."""
from collections import defaultdict
from pathlib import Path
import json
import re
import statistics

task = Path(__file__).resolve().parents[2]
folder = task / 'verification'
groups = defaultdict(list)
runs = []
for name in __import__('sys').argv[1:] or ['run11', 'run12']:
    text = (folder / (name + '-server.log')).read_text(encoding='utf-8')
    assert '[EE] BENCH_DONE' in text and '[EE] FAIL' not in text, name
    rows = re.findall(r'\[EE\] METRIC fixture=(\S+) op=(\S+) version=(\S+) round=(\d+) count=(\d+) bytes=(\d+) nanos=(\d+)', text)
    assert len(rows) == 720, (name, len(rows))
    for fixture, op, version, rnd, count, allocated, nanos in rows:
        groups[fixture, op, version].append({'run': name, 'round': int(rnd), 'count': int(count),
                                           'bytesPerOp': int(allocated)/int(count), 'nanosPerOp': int(nanos)/int(count)})
    runs.append({'name': name, 'rows': len(rows), 'behavior': re.findall(r'\[EE\] BEHAVIOR_DONE player=(\S+) checks=(\d+) matrix=(\d+)', text)})
summary = []
for fixture, op in sorted({(f, o) for f, o, v in groups}):
    entry = {'fixture': fixture, 'operation': op}
    for version in ['old', 'new']:
        entry[version] = {key: statistics.median([r[key] for r in groups[fixture, op, version]]) for key in ['bytesPerOp', 'nanosPerOp']}
    entry['allocationReductionPercent'] = 100*(1-entry['new']['bytesPerOp']/entry['old']['bytesPerOp']) if entry['old']['bytesPerOp'] else None
    entry['timeReductionPercent'] = 100*(1-entry['new']['nanosPerOp']/entry['old']['nanosPerOp'])
    summary.append(entry)
result = {'method': 'Alternating exact original/candidate methods in one JVM; 8 warmup and 12 measured rounds per version per case; medians of per-round means. Detached items on a platform thread; display/revert include clone cost; display benchmark has null player. Not production CPU/MSPT/heap savings.',
          'runs': runs, 'summary': summary,
          'raw': [{'fixture': f, 'operation': o, 'version': v, 'samples': r} for (f, o, v), r in groups.items()]}
(folder / 'results.json').write_text(json.dumps(result, ensure_ascii=False, indent=2), encoding='utf-8')
for r in summary:
    print(f"{r['fixture']:15s} {r['operation']:12s} {r['old']['bytesPerOp']:8.1f} -> {r['new']['bytesPerOp']:8.1f} B/op; allocation {r['allocationReductionPercent']}; time {r['timeReductionPercent']:.2f}%")
