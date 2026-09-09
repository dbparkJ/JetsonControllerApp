from pathlib import Path
import hashlib,json
P=Path(__file__).resolve().parents[1]
def main():
 entries=[]
 for f in sorted(P.rglob('*')):
  if not f.is_file() or f.name=='PACKAGE_MANIFEST.json' or '__pycache__' in f.parts or f.suffix=='.pyc':continue
  entries.append({'path':str(f.relative_to(P)),'bytes':f.stat().st_size,'sha256':hashlib.sha256(f.read_bytes()).hexdigest()})
 (P/'PACKAGE_MANIFEST.json').write_text(json.dumps({'name':'Slate Harmony V7','scope':'local handoff; not native or real Figma export','entries':entries},ensure_ascii=False,indent=2),encoding='utf-8')
 print('Manifest entries',len(entries))
if __name__=='__main__':main()
