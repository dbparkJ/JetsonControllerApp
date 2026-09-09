import asyncio,json,os,shutil
from pathlib import Path
from playwright.async_api import async_playwright
P=Path(__file__).resolve().parents[1]
JS='''() => {function walk(e){const c=getComputedStyle(e),r=e.getBoundingClientRect();return {name:e.dataset.name,kind:e.dataset.kind,component:e.dataset.component,icon:e.dataset.icon,action:e.dataset.action,style:JSON.parse(e.dataset.designStyle||'{}'),text:e.dataset.kind==='text'?e.textContent:undefined,w:r.width,h:r.height,fontSize:parseFloat(c.fontSize),lineHeight:parseFloat(c.lineHeight),children:[...e.children].filter(n=>n.classList.contains('node')).map(walk)}}return {tree:walk(document.querySelector('#app>.node')),dialog:document.querySelector('.modal-overlay>.node')?walk(document.querySelector('.modal-overlay>.node')):null}}'''
async def main():
 spec=json.loads((P/'design/scene-spec.json').read_text(encoding="utf-8"));shots=[]
 async with async_playwright() as a:
  browser_path=os.environ.get('BROWSER_EXECUTABLE') or shutil.which('chromium') or shutil.which('chromium-browser')
  launch_options={'args':['--no-sandbox']}
  if browser_path:launch_options['executable_path']=browser_path
  browser=await a.chromium.launch(**launch_options)
  for s in spec['screens']:
   page=await browser.new_page(viewport={'width':412,'height':892},device_scale_factor=1)
   await page.set_content((P/'prototype.html').read_text(encoding="utf-8").replace('new URLSearchParams(location.search)',f"new URLSearchParams('capture=1&screen={s['key']}')"))
   await page.evaluate('document.fonts.ready');result=await page.evaluate(JS)
   shots.append({**{k:s[k] for k in ['key','title','theme','scale']},**result});await page.close()
  await browser.close()
 (P/'design'/'figma-import-data.json').write_text(json.dumps({'name':spec['name'],'themes':spec['themes'],'icons':spec['icons'],'screens':shots},ensure_ascii=False,indent=2),encoding="utf-8")
 print('Snapshot export',len(shots))
asyncio.run(main())
