"""Check V7 package consistency and integrity. Not a native app or Figma test."""
from pathlib import Path
import hashlib,json,re,colorsys
from PIL import Image
P=Path(__file__).resolve().parents[1]
def main():
 tokens=json.loads((P/'design/tokens.json').read_text(encoding='utf-8'))
 spec=json.loads((P/'design/scene-spec.json').read_text(encoding='utf-8'))
 html=(P/'prototype.html').read_text(encoding='utf-8')
 embedded=json.loads(html.split('const SPEC=',1)[1].split(';\nconst params=',1)[0])
 data=json.loads((P/'design/figma-import-data.json').read_text(encoding='utf-8'))
 plugin=(P/'figma-plugin/code.js').read_text(encoding='utf-8')
 pdata=json.loads(plugin.split('const DATA = ',1)[1].split(';\nconst VERSION',1)[0])
 assert tokens['color']==spec['themes']==embedded['themes']==data['themes']==pdata['themes']
 assert len(spec['screens'])==32
 assert '__DATA__' not in plugin and '__SPEC__' not in html
 assert len({s['key'] for s in spec['screens']})==32
 for old in ['sectionCool','sectionWarm','sectionNeutral']:
  assert old not in json.dumps(spec) and old not in plugin
 k=(P/'compose-reference/SlateHarmonyColors.kt').read_text(encoding='utf-8')
 for mode,name in [('light','LightHarmonyTokens'),('dark','DarkHarmonyTokens')]:
  section=k.split('private val '+name+' = SlateHarmonyTokens(',1)[1].split('\n)',1)[0]
  actual=dict(re.findall(r'(\w+) = Color\(0xFF([A-F0-9]{6})\)',section))
  assert actual=={r:v[1:] for r,v in tokens['color'][mode].items()}
 guide=(P/'SLATE_HARMONY_STYLE_GUIDE.md').read_text(encoding='utf-8')
 for role,v in tokens['color']['light'].items():assert f'| `{role}` | `{v}` | `{tokens["color"]["dark"][role]}` |' in guide
 prompt=(P/'START_PROMPT.txt').read_text(encoding='utf-8').strip()
 assert prompt in (P/'JETSONCONTROLLER_SLATE_HARMONY_V7.md').read_text(encoding='utf-8')
 assert (P/'research/COLOR_RESEARCH.md').read_bytes()==(P/'COLOR_RESEARCH.md').read_bytes()
 for item in json.loads((P/'reference/manifest.json').read_text()):
  assert hashlib.sha256((P/item['file']).read_bytes()).hexdigest()==item['sha256']
 assets=[]
 for s in spec['screens']:
  path=P/'screens'/(s['key']+'.png')
  with Image.open(path) as im:assert im.size==(824,1784);im.verify()
  assets.append(str(path.relative_to(P)))
 boards=list((P/'screens').glob('00-*.png'));assert len(boards)==6
 for b in boards:
  with Image.open(b) as im:im.verify()
 binaries=[p.name for p in P.rglob('*') if p.suffix.lower() in ['.ttf','.ttc','.otf','.woff','.woff2']];assert not binaries
 # Filter only meaningfully chromatic green-family roles; near-neutral hue is unstable.
 green=[]
 for mode,ts in tokens['color'].items():
  for role,h in ts.items():
   rgb=[int(h[i:i+2],16)/255 for i in (1,3,5)];hu,s,v=colorsys.rgb_to_hsv(*rgb)
   if 70<hu*360<185 and s>.15:green.append([mode,role,h])
 assert not green
 val=json.loads((P/'evidence/prototype-validation.json').read_text());st=val['summary']
 assert st['horizontalOverflowCases']==0 and st['runtimeErrors']==0
 assert st['interactionPasses']==st['interactionTests']
 assert st['renderedTextPairPasses']==st['uniqueRenderedTextPairs']
 assert st['renderedIconPairPasses']==st['uniqueRenderedIconPairs']
 assert st['tokenPairPasses']==st['tokenPairs']
 result={'scope':'LOCAL_V7_SET_ONLY','status':'PASS','screens':32,'boards':len(boards),'paletteSynchronization':['tokens','scene','HTML','Figma import input','Figma plugin embedded input','style guide table','Kotlin reference'],'sourceReferenceHashes':'PASS','embeddedPrompt':'PASS','fontBinariesIncluded':False,'greenPaletteRoles':green,'remoteRepositoryChanged':False,'nativeCompilation':'NOT_RUN','nativeDeviceTest':'NOT_RUN','realFigmaExecution':'NOT_RUN','userStudy':'NOT_RUN','screenFiles':assets}
 (P/'evidence/set-consistency.json').write_text(json.dumps(result,ensure_ascii=False,indent=2),encoding='utf-8')
 print('V7 set consistency PASS')
if __name__=='__main__':main()
