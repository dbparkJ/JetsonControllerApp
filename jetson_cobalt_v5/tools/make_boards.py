from PIL import Image,ImageDraw,ImageFont
from pathlib import Path
import json,math,os
P=Path(__file__).resolve().parents[1]
reg=os.environ.get('CJK_REGULAR_FONT','/usr/share/fonts/opentype/noto/NotoSansCJK-Regular.ttc');bold=os.environ.get('CJK_BOLD_FONT','/usr/share/fonts/opentype/noto/NotoSansCJK-Bold.ttc')
def f(sz,b=False):return ImageFont.truetype(bold if b else reg,sz,index=1)
def put(im,draw,filename,x,y,w):
 a=Image.open(P/'screens'/filename).convert('RGB');h=round(a.height*w/a.width);a=a.resize((w,h),Image.Resampling.LANCZOS)
 mask=Image.new('L',(w,h),0);ImageDraw.Draw(mask).rounded_rectangle((0,0,w-1,h-1),radius=20,fill=255);draw.rounded_rectangle((x-1,y-1,x+w+1,y+h+1),radius=21,outline='#C3CCDB',width=2);im.paste(a,(x,y),mask);return h
im=Image.new('RGB',(1920,1280),'#E7E9EF');d=ImageDraw.Draw(im)
d.text((96,55),'JETSON CONTROLLER   /   PRODUCT DESIGN 2026',font=f(16,True),fill='#52617A');d.text((90,91),'Cobalt Field.',font=f(76,True),fill='#172B4D');d.text((98,202),'중요한 작업은 크게, 복잡한 관리는 뒤로.',font=f(27,True),fill='#233957')
for i,c in enumerate(['#172B4D','#2456D8','#DCE7FF','#F7F5F2']):d.rounded_rectangle((1592+i*55,96,1637+i*55,141),radius=12,fill=c)
d.text((1518,157),'LOCAL UI PROTOTYPE',font=f(15,True),fill='#52617A')
for i,(name,label,sub)in enumerate([('01-home-light.png','01  홈','현재 작업과 준비 상태'),('02-tasks.png','02  작업','명확한 상태와 다음 행동'),('03-data.png','03  데이터','선택 범위와 전송 목적지'),('04-settings.png','04  설정','일반 관리와 위험 동작 분리')]):
 x=96+i*444;h=put(im,d,name,x,282,380);d.text((x,282+h+20),label,font=f(22,True),fill='#172B4D');d.text((x,282+h+54),sub,font=f(15),fill='#52617A')
d.text((96,1232),'로컬 렌더링 시안 · 모든 수치와 연결 상태는 데모 · Figma 내보내기 또는 실제 앱 캡처가 아닙니다.',font=f(15),fill='#52617A')
im.save(P/'screens'/'00-overview.png')
spec=json.loads((P/'design/scene-spec.json').read_text(encoding="utf-8"));w=300;gap=46;left=50;top=180;cellh=round(892*w/412)+68;rows=math.ceil(len(spec['screens'])/4)
im=Image.new('RGB',(left*2+4*w+3*gap,top+rows*cellh+40),'#E7E9EF');d=ImageDraw.Draw(im);d.text((50,40),'Cobalt Field / 모든 화면과 상태',font=f(36,True),fill='#172B4D');d.text((50,105),'밝은 테마 · 어두운 테마 · 연결 끊김 · 큰 글꼴 · 실행/전송 확인 · 최초 등록',font=f(17),fill='#52617A')
for i,s in enumerate(spec['screens']):
 x=left+(i%4)*(w+gap);y=top+(i//4)*cellh;h=put(im,d,s['key']+'.png',x,y,w);d.text((x,y+h+12),s['key'],font=f(13,True),fill='#172B4D')
im.save(P/'screens'/'00-all-states.png')
print('Boards saved')
