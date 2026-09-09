"""Compose actual local renders into presentation boards; no generative UI assets."""
from PIL import Image,ImageDraw,ImageFont
from pathlib import Path
import json,math,os
P=Path(__file__).resolve().parents[1]
reg=os.environ.get('CJK_REGULAR_FONT','/usr/share/fonts/opentype/noto/NotoSansCJK-Regular.ttc')
bold=os.environ.get('CJK_BOLD_FONT','/usr/share/fonts/opentype/noto/NotoSansCJK-Bold.ttc')
def f(size,b=False):return ImageFont.truetype(bold if b else reg,size,index=1)
t=json.loads((P/'design/tokens.json').read_text(encoding='utf-8'))['color']
ink=t['light']['ink'];muted=t['light']['muted'];base='#EBEEF1'
def text(d,xy,s,size=22,b=False,fill=None):d.text(xy,s,font=f(size,b),fill=fill or ink)
def place(im,d,path,x,y,w):
 a=Image.open(path).convert('RGB');h=round(a.height*w/a.width);a=a.resize((w,h),Image.Resampling.LANCZOS)
 mask=Image.new('L',(w,h),0);ImageDraw.Draw(mask).rounded_rectangle((0,0,w-1,h-1),radius=18,fill=255)
 d.rounded_rectangle((x-1,y-1,x+w,y+h),radius=19,outline='#BCC2CB',width=2);im.paste(a,(x,y),mask);return h
def board(name,title,sub,items):
 im=Image.new('RGB',(1920,1300),base);d=ImageDraw.Draw(im)
 text(d,(86,38),'JETSON CONTROLLER  /  COLOR STUDY 2026',17,True,muted)
 text(d,(82,75),title,67,True);text(d,(90,176),sub,25,False,muted)
 for i,(path,label,desc) in enumerate(items):
  x=90+445*i;text(d,(x,233),label,23,True);h=place(im,d,path,x,282,378)
  text(d,(x,282+h+16),desc,17,False,muted)
 text(d,(90,1238),'로컬 UI 렌더링 · 수치/상태는 데모 · 실제 앱 캡처나 Figma export가 아닙니다.',17,False,muted)
 im.save(P/'screens'/name)
board('00-light-dark.png','Slate Harmony.','같은 색 계열, 분명한 명도 위계. 라이트와 다크를 함께 재설계했습니다.',[
 (P/'screens/01-home-light.png','LIGHT  /  홈','오프화이트 · 슬레이트 블루'),
 (P/'screens/03-data.png','LIGHT  /  데이터','목적지와 선택 범위를 표면 단계로'),
 (P/'screens/05-home-dark.png','DARK  /  홈','중립 차콜 · 절제된 주요 행동'),
 (P/'screens/20-settings-dark.png','DARK  /  설정','같은 계열로 장비 / 앱 설정 구분')])
board('00-overview-light.png','Light / Calm hierarchy.','큰 표면은 같은 회청색, 주요 행동에만 제한된 강조.',[(P/'screens'/f'{n}.png',l,s) for n,l,s in [('01-home-light','01  홈','현재 작업 / 준비 상태 / 결과'),('02-tasks','02  작업','실행 / 요청 중 / 실패의 의미 유지'),('03-data','03  데이터','선택 범위와 전송 목적지 확인'),('04-settings','04  설정','장비와 앱 설정의 그룹화')]])
board('00-overview-dark.png','Dark / Quiet contrast.','검정 반전이 아닌 차콜 표면 단계. 배경은 차분하게, 글자는 명확하게.',[(P/'screens'/f'{n}.png',l,s) for n,l,s in [('05-home-dark','01  홈','차콜 위계 / 작고 분명한 행동'),('18-tasks-dark','02  작업','상태와 다음 행동'),('19-data-dark','03  데이터','목적지 / 선택 / 하단 행동'),('20-settings-dark','04  설정','동일한 중립색 계열의 섹션')]])
board('00-before-after.png','V6 → V7 / One color family.','섹션마다 다른 색 온도를 섞는 대신 같은 계열에서 명도만 달리했습니다.',[
(P/'reference/previous_v6/01-home-light.png','LIGHT / V6','차가운 회색 + 따뜻한 회색'),(P/'screens/01-home-light.png','LIGHT / V7','하나의 회청색 표면 체계'),(P/'reference/previous_v6/05-home-dark.png','DARK / V6','여러 색 온도의 섹션'),(P/'screens/05-home-dark.png','DARK / V7','같은 차콜 계열의 명도 단계')])
# Style palette board; values are token data, not color-preference findings.
im=Image.new('RGB',(1680,1120),base);d=ImageDraw.Draw(im)
text(d,(72,44),'Slate Harmony / Color roles',48,True)
text(d,(74,118),'색은 적게, 위계는 명확하게. 정확한 값은 tokens.json을 사용합니다.',23,False,muted)
roles=[('canvas','전체 바탕'),('surface','기본 표면'),('sectionSoft','부드러운 섹션'),('sectionRaised','강조 섹션'),('hero','현재 작업'),('primary','주요 행동')]
for row,theme in enumerate(['light','dark']):
 y=195+row*350;ts=t[theme];text(d,(74,y),theme.upper(),24,True)
 for i,(role,label) in enumerate(roles):
  x=74+i*260;d.rounded_rectangle((x,y+48,x+230,y+212),radius=18,fill=ts[role],outline=ts['border'],width=1)
  fg=ts['onPrimary'] if role=='primary' else ts['ink'];text(d,(x+20,y+76),'Aa',40,True,fg)
  text(d,(x+20,y+142),ts[role],20,True,fg)
  text(d,(x,y+228),label,21,True);text(d,(x,y+262),role,16,False,muted)
text(d,(74,925),'기본 · 부드러움 · 강조 = 색 이름이 아니라 역할입니다.',25,True)
text(d,(74,982),'경고·실패만 국소 의미색 사용 · 색만으로 상태를 전달하지 않음 · 사용자 편안함은 별도 검증 필요',19,False,muted)
text(d,(74,1050),'연구를 참고한 신규 설계이며 논문에서 검증된 최적 HEX 팔레트가 아닙니다.',18,False,muted)
im.save(P/'screens/00-color-system.png')
# All-state board.
spec=json.loads((P/'design/scene-spec.json').read_text());w=260;gap=34;left=48;top=150;cellh=round(892*w/412)+62;cols=4;rows=math.ceil(len(spec['screens'])/cols)
im=Image.new('RGB',(left*2+cols*w+(cols-1)*gap,top+rows*cellh+30),base);d=ImageDraw.Draw(im)
text(d,(48,30),'Slate Harmony / 32 screen states',34,True);text(d,(48,88),'로컬 시안 · 실제 장비 연결 없음 · 같은 토큰을 라이트/다크 전체에 적용',17,False,muted)
for i,s in enumerate(spec['screens']):
 x=left+(i%cols)*(w+gap);y=top+(i//cols)*cellh;h=place(im,d,P/'screens'/(s['key']+'.png'),x,y,w);text(d,(x,y+h+13),s['key'],12,True)
im.save(P/'screens/00-all-states.png')
# Internal review sheet, kept in evidence with source provenance.
a=im.copy();a.thumbnail((1200,6000));a.save(P/'evidence/review-contact-sheet.png')
print('6 boards written')
