"""Which signal combinations fire on DEV items of a dataset (guides rule changes without printing test text).
  .deps/python312/python.exe -I scripts/laptop-eval/scam_signals_stats.py TAG DATASET pos|neg [mode]"""
import collections, os, sys
sys.path.insert(0, os.path.dirname(__file__))
import scam_eval as se
tag, ds, truth = sys.argv[1:4]
mode = sys.argv[4] if len(sys.argv) > 4 else 'all'
items, res = se.load(tag)
c = collections.Counter()
for i, it in items.items():
    if it['ds'] == ds and it['split'] == 'dev' and it['truth'] == truth and mode in res.get(i, {}):
        mx, _, _, _, sig = res[i][mode]
        c[(mx, sig)] += 1
for k, v in c.most_common(25):
    print(v, k)
