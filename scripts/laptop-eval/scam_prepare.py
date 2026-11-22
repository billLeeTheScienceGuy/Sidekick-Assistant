"""Turns the downloaded public corpora in .deps/scam-data/ into one tab-separated file for the Kotlin rule runner.

Run:  .deps/python312/python.exe -I scripts/laptop-eval/scam_prepare.py
Needs .deps/pyarrow-tools (parquet). Downloaded files are untrusted data: this script only parses them.

Output: .deps/scam-data/_prepared/corpus.tsv with columns
  dataset, lang, kind (conv|msg), truth (pos|neg), split (dev|test), id, utterances
where utterances are joined by U+241E and each is "side U+241F text" (side: A caller/other, B phone owner, ? unknown).
The dev/test split is fixed by SHA-1 of (SEED + key) BEFORE any result is looked at; key is the normalised text for
messages (so duplicates fall in one split and are kept once) or the conversation id.
"""
import csv, gzip, hashlib, json, os, random, re, sys, zipfile, collections

sys.path.insert(0, '.deps/pyarrow-tools')
csv.field_size_limit(10 ** 9)
SEED = 'sidekick-scam-eval-1'
B = '.deps/scam-data/'
OUT = B + '_prepared/'
SEP_U, SEP_S = '␞', '␟'
rows = []
seen = set()


def split_of(key):
    return 'dev' if hashlib.sha1((SEED + key).encode('utf-8')).digest()[0] % 2 == 0 else 'test'


def clean(t):
    return re.sub(r'\s+', ' ', t.replace(SEP_U, ' ').replace(SEP_S, ' ')).strip()


def norm(t):
    return re.sub(r'\W+', '', t.lower())


def add(ds, lang, kind, truth, key, utts, dedupe=True):
    utts = [(s, clean(t)) for s, t in utts if clean(t)]
    if not utts:
        return
    if dedupe:
        k = (ds, lang, key)
        if k in seen:
            return
        seen.add(k)
    rows.append((ds, lang, kind, truth, split_of(key), str(len(rows)), SEP_U.join(s + SEP_S + t for s, t in utts)))


def sentences(text):
    parts = re.split(r'(?<=[.!?。？！؟])\s*', text)
    return [p for p in parts if p.strip()]


def csv_rows(path, enc='utf-8-sig'):
    with open(path, encoding=enc, newline='') as f:
        return list(csv.DictReader(f))


# 1-2. UCI SMS Spam Collection (real English SMS) and its machine-translated multilingual version.
import pyarrow.parquet as pq
for r in pq.read_table(B + 'ucirvine_sms_spam/train-00000-of-00001.parquet').to_pylist():
    add('uci-sms', 'en', 'msg', 'pos' if str(r['label']) == '1' else 'neg', 'uci:' + norm(r['sms']), [('?', r['sms'])])
cols = {'de': 'text_de', 'fr': 'text_fr', 'zh': 'text_zh', 'ar': 'text_ar', 'ru': 'text_ru', 'ko': 'text_ko'}
for r in csv_rows(B + 'dbarbedillo_SMS_Spam_Multilingual_Collection_Dataset/data-augmented.csv'):
    truth = 'pos' if r['labels'] == 'spam' else 'neg'
    for lang, col in cols.items():
        add('sms-mt', lang, 'msg', truth, 'uci:' + norm(r['text']), [('?', r[col])])

# 3. Korean smishing messages (real; label 스미싱 = smishing, 정상 = normal).
for r in csv_rows(B + 'jmjmjm3_kor-smishing-message/smishing.csv'):
    if r['label'] in ('스미싱', '정상') and r['content'].strip():
        add('kor-smishing', 'ko', 'msg', 'pos' if r['label'] == '스미싱' else 'neg', 'ks:' + norm(r['content']), [('?', r['content'])])

# 4. Korean voice-phishing / normal call transcript segments (ASR text, no speaker labels).
for i, r in enumerate(pq.read_table(B + 'HyaDoo_ko-voicephishing-binary-classification-ver2/train-dataset-ver2.parquet').to_pylist()):
    add('ko-voicephishing', 'ko', 'conv', 'pos' if str(r['label']) == '1' else 'neg', 'kv:' + norm(r['text']),
        [('?', s) for s in sentences(r['text'])])

# 5. Chinese telecom-fraud / normal call transcripts (ASR text).
for r in csv_rows(B + 'leon202508_cn-antifraud/AntiFraudDataSetLite.csv'):
    add('cn-antifraud', 'zh', 'conv', 'pos' if r['isfraud'] == '1' else 'neg', 'cn:' + norm(r['txt']), [('?', s) for s in sentences(r['txt'])])


# 6-8. English scam-call dialogues.
def turns(text, tags):
    pat = '(' + '|'.join(re.escape(t) for t in tags) + r'):\s*'
    parts = re.split(pat, text)
    out = []
    for i in range(1, len(parts) - 1, 2):
        out.append((tags[parts[i]], parts[i + 1]))
    return out


for r in csv_rows(B + 'BothBosu_scam-dialogue/scam-dialogue_all.csv'):
    add('llm-scam-dialogue', 'en', 'conv', 'pos' if r['label'] == '1' else 'neg', 'bd:' + norm(r['dialogue']),
        turns(r['dialogue'], {'caller': 'A', 'receiver': 'B'}))
for r in csv_rows(B + 'BothBosu_Scammer-Conversation/gen_conver_noIdentifier_1000.csv'):
    add('llm-scammer-conv', 'en', 'conv', 'pos' if r['label'] == '1' else 'neg', 'sc:' + norm(r['conversation']),
        turns(r['conversation'], {'Person A': 'A', 'Person B': 'B'}))
for r in csv_rows(B + 'BothBosu_youtube-scam-conversations/youtube_scam_conversations.csv'):
    # Real recorded calls (scam-baiters). "Innocent" is the person called, "Suspect" the scammer.
    ut = []
    for line in r['dialogue'].split('\n'):
        m = re.match(r'(Innocent|Suspect):\s*(.*)', line.strip())
        if m:
            ut.append(('B' if m.group(1) == 'Innocent' else 'A', m.group(2)))
    add('youtube-scam-calls', 'en', 'conv', 'pos', 'yt:' + norm(r['dialogue'])[:200], ut)

# 9. Everyday English dialogue (DailyDialog; written conversations).
with zipfile.ZipFile(B + 'roskoN_dailydialog/train.zip') as z:
    name = [n for n in z.namelist() if n.endswith('dialogues_train.txt')][0]
    for i, line in enumerate(z.read(name).decode('utf-8').splitlines()):
        us = [u.strip() for u in line.split('__eou__') if u.strip()]
        add('dailydialog', 'en', 'conv', 'neg', 'dd:%d' % i, [('A' if j % 2 == 0 else 'B', u) for j, u in enumerate(us)])

# 10. Tatoeba sentences (real everyday sentences, 1000 per language).
for lang, f in {'ar': 'ara', 'zh': 'cmn', 'de': 'deu', 'fr': 'fra', 'ko': 'kor', 'ru': 'rus'}.items():
    with gzip.open(B + 'mteb_tatoeba-bitext-mining/%s-eng.jsonl.gz' % f, 'rt', encoding='utf-8') as fh:
        for line in fh:
            j = json.loads(line)
            add('tatoeba', lang, 'msg', 'neg', 'tt:' + lang + norm(j['sentence1']), [('?', j['sentence1'])])
            if lang == 'ar':
                add('tatoeba', 'en', 'msg', 'neg', 'tt:en' + norm(j['sentence2']), [('?', j['sentence2'])])

# 11. Movie-subtitle lines (spoken everyday dialogue lines; 4000 sampled per language, seeded).
rng = random.Random(SEED)
for lang, f, col in [('ko', 'en-ko', 'non_english'), ('zh', 'en-zh_cn', 'non_english'), ('ar', 'en-ar', 'non_english'), ('de', 'en-de', 'non_english'),
                     ('fr', 'en-fr', 'non_english'), ('ru', 'en-ru', 'non_english')]:
    p = B + 'sbert_opensubtitles/%s.parquet' % f
    if not os.path.exists(p):
        continue
    pf = pq.ParquetFile(p)
    lines = [t for g in range(min(3, pf.num_row_groups)) for t in pf.read_row_group(g, columns=[col]).column(col).to_pylist() if 8 <= len(t) <= 200]
    for t in rng.sample(lines, min(4000, len(lines))):
        add('subtitles', lang, 'msg', 'neg', 'st:' + lang + norm(t), [('?', t)])
    if lang == 'ko':
        en = [t for g in range(min(3, pf.num_row_groups)) for t in pf.read_row_group(g, columns=['english']).column('english').to_pylist() if 8 <= len(t) <= 200]
        for t in rng.sample(en, min(4000, len(en))):
            add('subtitles', 'en', 'msg', 'neg', 'st:en' + norm(t), [('?', t)])

# 12. Russian forum/chat lines (real; only the non-spam rows are used, as everyday text).
p = B + 'benzlokzik_russian-spam-fork/processed_combined.parquet'
if os.path.exists(p):
    t = pq.ParquetFile(p).read_row_group(0, columns=['text', 'label'])
    texts = [x for x, l in zip(t.column('text').to_pylist(), t.column('label').to_pylist()) if not l and 8 <= len(x) <= 300]
    for x in rng.sample(texts, 4000):
        add('ru-forum', 'ru', 'msg', 'neg', 'rf:' + norm(x), [('?', x)])

# 13-14. Synthetic (LLM-written) French and German spam / non-spam sentences.
for line in open(B + 'Kinoux_french-spam-ham-detection-free-2k/data.jsonl', encoding='utf-8'):
    if line.strip():
        j = json.loads(line)
        add('synthetic-spam', 'fr', 'msg', 'pos' if j['label'] == 'spam' else 'neg', 'fr:' + norm(j['text']), [('?', j['text'])])
for r in csv_rows(B + 'tanaos_synthetic-spam-detection-dataset-german/data.csv'):
    add('synthetic-spam', 'de', 'msg', 'pos' if r['labels'] == 'spam' else 'neg', 'de:' + norm(r['text']), [('?', r['text'])])

os.makedirs(OUT, exist_ok=True)
with open(OUT + 'corpus.tsv', 'w', encoding='utf-8', newline='\n') as f:
    for r in rows:
        f.write('\t'.join(r) + '\n')
c = collections.Counter((r[0], r[1], r[3], r[4]) for r in rows)
for k in sorted(c):
    print(*k, c[k])
print(len(rows), 'items')
