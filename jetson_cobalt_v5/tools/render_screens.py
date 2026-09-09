import asyncio,json,os,shutil
from pathlib import Path
from playwright.async_api import async_playwright
P=Path(__file__).resolve().parents[1]
async def main():
 spec=json.loads((P/'design/scene-spec.json').read_text(encoding="utf-8"));results=[]
 async with async_playwright() as a:
  browser_path=os.environ.get('BROWSER_EXECUTABLE') or shutil.which('chromium') or shutil.which('chromium-browser')
  launch_options={'args':['--no-sandbox']}
  if browser_path:launch_options['executable_path']=browser_path
  browser=await a.chromium.launch(**launch_options)
  errors=[]
  for s in spec['screens']:
   page=await browser.new_page(viewport={'width':412,'height':892},device_scale_factor=2)
   page.on('pageerror',lambda e: errors.append(str(e)))
   await page.set_content((P/'prototype.html').read_text(encoding="utf-8").replace('new URLSearchParams(location.search)',f"new URLSearchParams('capture=1&screen={s['key']}')"))
   await page.evaluate('document.fonts.ready');await page.wait_for_timeout(70)
   await page.screenshot(path=str(P/'screens'/(s['key']+'.png')))
   layout=await page.evaluate('window.exportLayout()')
   results.append({'screen':s['key'],'nodes':len(layout),'layout':layout})
   await page.close()
  await browser.close()
 (P/'evidence/render-layouts.json').write_text(json.dumps({'errors':errors,'renders':results},ensure_ascii=False,indent=2),encoding="utf-8")
 print('Rendered',len(results),'screens; errors:',errors)
asyncio.run(main())
