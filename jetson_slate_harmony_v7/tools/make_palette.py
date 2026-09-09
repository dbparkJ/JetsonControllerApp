"""Author-maintained perceptual palette. Not a scientific optimum or user-tested result."""
from pathlib import Path
import json,csv
from color_math import from_oklch,to_oklch,contrast
P=Path(__file__).resolve().parents[1]
def c(L,C,h=260):return from_oklch(L,C,h)
light={
'canvas':c(.971,.003),'surface':c(.991,.0015),'subtle':c(.947,.005),
'ink':c(.29,.014),'muted':c(.46,.018),'border':c(.867,.009),
'controlBorder':c(.57,.02),'focusRing':c(.515,.055),
'primary':c(.505,.055),'onPrimary':'#FFFFFF',
'accent':c(.899,.028),'onAccent':c(.39,.04),
'hero':c(.925,.016),'heroText':c(.29,.014),'heroMuted':c(.46,.018),
'success':c(.46,.018),'successBg':c(.947,.005),
'warning':'#7A5729','warningBg':'#F5EDDF',
'danger':'#9B4B46','dangerBg':'#F8ECEA',
'info':c(.505,.055),'infoBg':c(.939,.013),
'disabled':c(.92,.008),'onDisabled':c(.51,.011),'onDanger':'#FFFFFF',
'sectionBase':c(.991,.0015),'sectionSoft':c(.947,.005),'sectionRaised':c(.92,.008),
'sectionDanger':c(.947,.005),'sectionBorder':c(.867,.009),
'navSelected':c(.899,.028),'onNavSelected':c(.39,.04),
}
dark={
'canvas':c(.235,.008),'surface':c(.277,.01),'subtle':c(.32,.011),
'ink':c(.925,.005),'muted':c(.755,.012),'border':c(.41,.015),
'controlBorder':c(.64,.017),'focusRing':c(.78,.037),
'primary':c(.74,.035),'onPrimary':c(.25,.025),
'accent':c(.40,.032),'onAccent':c(.91,.012),
'hero':c(.35,.025),'heroText':c(.925,.005),'heroMuted':c(.755,.012),
'success':c(.79,.016),'successBg':c(.32,.011),
'warning':'#D6B88A','warningBg':'#3A3126',
'danger':'#E0AAA4','dangerBg':'#3C2C2D',
'info':c(.78,.037),'infoBg':c(.35,.025),
'disabled':c(.36,.013),'onDisabled':c(.733,.01),'onDanger':'#302322',
'sectionBase':c(.277,.01),'sectionSoft':c(.32,.011),'sectionRaised':c(.36,.013),
'sectionDanger':c(.32,.011),'sectionBorder':c(.41,.015),
'navSelected':c(.40,.032),'onNavSelected':c(.91,.012),
}
p=P/'design/tokens.json'; data=json.loads(p.read_text())
data.update(version='7.0',name='Slate Harmony',scope='Evidence-informed color harmony; same-hue surface hierarchy; no changes to device behavior.')
data['color']={'light':light,'dark':dark}
data['colorModel']={'authoringSpace':'OKLCH D65','neutralAndBrandHueDegrees':260,'productionSpace':'opaque sRGB hex','statusColors':'authored separate hues for warning and error; not large structural panels','evidenceStatus':'Derived design decisions, not a palette validated in the cited papers.'}
p.write_text(json.dumps(data,ensure_ascii=False,indent=2),encoding='utf-8')
rows=[]
for theme,ts in data['color'].items():
 for key,value in ts.items():
  L,C,h=to_oklch(value);rows.append(dict(theme=theme,role=key,srgb=value,oklch_L=round(L,6),oklch_C=round(C,6),oklch_h=round(h,3)))
with (P/'design/palette-oklch.csv').open('w',newline='',encoding='utf-8') as f:
 w=csv.DictWriter(f,fieldnames=rows[0]);w.writeheader();w.writerows(rows)
for theme,ts in data['color'].items():
 print(theme, {k:ts[k] for k in ['canvas','surface','sectionSoft','sectionRaised','hero','primary','ink','muted']})
 for fg,bg in [('primary','hero'),('primary','sectionRaised'),('muted','hero'),('onPrimary','primary'),('onAccent','accent'),('controlBorder','surface')]:
  print(fg,bg,round(contrast(ts[fg],ts[bg]),3))
