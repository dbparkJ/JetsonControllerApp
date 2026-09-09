#!/usr/bin/env python3
"""Rebuild local artifacts only. No repository, Figma or device connection."""
from pathlib import Path
import subprocess,sys,json,shutil
P=Path(__file__).resolve().parents[1]
def run(script):subprocess.run([sys.executable,str(P/'tools'/script)],check=True)
def main():
 for name in ['design','screens','evidence']:(P/name).mkdir(exist_ok=True)
 for script in ['make_palette.py','build_design.py']:run(script)
 template=(P/'tools/prototype_template.html').read_text(encoding='utf-8')
 (P/'prototype.html').write_text(template.replace('__SPEC__',(P/'design/scene-spec.json').read_text(encoding='utf-8')),encoding='utf-8')
 run('render_screens.py')
 data=json.loads((P/'design/figma-import-data.json').read_text(encoding='utf-8'))
 template=(P/'figma-plugin/code.template.js').read_text(encoding='utf-8')
 (P/'figma-plugin/code.js').write_text(template.replace('__DATA__',json.dumps(data,ensure_ascii=False,separators=(',',':'))),encoding='utf-8')
 for script in ['write_handoff.py','validate_prototype.py','make_boards.py']:run(script)
 node=shutil.which('node')
 if not node:raise SystemExit('Node.js unavailable. Figma plugin checks NOT RUN; handoff not validated.')
 subprocess.run([node,'--check',str(P/'figma-plugin/code.js')],check=True)
 subprocess.run([node,str(P/'tools/test_plugin_mock.js')],check=True)
 for script in ['audit_set.py','write_validation_report.py','make_manifest.py']:run(script)
 print('Local set rebuilt. Android/Figma/Jetson/user validation NOT RUN.')
if __name__=='__main__':main()
