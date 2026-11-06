from pathlib import Path
import json,collections
items=json.loads(Path('work/audit56-terrain.json').read_text());alt=json.loads(Path('tools/audit/alternates56.json').read_text())
for o in items:
 if o['reason']:
  a=next((a for a in alt if a['biome']==o['biome'] and a['from']==o['from'] and a['to']==o['to'] and a['path']),None)
  if a:o['cachedResult']='cached alternate available';o['alternate']=a['path']
summary=collections.Counter(o['cachedResult'] for o in items);print(summary)
Path('../ChipsCritters-0.1.56-leg-audit.json').write_text(json.dumps({'modVersion':'0.1.56','method':'Static plans and fixed connectors, bundled graph, cached full-block terrain; not a live rendered-line test. Partial block shapes, live chunk loading, dynamic skips and shader output require in-game checks.','summary':dict(summary),'legs':items},indent=1))
text=['# ChipsCritters 0.1.56 route leg audit','',f'{len(items)} biome/mode/connector cases: '+', '.join(f'{v} {k}' for k,v in summary.items())+'.','', 'These are cached geometry results, not a claim of zero live gaps. Cached-clear tests exclude full-block collisions at 0.25-block samples and <=1.5-block ribbon lift. Partial collision shapes and live client rendering remain unverified. Alternate paths are candidates for the runtime graph search, not new route stops.','', 'All authored route order and waypoint coordinates remain byte-for-byte unchanged from 0.1.55. Connector navigation anchors were corrected to their cached open cells.','', '## Cases still needing a walked recording or live confirmation','', '| Biome / mode | Stop / kind | From | To | Reason |','|---|---|---|---|---|']
for o in items:
 if o['cachedResult']=='unverified':text.append(f"| {o['biome']} / {o['mode']} | {o['stop']} / {o['kind']} | {o['from']} | {o['to']} | {o['reason']} |")
text+=['','The full JSON includes every passing, alternate and unresolved case with exact mapped edge and obstruction. A failed offline path is not necessarily a live failure: the runtime includes painting approaches, direct walkability checks and exact Minecraft collision shapes.','', 'Record the three reported Cavern legs first using `/sr record next`, rather than stop numbers. If a line still breaks at the vines, start recording before the climb; if it breaks at the next floor drop, record that leg from the plateau.','']
Path('../ChipsCritters-0.1.56-leg-audit.md').write_text('\n'.join(text),encoding='utf8')
