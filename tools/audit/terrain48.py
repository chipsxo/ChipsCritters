from pathlib import Path
import os,gzip,zlib,struct,io,functools,re,json,sys,math
profile=Path(os.environ['APPDATA'])/'ModrinthApp/profiles/SkyBlock Enhanced'
def tag(f,k):
 def num(fmt):return struct.unpack('>'+fmt,f.read(struct.calcsize('>'+fmt)))[0]
 if 1<=k<=6:return num('bhiqfd'[k-1])
 if k==7:return f.read(num('i'))
 if k==8:return f.read(num('H')).decode('utf8','replace')
 if k==9:
  t=num('B');return [tag(f,t) for _ in range(num('i'))]
 if k==10:
  d={}
  while (t:=num('B')):
   key=tag(f,8);d[key]=tag(f,t)
  return d
 if k in (11,12):
  n=num('i');t='i' if k==11 else 'q';return struct.unpack('>'+str(n)+t,f.read(n*struct.calcsize(t)))
@functools.lru_cache(None)
def chunk(folder,cx,cz):
 p=folder/f'r.{cx//32}.{cz//32}.mca'
 if not p.exists():return {}
 with p.open('rb') as f:
  f.seek(4*(cx%32+32*(cz%32)));off=int.from_bytes(f.read(3),'big')
  if not off:return {}
  f.seek(off*4096);n=int.from_bytes(f.read(4),'big');c=f.read(1);raw=f.read(n-1)
  if c!=b'\x02':return {}
  f=io.BytesIO(zlib.decompress(raw));f.read(1);tag(f,8);root=tag(f,10)
  return {s['Y']:s['block_states'] for s in root.get('sections',[]) if 'block_states' in s}
def block(folder,x,y,z):
 s=chunk(folder,x//16,z//16).get(y//16)
 if not s:return '?'
 pal=s['palette'];b=max(4,(len(pal)-1).bit_length());i=(y%16*16+z%16)*16+x%16
 idx=(s['data'][i//(64//b)]>>(b*(i%(64//b))))%2**b if len(pal)>1 else 0
 return pal[idx]['Name'].split(':')[-1]
if __name__=='__main__':
 for folder in (profile/'.bobby').glob('*/0/minecraft/overworld/*'):
  if folder.is_dir():
   col=[block(folder,-88,y,43) for y in (62,65,69)]
   if 'ladder' in col:print(folder,col,[block(folder,-94,y,37) for y in (75,77,79)])
 seen=set()
 for p in [profile/'logs/latest.log',*sorted((profile/'logs').glob('*.gz'),reverse=True)[:35]]:
  try:t=(gzip.open(p,'rt',encoding='utf8',errors='replace') if p.suffix=='.gz' else p.open(encoding='utf8',errors='replace')).read()
  except:continue
  for m in re.findall(r'CAPTURE! You caught [^\n]{1,130}',t):
   if any(n in m.lower() for n in ['doomspiral','gimmiegold']) and m not in seen: print(m);seen.add(m)
