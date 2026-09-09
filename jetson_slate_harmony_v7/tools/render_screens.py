"""Render all states with one browser page; also export measured Figma scene data.
No real device, network connection or Figma API is contacted.
"""
import asyncio,json,os,shutil
from pathlib import Path
from playwright.async_api import async_playwright
P=Path(__file__).resolve().parents[1]
SNAPSHOT='''() => {function walk(e){const c=getComputedStyle(e),r=e.getBoundingClientRect();return {name:e.dataset.name,kind:e.dataset.kind,component:e.dataset.component,icon:e.dataset.icon,action:e.dataset.action,style:JSON.parse(e.dataset.designStyle||'{}'),text:e.dataset.kind==='text'?e.textContent:undefined,w:r.width,h:r.height,fontSize:parseFloat(c.fontSize),lineHeight:parseFloat(c.lineHeight),children:[...e.children].filter(n=>n.classList.contains('node')).map(walk)}}return {tree:walk(document.querySelector('#app>.node')),dialog:document.querySelector('.modal-overlay>.node')?walk(document.querySelector('.modal-overlay>.node')):null}}'''
async def main():
 spec=json.loads((P/'design/scene-spec.json').read_text(encoding='utf-8'));results=[];shots=[];errors=[]
 async with async_playwright() as a:
  options={'args':['--no-sandbox']};path=os.environ.get('BROWSER_EXECUTABLE') or shutil.which('chromium') or shutil.which('chromium-browser')
  if path: options['executable_path']=path
  browser=await a.chromium.launch(**options)
  page=await browser.new_page(viewport={'width':412,'height':892},device_scale_factor=2)
  page.on('pageerror',lambda e:errors.append(str(e)))
  await page.set_content((P/'prototype.html').read_text().replace('new URLSearchParams(location.search)',"new URLSearchParams('capture=1')"))
  await page.evaluate('document.fonts.ready')
  for s in spec['screens']:
   await page.evaluate("k=>{overrideTheme=null;overrideScale=null;render(k);}",s['key'])
   await page.evaluate('document.fonts.ready')
   await page.screenshot(path=str(P/'screens'/(s['key']+'.png')),animations='disabled')
   layout=await page.evaluate('window.exportLayout()')
   results.append({'screen':s['key'],'nodes':len(layout),'layout':layout})
   snap=await page.evaluate(SNAPSHOT)
   shots.append({**{k:s[k] for k in ['key','title','theme','scale']},**snap})
   print('Rendered',s['key'],flush=True)
  await browser.close()
 (P/'evidence/render-layouts.json').write_text(json.dumps({'errors':errors,'renders':results},ensure_ascii=False,indent=2))
 (P/'design/figma-import-data.json').write_text(json.dumps({'name':spec['name'],'themes':spec['themes'],'icons':spec['icons'],'screens':shots},ensure_ascii=False,indent=2))
 if errors:raise RuntimeError(errors)
 print('Rendered and exported measurements:',len(results))
if __name__=='__main__':asyncio.run(main())
