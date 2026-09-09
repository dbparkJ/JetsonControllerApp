"""Local browser checks only; never a native accessibility or comfort certification."""
import asyncio,csv,json,os,shutil
from pathlib import Path
from playwright.async_api import async_playwright
from color_math import contrast,to_oklch
P=Path(__file__).resolve().parents[1]
HTML=(P/'prototype.html').read_text(encoding='utf-8');SPEC=json.loads((P/'design/scene-spec.json').read_text(encoding='utf-8'))
JS=r'''() => {
 function rgba(s){const m=s.match(/[\d.]+/g);return m?m.map(Number):[0,0,0,0]}
 function hex(a){return '#'+a.slice(0,3).map(v=>Math.round(v).toString(16).padStart(2,'0')).join('').toUpperCase()}
 function bg(e){for(let x=e;x;x=x.parentElement){const c=rgba(getComputedStyle(x).backgroundColor);if(c.length===3||c[3]===1)return hex(c);if(c[3]>0)return null;}return null;}
 const scope=document.querySelector('.modal-overlay')||document.querySelector('#app');
 const texts=[...scope.querySelectorAll('.text')].map(e=>{const c=rgba(getComputedStyle(e).color);return {node:e.dataset.name,text:e.textContent,fg:hex(c),bg:bg(e),opacity:getComputedStyle(e).opacity}});
 const icons=[...scope.querySelectorAll('.icon')].map(e=>{const s=e.querySelector('svg');return {node:e.dataset.name,fg:s?s.getAttribute('stroke'):null,bg:bg(e)}});
 const issues=[...scope.querySelectorAll('.text')].filter(e=>{const r=e.getBoundingClientRect();return r.right>innerWidth+.5||r.left<-.5}).map(e=>({node:e.dataset.name,text:e.textContent,left:e.getBoundingClientRect().left,right:e.getBoundingClientRect().right}));
 return {texts,icons,issues};
}'''
async def main():
 report={'scope':'LOCAL_HTML_ONLY; not Figma, Android, user comfort, or full WCAG conformance','runtimeErrors':[],'networkRequests':[],'layoutCases':[],'interactionTests':[],'textPairs':[],'iconPairs':[],'tokenPairs':[]}
 textPairs={};iconPairs={}
 async with async_playwright() as a:
  path=os.environ.get('BROWSER_EXECUTABLE') or shutil.which('chromium') or shutil.which('chromium-browser')
  options={'args':['--no-sandbox']}
  if path:options['executable_path']=path
  browser=await a.chromium.launch(**options)
  page=await browser.new_page(viewport={'width':412,'height':892})
  page.on('pageerror',lambda e:report['runtimeErrors'].append(str(e)))
  page.on('request',lambda r:report['networkRequests'].append(r.url))
  await page.set_content(HTML.replace('new URLSearchParams(location.search)',"new URLSearchParams('capture=1')"))
  await page.evaluate('document.fonts.ready')
  textCount=0;iconCount=0
  for width,scale in [(412,1.0),(360,1.0),(360,1.3),(360,2.0)]:
   await page.set_viewport_size({'width':width,'height':892})
   for screen in SPEC['screens']:
    await page.evaluate('([k,s])=>{overrideTheme=null;overrideScale=s;render(k)}',[screen['key'],scale])
    await page.evaluate('document.fonts.ready')
    data=await page.evaluate(JS)
    report['layoutCases'].append({'screen':screen['key'],'width':width,'linearFontScale':scale,'horizontalOverflow':data['issues']})
    if width==412:
     for v in data['texts']:
      if not v['text'].strip():continue
      textCount+=1
      key=(screen['theme'],v['fg'],v['bg'])
      if key not in textPairs:textPairs[key]={'theme':key[0],'fg':key[1],'bg':key[2],'examples':[],'count':0}
      pair=textPairs[key];pair['count']+=1
      if len(pair['examples'])<3:pair['examples'].append(screen['key']+' / '+v['node']+' / '+v['text'][:70])
     for v in data['icons']:
      if not v['fg'] or not v['fg'].startswith('#') or not v['bg']:continue
      iconCount+=1;key=(screen['theme'],v['fg'].upper(),v['bg'])
      if key not in iconPairs:iconPairs[key]={'theme':key[0],'fg':key[1],'bg':key[2],'examples':[],'count':0}
      pair=iconPairs[key];pair['count']+=1
      if len(pair['examples'])<3:pair['examples'].append(screen['key']+' / '+v['node'])
  await page.set_viewport_size({'width':412,'height':892})
  await page.evaluate("overrideTheme=null;overrideScale=null;render('01-home-light')")
  def record(name,passed):report['interactionTests'].append({'test':name,'passed':bool(passed)})
  for action,expected in [('tasks','02-tasks'),('data','03-data'),('settings','04-settings'),('home','01-home-light')]:
   await page.locator(f'[data-name="Nav/{action}"]').click();record('nav '+action,await page.evaluate('window.currentScreen')==expected)
  await page.evaluate("act('data');act('clear-selection');act('transfer-confirm')")
  record('empty selection blocks confirmation',await page.locator('.modal-overlay').count()==0)
  await page.evaluate("act('select:1');act('transfer-confirm')")
  record('one selected folder uses correct bytes','11.6 MB' in await page.locator('.modal-overlay').inner_text())
  await page.keyboard.press('Escape');record('Escape cancels confirmation',await page.locator('.modal-overlay').count()==0)
  await page.evaluate("act('home');act('stop-confirm')");record('stop needs confirmation',await page.locator('.modal-overlay').count()==1)
  await page.evaluate("act('cancel')");record('stop cancel returns home',await page.evaluate('window.currentScreen')=='01-home-light')
  await page.evaluate("act('connection')");record('recovery reachable',await page.evaluate('window.currentScreen')=='11-connection')
  await page.evaluate("overrideTheme=null;render('05-home-dark');act('tasks')")
  record('dark preserved across root navigation',await page.evaluate("current.theme==='dark' && window.currentScreen==='02-tasks'"))
  await page.evaluate("overrideTheme=null;overrideScale=null;render('30-transfer-confirm-dark')")
  record('dark transfer modal has correct theme',await page.evaluate("current.theme==='dark' && !!document.querySelector('.modal-overlay')"))
  await browser.close()
 for group,threshold in [(textPairs,4.5),(iconPairs,3.0)]:
  for pair in group.values():
   pair['ratio']=contrast(pair['fg'],pair['bg']) if pair['bg'] else None
   pair['threshold']=threshold;pair['passes']=pair['ratio'] is not None and pair['ratio']>=threshold
 report['textPairs']=list(textPairs.values());report['iconPairs']=list(iconPairs.values())
 # These are required role combinations, not all possible arbitrary color combinations.
 pairs=[(fg,bg,4.5) for fg in ['ink','muted'] for bg in ['canvas','surface','sectionSoft','sectionRaised','hero']]
 pairs += [(fg,bg,4.5) for fg,bg in [('onPrimary','primary'),('onAccent','accent'),('success','successBg'),('warning','warningBg'),('danger','dangerBg'),('info','infoBg'),('onDanger','danger'),('onNavSelected','navSelected'),('primary','surface'),('primary','hero'),('primary','sectionRaised')]]
 pairs += [(fg,bg,3) for fg in ['controlBorder','focusRing'] for bg in ['canvas','surface','sectionSoft','sectionRaised','hero']]
 for theme,tokens in SPEC['themes'].items():
  for fg,bg,threshold in pairs:
   r=contrast(tokens[fg],tokens[bg]);report['tokenPairs'].append({'theme':theme,'pair':fg+'/'+bg,'fg':tokens[fg],'bg':tokens[bg],'ratio':r,'threshold':threshold,'passes':r>=threshold})
 report['summary']={'layoutCases':len(report['layoutCases']),'horizontalOverflowCases':sum(bool(v['horizontalOverflow']) for v in report['layoutCases']),'interactionTests':len(report['interactionTests']),'interactionPasses':sum(v['passed'] for v in report['interactionTests']),'textNodesInspectedAtDefaultWidth':textCount,'uniqueRenderedTextPairs':len(textPairs),'renderedTextPairPasses':sum(v['passes'] for v in textPairs.values()),'iconNodesInspectedAtDefaultWidth':iconCount,'uniqueRenderedIconPairs':len(iconPairs),'renderedIconPairPasses':sum(v['passes'] for v in iconPairs.values()),'tokenPairs':len(report['tokenPairs']),'tokenPairPasses':sum(v['passes'] for v in report['tokenPairs']),'runtimeErrors':len(report['runtimeErrors']),'externalRequests':len(report['networkRequests'])}
 report['limitations']=['Checks local CSS/DOM, not Android font scaling or physical luminance.','Horizontal bounds check does not prove vertical content visibility or all interactions.','Normal-sized text uses the 4.5 target even where a larger-text exception exists.','Icon contrast inspection includes decorative icons; decorative section borders intentionally excluded from the 3:1 requirement.','No user study, eye tracking, medical benefit test, actual device/API call, real Figma render, or native build performed.','Hidden behind a modal content is excluded from text color checks.','No color-vision deficiency simulation or complete TalkBack audit performed.']
 (P/'evidence/prototype-validation.json').write_text(json.dumps(report,ensure_ascii=False,indent=2),encoding='utf-8')
 with (P/'evidence/contrast-pairs.csv').open('w',encoding='utf-8-sig',newline='') as f:
  w=csv.writer(f);w.writerow(['kind','theme','foreground','background','ratio_unrounded','threshold','passed'])
  for kind,items in [('rendered-text',report['textPairs']),('rendered-icon',report['iconPairs']),('token-contract',report['tokenPairs'])]:
   for p in items:w.writerow([kind,p['theme'],p['fg'],p['bg'],p['ratio'],p['threshold'],p['passes']])
 print(json.dumps(report['summary'],indent=2))
 failures=[p for group in ['textPairs','iconPairs','tokenPairs'] for p in report[group] if not p['passes']]
 if failures:print('CONTRAST FAILURES',json.dumps(failures,ensure_ascii=False,indent=2))
 if failures or report['summary']['horizontalOverflowCases'] or report['summary']['runtimeErrors'] or report['summary']['interactionPasses']!=report['summary']['interactionTests']:raise SystemExit(1)
if __name__=='__main__':asyncio.run(main())
