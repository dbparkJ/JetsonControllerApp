#!/usr/bin/env python3
"""Install into a user-created Figma development-plugin folder, keeping its ID.
Usage: python install.py /path/to/your/generated/manifest.json
No Figma account access or network request is made.
"""
from pathlib import Path
import json,shutil,sys,datetime

def main():
 if len(sys.argv)!=2:raise SystemExit('Usage: python install.py PATH_TO_FIGMA_GENERATED_MANIFEST_JSON')
 target=Path(sys.argv[1]).expanduser().resolve();src=Path(__file__).resolve().parent
 if not target.is_file():raise SystemExit('Figma가 생성한 manifest.json의 실제 경로를 입력하세요.')
 previous=json.loads(target.read_text(encoding='utf-8-sig'))
 if not isinstance(previous.get('id'),str) or not previous['id'].strip():raise SystemExit('Figma에서 발급된 id가 필요합니다. 임의 ID를 만들지 않습니다.')
 payload=json.loads((src/'manifest.template.json').read_text(encoding='utf-8'));payload['id']=previous['id']
 stamp=datetime.datetime.now().strftime('%Y%m%d_%H%M%S');backup=target.parent/('backup_'+stamp);backup.mkdir(exist_ok=False)
 for name in ['manifest.json','code.js','ui.html']:
  f=target.parent/name
  if f.is_file():shutil.copy2(f,backup/name)
 for name in ['code.js','ui.html']:
  shutil.copy2(src/name,target.parent/name)
 target.write_text(json.dumps(payload,ensure_ascii=False,indent=2),encoding='utf-8')
 print('Installed:',target,'\nBackup:',backup,'\nFigma에서 이 개발 플러그인을 실행하세요.')
if __name__=='__main__':main()
