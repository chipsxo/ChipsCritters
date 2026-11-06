from terrain48 import *
import heapq
from collections import deque,Counter
base=profile/'.bobby/mc.hypixel.net/0/minecraft/overworld'
folders=[base/i for i in ('2032','2036','1638','1447','1256','491')]
@functools.lru_cache(None)
def B(p):
 for f in folders:
  v=block(f,*p)
  if v!='?':return v
 return '?'
def elev(p,n):return p[0],p[1]+n,p[2]
def clear(p):return B(p) in ('air','cave_air','vine','ladder','short_grass','tall_grass','light','wall_torch','torch','glow_lichen','tripwire','water') or B(p).endswith('_wall_sign')
def solid(p):
 n=B(p)
 if n=='snow_block' or n.endswith('_carpet'):return True
 return n not in ('?','void_air','lava') and not clear(p) and not any(t in n for t in ['fence','wall','pane','chain','lantern','button','door','skull','head','carpet','slab','stairs','flower','rail','candle','sign','roots','mushroom','pressure_plate','banner','snow','pot','dripstone','lever','lichen','azalea','tulip','bush','coral'])
@functools.lru_cache(None)
def stand(p):return clear(p) and clear(elev(p,1)) and (solid(elev(p,-1)) or B(p) in ('ladder','vine','water'))
def correction(p):
 if B(p)=='?' or clear(p):return p
 for dy in range(1,4):
  q=elev(p,dy)
  if stand(q):return q
  if clear(q):break
 return p
routes=json.loads(Path('src/main/resources/data/chipscritters/routes.json').read_text())
graph=json.loads(Path('src/main/resources/data/chipscritters/safari_graph.json').read_text())['nodes']
def neighbors(p):
 x,y,z=p
 for dx,dz in [(1,0),(-1,0),(0,1),(0,-1)]:
  for dy in [0,1,-1,-2,-3]:
   q=(x+dx,y+dy,z+dz)
   if stand(q) and all(clear((q[0],h,q[2])) for h in range(q[1],y+2)) and (dy!=1 or clear(elev(p,2))):
    yield q;break
 for dy in [-1,1]:
  q=elev(p,dy)
  if (B(p) in ('ladder','vine','water') or B(q) in ('ladder','vine','water')) and stand(q):yield q
def walk(start,goal,limit=16000):
 q=[(0,0,start)];prev={};d={start:0}
 while q and len(d)<limit:
  _,v,p=heapq.heappop(q)
  if p==goal:
   path=[p]
   while p in prev:p=prev[p];path.append(p)
   return path[::-1]
  if v!=d[p]:continue
  for n in neighbors(p):
   alt=v+math.dist(n,p)
   if alt<d.get(n,1e9):
    d[n]=alt;prev[n]=p;heapq.heappush(q,(alt+math.dist(n,goal),alt,n))
 return []
def near(p,r=3):
 return sorted([(x,y,z) for x in range(p[0]-r,p[0]+r+1) for z in range(p[2]-r,p[2]+r+1) for y in range(p[1]-2,p[1]+3) if stand((x,y,z))],key=lambda a:math.dist(p,a))
if __name__=='__main__':
 for k,v in routes.items():
  print(k,[(p,B(tuple(p)),correction(tuple(p))) for p in v['stops'] if not clear(tuple(p))][:12])
 for p in routes['HAUNTED']['hideonwallLocations']+[[10,90,10],[-19,66,34],[25,56,43],[25,57,45]]:
  p=tuple(p);print(p,[B(elev(p,n)) for n in range(-2,4)],near(p)[:4])
