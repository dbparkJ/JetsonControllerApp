"""Consistency checks for this local handoff set; no remote service requests."""
from pathlib import Path
import json,re,hashlib,colorsys
from PIL import Image,ImageChops,ImageStat
P=Path(__file__).resolve().parents[1]
def read_json(rel):return json.loads((P/rel).read_text(encoding='utf-8'))
def main():
 tokens=read_json('design/tokens.json');spec=read_json('design/scene-spec.json');imp=read_json('design/figma-import-data.json')
 proto=(P/'prototype.html').read_text(encoding='utf-8');plugin=(P/'figma-plugin/code.js').read_text(encoding='utf-8')
 embedded_spec=json.loads(proto.split('const SPEC=',1)[1].split(';\nconst params=',1)[0])
 embedded_plugin=json.loads(plugin.split('const DATA = ',1)[1].split(';\nconst VERSION',1)[0])
 assert tokens['color']==spec['themes']==imp['themes']==embedded_spec['themes']==embedded_plugin['themes']
 assert spec['version']=='5.0' and len(spec['screens'])==17
 forbidden=[]
 for mode,roles in tokens['color'].items():
  for role,h in roles.items():
   hue,sat,value=colorsys.rgb_to_hsv(*(int(h[i:i+2],16)/255 for i in (1,3,5)))
   if 65<=hue*360<=175 and sat>=.15:forbidden.append((mode,role,h))
 assert not forbidden,forbidden
 docs=['JETSONCONTROLLER_COBALT_FIELD_V5.md','COBALT_FIELD_STYLE_GUIDE.md']
 hexes={h for mode in tokens['color'].values() for h in mode.values()}
 for doc in docs:
  content=(P/doc).read_text(encoding='utf-8')
  assert hexes.issubset(set(re.findall(r'#[0-9A-Fa-f]{6}',content))),doc
 prompt=(P/'START_PROMPT.txt').read_text(encoding='utf-8').strip()
 main_doc=(P/docs[0]).read_text(encoding='utf-8')
 assert prompt in main_doc
 assert '## 13. 마지막 정리' in main_doc
 assert '정확한 경로 한 개만 삭제' in main_doc
 assert not list(P.glob('*FIELD_SIGNAL_V4*.md'))
 rows=[]
 for scr in spec['screens']:
  f=P/'screens'/(scr['key']+'.png')
  with Image.open(f) as im:
   im.verify()
  with Image.open(f) as im:assert im.size==(824,1784),(f,im.size)
  rows.append({'file':str(f.relative_to(P)),'size':[824,1784],'sha256':hashlib.sha256(f.read_bytes()).hexdigest()})
 assert len([f for f in (P/'screens').glob('*.png')])==19
 for n in ['01-home-light.png','00-overview.png']:
  with Image.open(P/'reference/last_shown_cobalt'/n) as a,Image.open(P/'screens'/n) as b:
   assert a.size==b.size
   diff=ImageChops.difference(a.convert('RGB'),b.convert('RGB'))
   identical=diff.getbbox() is None
   rows.append({'reference_comparison':n,'identical_pixels':identical,'mean_absolute_channel_difference':sum(ImageStat.Stat(diff).mean)/3})
 for r in read_json('reference/manifest.json'):
  assert hashlib.sha256((P/r['file']).read_bytes()).hexdigest()==r['sha256']
 errors=[]
 for f in P.rglob('*'):
  if not f.is_file() or 'reference' in f.relative_to(P).parts:continue
  if f.suffix.lower() in ['.ttf','.otf','.woff','.woff2','.ttc']:errors.append('font binary: '+str(f))
  if f.suffix.lower() in ['.html','.json','.js','.py'] and f.name!='audit_set.py':
   s=f.read_text(encoding='utf-8')
   for h in ['#193E32','#205A48','#D6EC9C','#B6D98A']:
    if h in s:errors.append('legacy color '+h+' in '+str(f.relative_to(P)))
 assert not errors,errors
 result={'scope':'LOCAL_PACKAGE_ONLY','status':'PASS','screen_count':17,'overview_boards':2,'color_roles_per_mode':{k:len(v) for k,v in tokens['color'].items()},'palette_sync':['tokens','scene','figma-import-data','embedded HTML','embedded plugin DATA','both MD tables'],'green_palette_roles':forbidden,'prompt_embedded_identical':True,'original_8_screenshot_hashes':'PASS','no_font_binaries':True,'artifacts':rows,'real_figma_export':'NOT_RUN','native_app_or_real_device_test':'NOT_RUN'}
 (P/'evidence/set-consistency.json').write_text(json.dumps(result,ensure_ascii=False,indent=2),encoding='utf-8')
 print('Set consistency PASS:',len(rows)-2,'screens,',rows[-2:])
if __name__=='__main__':main()
