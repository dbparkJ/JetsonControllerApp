import asyncio,json,os,shutil
from pathlib import Path
from playwright.async_api import async_playwright
P=Path(__file__).resolve().parents[1]
HTML=(P/'prototype.html').read_text(encoding="utf-8");SPEC=json.loads((P/'design/scene-spec.json').read_text(encoding="utf-8"))
async def main():
 report={'scope':'local HTML prototype, NOT native Android or real Figma','runtimeErrors':[],'networkRequests':[],'layoutCases':[],'interactionTests':[]}
 async with async_playwright() as a:
  browser_path=os.environ.get('BROWSER_EXECUTABLE') or shutil.which('chromium') or shutil.which('chromium-browser')
  launch_options={'args':['--no-sandbox']}
  if browser_path:launch_options['executable_path']=browser_path
  browser=await a.chromium.launch(**launch_options)
  for width,scale in [(412,1),(360,1),(360,2)]:
   for screen in SPEC['screens']:
    p=await browser.new_page(viewport={'width':width,'height':892})
    p.on('pageerror',lambda e:report['runtimeErrors'].append(str(e)))
    p.on('request',lambda r:report['networkRequests'].append(r.url))
    await p.set_content(HTML.replace('new URLSearchParams(location.search)',f"new URLSearchParams('capture=1&screen={screen['key']}&scale={scale}')"))
    await p.evaluate('document.fonts.ready')
    issues=await p.evaluate('''() => [...document.querySelectorAll('#app .text')].filter(e=>{let r=e.getBoundingClientRect();return r.right>innerWidth+.5||r.left<-.5}).map(e=>({name:e.dataset.name,text:e.textContent,rect:{left:e.getBoundingClientRect().left,right:e.getBoundingClientRect().right}}))''')
    report['layoutCases'].append({'screen':screen['key'],'width':width,'linearFontScale':scale,'horizontalOverflow':issues})
    await p.close()
  p=await browser.new_page(viewport={'width':412,'height':892})
  await p.set_content(HTML.replace('new URLSearchParams(location.search)',"new URLSearchParams('capture=1')"))
  for action,expected in [('tasks','02-tasks'),('data','03-data'),('settings','04-settings'),('home','01-home-light')]:
   await p.locator(f'[data-name="Nav/{action}"]').click();result=await p.evaluate('window.currentScreen');report['interactionTests'].append({'test':'nav '+action,'passed':result==expected})
  await p.evaluate("act('data');act('clear-selection');act('transfer-confirm')")
  report['interactionTests'].append({'test':'empty selection blocks confirmation','passed':await p.locator('.modal-overlay').count()==0})
  await p.evaluate("act('select:1');act('transfer-confirm')")
  report['interactionTests'].append({'test':'one selected folder confirmation with correct bytes','passed':'11.6 MB' in await p.locator('.modal-overlay').inner_text()})
  await p.keyboard.press('Escape')
  report['interactionTests'].append({'test':'Escape cancels confirmation','passed':await p.locator('.modal-overlay').count()==0})
  await p.evaluate("act('home');act('stop-confirm')")
  report['interactionTests'].append({'test':'stop needs explicit confirmation','passed':await p.locator('.modal-overlay').count()==1})
  await p.evaluate("act('cancel')")
  report['interactionTests'].append({'test':'stop cancel returns to home','passed':await p.evaluate('window.currentScreen')=='01-home-light'})
  await p.evaluate("act('connection')")
  report['interactionTests'].append({'test':'connection recovery screen reachable','passed':await p.evaluate('window.currentScreen')=='11-connection'})
  await browser.close()
 def lum(h):
  vals=[int(h[i:i+2],16)/255 for i in (1,3,5)];vals=[v/12.92 if v<=.04045 else ((v+.055)/1.055)**2.4 for v in vals];return sum(x*y for x,y in zip(vals,[.2126,.7152,.0722]))
 pairs=[('ink','canvas'),('ink','surface'),('muted','canvas'),('muted','surface'),('onPrimary','primary'),('onAccent','accent'),('heroText','hero'),('heroMuted','hero'),('success','successBg'),('warning','warningBg'),('danger','dangerBg'),('info','infoBg')]
 report['contrast']=[]
 for theme,tokens in SPEC['themes'].items():
  for fg,bg in pairs:
   x,y=sorted([lum(tokens[fg]),lum(tokens[bg])]);ratio=(y+.05)/(x+.05);report['contrast'].append({'theme':theme,'pair':f'{fg}/{bg}','ratio':round(ratio,2),'passes4_5':ratio>=4.5})
 report['summary']={'cases':len(report['layoutCases']),'overflowCases':sum(bool(c['horizontalOverflow']) for c in report['layoutCases']),'interactionPasses':sum(c['passed'] for c in report['interactionTests']),'interactionTests':len(report['interactionTests']),'contrastPasses':sum(c['passes4_5'] for c in report['contrast']),'contrastPairs':len(report['contrast'])}
 (P/'evidence/prototype-validation.json').write_text(json.dumps(report,ensure_ascii=False,indent=2),encoding="utf-8");print(report['summary'])
 for c in report['layoutCases']:
  if c['horizontalOverflow']:print(c['screen'],c['width'],c['linearFontScale'],c['horizontalOverflow'][:2])
asyncio.run(main())
