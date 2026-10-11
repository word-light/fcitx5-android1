# Build a TSV of common words (jieba frequency list) with Dachen key sequences
import re, sys
from pypinyin import pinyin, Style
KEY = {"ㄅ":"1","ㄆ":"q","ㄇ":"a","ㄈ":"z","ㄉ":"2","ㄊ":"w","ㄋ":"s","ㄌ":"x","ㄍ":"e","ㄎ":"d","ㄏ":"c","ㄐ":"r","ㄑ":"f","ㄒ":"v","ㄓ":"5","ㄔ":"t","ㄕ":"g","ㄖ":"b","ㄗ":"y","ㄘ":"h","ㄙ":"n","ㄧ":"u","ㄨ":"j","ㄩ":"m","ㄚ":"8","ㄛ":"i","ㄜ":"k","ㄝ":",","ㄞ":"9","ㄟ":"o","ㄠ":"l","ㄡ":".","ㄢ":"0","ㄣ":"p","ㄤ":";","ㄥ":"/","ㄦ":"-"}
TONE = {"ˊ":"6","ˇ":"3","ˋ":"4"}
cjk = re.compile(r'^[一-鿿]+$')
out = []
for line in open(sys.argv[1], encoding='utf-8'):
    p = line.split()
    if len(p) < 2 or not p[1].isdigit(): continue
    w, f = p[0], int(p[1])
    if not cjk.match(w) or not (2 <= len(w) <= 4) or f < int(sys.argv[3]): continue
    keys = ''
    ok = True
    for syl in pinyin(w, style=Style.BOPOMOFO, strict=False, errors=lambda x: None):
        s = syl[0] if syl else ''
        if not s: ok = False; break
        neutral = s.startswith('˙')
        s = s.replace('˙', '')
        tone = ' '
        for ch in list(s):
            if ch in TONE: tone = TONE[ch]
        body = ''.join(KEY.get(ch, '') for ch in s if ch in KEY)
        if not body: ok = False; break
        keys += body + ('7' if neutral else tone)
    if ok: out.append((f, w, keys))
out.sort(reverse=True)
with open(sys.argv[2], 'w', encoding='utf-8') as fh:
    for f, w, k in out: fh.write(f"{w}\t{k}\t{f}\n")
print(len(out), "words")
