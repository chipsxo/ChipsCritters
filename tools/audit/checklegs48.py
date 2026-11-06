from map48 import *
import functools
# Approximate only fully occupied collision cells (not partial block shapes).
t=json.loads(Path("src/main/resources/data/chipscritters/terrain.json").read_text())
@functools.lru_cache(None)
def empty(p):
 n=B(p)
 return not solid(p) or n.endswith('_carpet')
@functools.lru_cache(None)
def edge(a,b):
 if a==b:return True
 if math.hypot(a[0]-b[0],a[2]-b[2])<.65 and abs(a[1]-b[1])>1.25 and not (a[0]==b[0]==18.5 and a[2]==b[2]==-71.5 and 77<=min(a[1],b[1])<=max(a[1],b[1])<=80) and not (-27.5<=a[0]<=-23 and -27.5<=b[0]<=-23 and 34<=a[2]<=36 and 34<=b[2]<=36):return False
 l=math.ceil(math.dist(a,b)*4)
 for lift in (0,.25,.5,.75,1,1.25,1.5):
  if all(empty(tuple(math.floor(a[k]+(b[k]-a[k])*i/l+o) for k,o in enumerate((dx,.2+lift,dz)))) for i in range(l+1) for dx,dz in ((0,0),(.14,.14),(-.14,-.14),(.14,-.14),(-.14,.14))):return True
 return False
def supported(a,b):
 if math.dist(a,b)<.1:return True
 n=math.ceil(math.dist(a,b)*3);prev=None
 for i in range(n+1):
  x,y,z=[s+(t-s)*i/n for s,t in zip(a,b)];surface=None
  for dy in range(4):
   px,py,pz=math.floor(x),math.floor(y+.25)-dy,math.floor(z);name=B((px,py,pz))
   if name in ('water','vine','ladder'):surface=py;break
   if not clear((px,py,pz)) and name not in ('?',):
    surface=py+(.5 if name.endswith('_slab') else 1);break
  if surface is None or surface-y>1.05 or surface-y< -2.1 or (prev!=None and abs(surface-prev)>1.01):return False
  prev=surface
 return True
def route(a,b,alt=False,biome=None):
 def ok(n):
  x,y,z=n['p']
  return biome!='FOREST' or -45<=x<=50 and 35<=y<=130 and 0<=z<=100
 ns={i:n for i,n in graph.items() if ok(n)}
 starts=sorted(ns,key=lambda i:math.dist(a,ns[i]['p']))[:6 if alt else 1]
 goals=sorted(ns,key=lambda i:math.dist(b,ns[i]['p']))[:6 if alt else 1]
 goals={j for j in goals if edge(tuple(ns[j]['p']),b) and (not alt or supported(b,tuple(ns[j]['p'])))}
 q=[(0,i) for i in starts if edge(a,tuple(ns[i]['p'])) and (not alt or supported(a,tuple(ns[i]['p'])))];heapq.heapify(q);seen=set()
 while q:
  v,i=heapq.heappop(q)
  if i in seen:continue
  seen.add(i)
  if i in goals:return True
  for j,c in ns[i]['n'].items():
   if j in ns and j not in seen and edge(tuple(ns[i]['p']),tuple(ns[j]['p'])):heapq.heappush(q,(v+c,j))
 return False
if __name__=='__main__':
 for b,v in routes.items():
  g=v['stops'][0];q=next((y for x,y in t['corrections'][b] if x==g),g);end=(q[0]+.5,q[1],q[2]+.5)
  for a in [(-50,69,22),(-48,68,19),(-50.5,66.5,14.5)]:print(b,a,route(a,end),route(a,end,True))
 assert all(route(a,tuple(q+.5 if k!=1 else q for k,q in enumerate(next((p for old,p in t['corrections'][b] if old==v['stops'][0]),v['stops'][0]))),True) for a in [(-50,69,22),(-48,68,19),(-50.5,66.5,14.5)] for b,v in routes.items())
 assert route((-18.5,65,34.5),(10.5,89,10.5),False,'FOREST')
 print('Forest 17 and all 12 gate approaches passed cached full-block clearance.')
