"""Show DEV items of a dataset with a given signal string. .deps/python312/python.exe -I scam_show.py TAG DATASET pos|neg 'SIG,LIST' [n] [mode]"""
import os, random, sys
sys.path.insert(0, os.path.dirname(__file__))
import scam_eval as se
tag, ds, truth, sig = sys.argv[1:5]
n = int(sys.argv[5]) if len(sys.argv) > 5 else 8
mode = sys.argv[6] if len(sys.argv) > 6 else 'all'
sys.stdout.reconfigure(encoding='utf-8')
items, res = se.load(tag)
ids = [i for i, it in items.items() if it['ds'] == ds and it['split'] == 'dev' and it['truth'] == truth and mode in res.get(i, {}) and res[i][mode][4] == sig]
random.Random(7).shuffle(ids)
for i in ids[:n]:
    print(' / '.join(u.split(se.SEP_S, 1)[1] for u in items[i]['utts'].split(se.SEP_U))[:330])
