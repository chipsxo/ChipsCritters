from checklegs48 import *
from collections import Counter
items=json.loads(Path('work/audit56-map.json').read_text())
@functools.lru_cache(None)
def clearance(a,b):
 n=max(1,math.ceil(math.dist(a,b)*4))
 for lift in (0,.1,.2,.3,.4,.5,.6,.7,.8,.9,1,1.1,1.2,1.3,1.4,1.5):
  bad=None;unknown=False
  for i in range(n+1):
   for dx,dz in ((0,0),(.14,.14),(-.14,-.14),(.14,-.14),(-.14,.14)):
    q=tuple(math.floor(a[k]+(b[k]-a[k])*i/n+o) for k,o in enumerate((dx,.2+lift,dz)));name=B(q)
    if name=='?':unknown=True
    elif solid(q) and not name.endswith('_carpet'):bad=(q,name);break
   if bad:break
  if not bad:return (not unknown,'unknown cached terrain' if unknown else '',None)
 return False,'cached full-block collision',bad
report=[]
for o in items:
 path=[tuple(p) for p in o['path']];reason=[]
 if not path:reason=['no biome-bounded mapped topology path']
 else:
  for a,b in zip(path,path[1:]):
   ok,why,where=clearance(a,b)
   if not ok:reason=[why,dict(edge=[a,b],obstruction=where)];break
 o['cachedResult']='candidate-clear' if not reason else 'unverified'
 o['reason']=reason
 report.append(o)
Path('work/audit56-terrain.json').write_text(json.dumps(report,indent=1))
print(Counter((o['biome'],o['cachedResult']) for o in report))
print('unique failed pairs',len({(o['biome'],str(o['from']),str(o['to'])) for o in report if o['reason']}))
for o in report:
 if o['biome']=='CAVERN' and o['mode']=='MONEY' and o['stop']<=5:print(o['stop'],o['kind'],o['from'],o['to'],o['reason'])
