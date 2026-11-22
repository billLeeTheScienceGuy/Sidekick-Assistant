"""Runs the real Kotlin scam rules (core ScamCorpusEvalTest) over the prepared public corpora and reports the numbers.

  .deps/python312/python.exe -I scripts/laptop-eval/scam_eval.py --run --tag baseline          # run rules, print dev + test tables
  .deps/python312/python.exe -I scripts/laptop-eval/scam_eval.py --tag baseline --splits dev   # re-print only
  .deps/python312/python.exe -I scripts/laptop-eval/scam_eval.py --tag x --errors 30 --kind miss|alarm   # dev errors only

Prepare the data first with scam_prepare.py. Item text of the TEST split is never printed (so rule changes can only be
guided by dev errors). "recall" = share of positive items (scams / spam) that reached CAUTION or STOP; "STOP" = reached STOP;
"false alarm" = share of ordinary items that reached CAUTION or STOP at any step; "per step" = share of steps (utterances
or messages) after which a warning was showing.
"""
import argparse, collections, os, subprocess, sys

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), '..', '..'))
PREP = os.path.join(ROOT, '.deps', 'scam-data', '_prepared')
SEP_U, SEP_S = '\u241e', '\u241f'


def load(tag):
    items = {}
    with open(os.path.join(PREP, 'corpus.tsv'), encoding='utf-8') as f:
        for line in f:
            p = line.rstrip('\n').split('\t')
            items[p[5]] = dict(ds=p[0], lang=p[1], kind=p[2], truth=p[3], split=p[4], utts=p[6])
    res = collections.defaultdict(dict)
    with open(os.path.join(PREP, 'results-%s.tsv' % tag), encoding='utf-8') as f:
        next(f)
        for line in f:
            i, mode, mx, steps, warned, stops, sig = line.rstrip('\n').split('\t')
            res[i][mode] = (int(mx), int(steps), int(warned), int(stops), sig)
    return items, res


def pct(a, b):
    return '%5.1f%% %d/%d' % (100.0 * a / b, a, b) if b else '    -'


def table(items, res, split):
    agg = collections.defaultdict(lambda: collections.Counter())
    for i, it in items.items():
        if it['split'] != split or i not in res:
            continue
        for mode, (mx, steps, warned, stops, sig) in res[i].items():
            for key in ((it['ds'], it['lang'], mode), ('ALL', it['lang'], mode)):
                c = agg[key]
                if it['truth'] == 'pos':
                    c['pos'] += 1; c['any'] += mx >= 1; c['stop'] += mx >= 2
                else:
                    c['neg'] += 1; c['fa'] += mx >= 1; c['fastop'] += mx >= 2; c['steps'] += steps; c['wsteps'] += warned
    print('== %s split ==' % split)
    print('%-18s %-3s %-5s | %-17s %-17s | %-17s %-15s %-17s' % ('dataset', 'lng', 'mode', 'recall any', 'recall STOP', 'false alarm', 'FA STOP', 'FA per step'))
    for key in sorted(agg, key=lambda k: (k[0] == 'ALL', k)):
        c = agg[key]
        print('%-18s %-3s %-5s | %-17s %-17s | %-17s %-15s %-17s' % (key + (pct(c['any'], c['pos']), pct(c['stop'], c['pos']), pct(c['fa'], c['neg']),
              pct(c['fastop'], c['neg']), pct(c['wsteps'], c['steps']))))


a_len = 300


def errors(items, res, kind, n, mode='all', ds=None, seed=1):
    import random
    shown = 0
    order = list(items)
    random.Random(seed).shuffle(order)
    for i in order:
        it = items[i]
        if it['split'] != 'dev' or i not in res or mode not in res[i] or (ds and it['ds'] != ds):
            continue
        mx, steps, warned, stops, sig = res[i][mode]
        bad = (kind == 'miss' and it['truth'] == 'pos' and mx == 0) or (kind == 'alarm' and it['truth'] == 'neg' and mx >= 1)
        if bad:
            text = ' / '.join(u.split(SEP_S, 1)[1] for u in it['utts'].split(SEP_U))
            print('[%s %s max=%d] %s -> %s' % (it['ds'], it['lang'], mx, text[:a_len], sig))
            shown += 1
            if shown >= n:
                break


if __name__ == '__main__':
    ap = argparse.ArgumentParser()
    ap.add_argument('--tag', default='latest')
    ap.add_argument('--run', action='store_true', help='run the Kotlin rules first (gradle, ~1 minute)')
    ap.add_argument('--splits', default='dev,test')
    ap.add_argument('--errors', type=int, default=0)
    ap.add_argument('--kind', default='miss', choices=['miss', 'alarm'])
    ap.add_argument('--mode', default='all')
    ap.add_argument('--ds', default=None)
    ap.add_argument('--seed', type=int, default=1)
    a = ap.parse_args()
    if a.run:
        env = dict(os.environ, SCAM_CORPUS='1', SCAM_TAG=a.tag, JAVA_HOME=os.environ.get('JAVA_HOME', 'C:/Users/Patron/.jdks/jbr-17.0.14'))
        gradlew = os.path.join(ROOT, 'gradlew.bat' if os.name == 'nt' else 'gradlew')
        r = subprocess.run([gradlew, '-q', ':core:cleanTest', ':core:test', '--tests', 'com.sidekick.core.ScamCorpusEvalTest'], cwd=ROOT, env=env)
        if r.returncode:
            sys.exit(r.returncode)
    sys.stdout.reconfigure(encoding='utf-8')
    items, res = load(a.tag)
    if a.errors:
        errors(items, res, a.kind, a.errors, a.mode, a.ds, a.seed)
    else:
        for s in a.splits.split(','):
            table(items, res, s)
