# SogaKey: turn the noun entries of Mozc's open-source dictionary into an extra Anthy word list
import re,glob,sys,collections
# usage: build_mozc_extra.py <mozc>/src/data/dictionary_oss/ <anthy-unicode source dir>/ <out.t> [max_cost]
MOZC=sys.argv[1]
ANTHY=sys.argv[2]
OUT=sys.argv[3]
MAXCOST=int(sys.argv[4]) if len(sys.argv)>4 else 7000
# existing Anthy (yomi,surface)
have=set()
files=['alt-cannadic/gcanna.ctd','alt-cannadic/gcannaf.ctd','alt-cannadic/gtankan.ctd','alt-cannadic/extra/g-jiritu-34.t','alt-cannadic/extra/gc-fullname-34.t','alt-cannadic/g_fname.t','mkworddic/extra.t','mkworddic/name.t','mkworddic/adjust.t','mkworddic/compound.t']
for f in files:
    for l in open(ANTHY+f,encoding='utf-8'):
        if l.startswith('#') or not l.strip(): continue
        t=l.split(); y=t[0]
        for x in t[1:]:
            if x.startswith('#') and re.match(r'#[A-Za-z0-9]',x): continue
            have.add((y,re.sub(r'#_\d','',x)))
pos={}
for l in open(MOZC+'id.def',encoding='utf-8'):
    i,_,r=l.rstrip('\n').partition(' '); pos[int(i)]=r.split(',')
def tag(p):
    if p[0]!='名詞': return None
    s=p[1]
    if s=='一般': return 'T35'
    if s=='サ変接続': return 'T30'
    if s=='固有名詞':
        sub=p[2] if len(p)>2 else ''
        if sub=='地域': return 'CN'
        if sub=='人名': return 'JN'
        return 'T35'   # organisations, other proper nouns
    return None
hira=re.compile(r'^[ぁ-ゖー]+$')
out=collections.defaultdict(list); n=0; skipped_have=0
for f in sorted(glob.glob(MOZC+'dictionary0*.txt')):
    for l in open(f,encoding='utf-8'):
        p=l.rstrip('\n').split('\t')
        if len(p)<5: continue
        y,lid,rid,cost,s=p[0],int(p[1]),int(p[2]),int(p[3]),p[4]
        if cost>MAXCOST or not hira.match(y) or len(y)>20 or len(s)>20: continue
        if ' ' in s or '#' in s or '\\' in s: continue
        t=tag(pos[lid])
        if not t: continue
        if (y,s) in have: skipped_have+=1; continue
        have.add((y,s))
        # cannadic frequency: lower mozc cost -> higher freq (scale 1..300)
        freq=max(1,min(300,int((MAXCOST-cost)/MAXCOST*300)))
        out[(y,t)].append((freq,s)); n+=1
with open(OUT,'w',encoding='utf-8') as o:
    for (y,t),items in sorted(out.items()):
        items.sort(reverse=True)
        o.write(y+' '+' '.join('#%s*%d %s'%(t,f,s) for f,s in items)+'\n')
print('new entries',n,'already in anthy',skipped_have)
