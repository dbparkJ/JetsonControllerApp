from pathlib import Path
import json, shutil, hashlib
P=Path(__file__).resolve().parents[1]
# Public token file is the color source of truth. Do not overwrite it on rebuild.
THEMES=json.loads((P/'design/tokens.json').read_text(encoding='utf-8'))['color']
ICONS={
'home':'<path d="m3 10 9-7 9 7v10a1 1 0 0 1-1 1h-5v-7H9v7H4a1 1 0 0 1-1-1Z"/>',
'play':'<path d="m8 5 11 7-11 7Z"/>',
'folder':'<path d="M3 7V5a1 1 0 0 1 1-1h5l2 3h9a1 1 0 0 1 1 1v11a1 1 0 0 1-1 1H4a1 1 0 0 1-1-1Z"/>',
'settings':'<path d="m9 3-.6 2.3-2 .9L4 5.6 2.5 8.2l1.7 1.7v2.3L2.5 14l1.5 2.6 2.4-.6 2 .9L9 19.2h3l.6-2.3 2-.9 2.4.6 1.5-2.6-1.7-1.8V9.9l1.7-1.7L17 5.6l-2.4.6-2-.9L12 3Z" transform="translate(2 1)"/><circle cx="12.5" cy="12" r="3"/>',
'bell':'<path d="M5 17h14l-2-3V9a5 5 0 0 0-10 0v5ZM10 20h4M12 3V2"/>',
'chevron':'<path d="m9 5 7 7-7 7"/>',
'down':'<path d="m6 9 6 6 6-6"/>',
'arrow':'<path d="M4 12h16m-6-6 6 6-6 6"/>',
'back':'<path d="M20 12H4m6-6-6 6 6 6"/>',
'check':'<circle cx="12" cy="12" r="9"/><path d="m8 12 3 3 5-6"/>',
'warning':'<path d="m12 3 10 18H2Z"/><path d="M12 9v5m0 3v.5"/>',
'info':'<circle cx="12" cy="12" r="9"/><path d="M12 10v7m0-11v1"/>',
'cloud':'<path d="M6 18a5 5 0 0 1-1-9 7 7 0 0 1 13-1 5 5 0 0 1 0 10M12 20V10m-4 4 4-4 4 4"/>',
'camera':'<path d="M3 7h4l2-3h6l2 3h4v13H3Z"/><circle cx="12" cy="13" r="4"/>',
'gnss':'<circle cx="12" cy="12" r="7"/><path d="M12 1v5m0 12v5M1 12h5m12 0h5"/><circle cx="12" cy="12" r="2"/>',
'sensor':'<rect x="6" y="6" width="12" height="12" rx="2"/><path d="M9 2v4m6-4v4M9 18v4m6-4v4M2 9h4m-4 6h4m12-6h4m-4 6h4"/><path d="M10 10h4v4h-4Z"/>',
'storage':'<rect x="3" y="4" width="18" height="16" rx="3"/><path d="M3 14h18m-5 3h2M6 8h12"/>',
'network':'<rect x="8" y="2" width="8" height="6" rx="1"/><path d="M12 8v6M4 14h16M4 14v3m8-3v3m8-3v3"/><path d="M2 17h4v4H2Zm8 0h4v4h-4Zm8 0h4v4h-4Z"/>',
'refresh':'<path d="M20 7v5h-5M4 17v-5h5"/><path d="M6 6a8 8 0 0 1 13 3M5 15a8 8 0 0 0 13 3"/>',
'power':'<path d="M12 2v10m-5-8a9 9 0 1 0 10 0"/>',
'plus':'<path d="M12 4v16M4 12h16"/>',
'search':'<circle cx="10.5" cy="10.5" r="6.5"/><path d="m16 16 5 5"/>',
'lock':'<rect x="5" y="10" width="14" height="11" rx="2"/><path d="M8 10V6a4 4 0 0 1 8 0v4m-4 4v3"/>',
'linkoff':'<path d="m3 3 18 18M9 7l2-2a5 5 0 0 1 7 7l-2 2M8 10l-2 2a5 5 0 0 0 7 7l2-2"/>',
'more':'<circle cx="12" cy="5" r="1"/><circle cx="12" cy="12" r="1"/><circle cx="12" cy="19" r="1"/>',
'moon':'<path d="M20 15A9 9 0 0 1 9 3a9 9 0 1 0 11 12Z"/>',
'fan':'<circle cx="12" cy="12" r="2"/><path d="M10 10C0 4 14-3 14 9m0 1c10-6 12 10 1 5m-3-1c0 12-13 5-3-1"/>',
'file':'<path d="M5 2h9l5 5v15H5Z"/><path d="M14 2v6h5M8 12h8m-8 4h6"/>',
'qr':'<path d="M3 3h6v6H3Zm12 0h6v6h-6ZM3 15h6v6H3Zm12 0h3v3h-3Zm3 3h3v3h-3ZM12 3v6m0 3h9M3 12h6m3 3v6"/>',
'log':'<path d="m4 6 5 6-5 6m7 0h9M2 3h20v18H2Z"/>',
'clock':'<circle cx="12" cy="12" r="9"/><path d="M12 6v6l4 2"/>',
'close':'<path d="m6 6 12 12M18 6 6 18"/>',
'checked':'<rect x="3" y="3" width="18" height="18" rx="4"/><path d="m7 12 3 3 7-7"/>',
'emptycheck':'<rect x="3" y="3" width="18" height="18" rx="4"/>',
'stop':'<rect x="5" y="5" width="14" height="14" rx="2"/>'}

def N(name,children=None,**s):
 d={'kind':'frame','name':name,'style':s}
 if children is not None:d['children']=children
 return d
def T(text,size=14,weight=400,color='ink',name=None,**s):
 return {'kind':'text','name':name or text[:30],'text':text,'style':dict(size=size,weight=weight,color=color,**s)}
def I(icon,color='muted',size=22,**s):
 return {'kind':'icon','name':'Icon/'+icon,'icon':icon,'style':dict(w=size,h=size,color=color,**s)}
def C(key,label,action=None,**s):
 return {'kind':'component','name':key+'/'+label,'component':key,'props':{'label':label},'action':action,'style':s}
def V(name,*ch,**s):return N(name,list(ch),dir='v',**s)
def H(name,*ch,**s):return N(name,list(ch),dir='h',align='center',**s)
def B(label,action=None,tone='primary',**s):return C('Button/'+tone,label,action,w='fill',**s)
def badge(label,tone='success'):return C('Badge/'+tone,label)
def line():return N('Divider',[],h=1,w='fill',bg='border')
def section(title,link=None,action=None):
 return H('Section/'+title,T(title,15,700,w='fill'),N('SectionAction',[T(link,13,500,'primary')],dir='h',h=48,align='center',action=action) if link else T('',1),w='fill',minH=32,justify='between')
def row(title,subtitle,icon,action=None,trailing=None,tone='ink',bg='surface',**s):
 r=H('Row/'+title,H('IconWell',I(icon,'primary'),bg='subtle',w=40,h=40,radius=12,justify='center'),V('Labels',T(title,16,500,tone),T(subtitle,13,400,'muted',w='fill') if subtitle else T('',1),gap=4,w='fill'),*( [T(trailing,13,500,'muted')] if trailing else []),I('chevron','muted',18) if action else I('check','success',20),w='fill',gap=12,pad=16,bg=bg,minH=76,**s)
 if action:r['action']=action
 return r
def iconbutton(icon,action,label=None):
 d=H('IconButton/'+icon,I(icon,'ink',22),w=48,h=48,radius=24,bg='surface',border='border',justify='center');d['action']=action;d['aria']=label or icon;return d
COMPONENTS={}
for tone,(bg,fg,bd) in {
 'primary':('primary','onPrimary',None),'accent':('accent','onAccent',None),'secondary':('surface','ink','controlBorder'),
 'heroSecondary':('hero','heroText','controlBorder'),'danger':('danger','onDanger',None),
 'disabled':('disabled','onDisabled',None)}.items():
 COMPONENTS['Button/'+tone]=H('Button',T('{label}',15,700,fg,name='label',alignText='center',w='fill'),padX=16,padY=14,minH=52,radius=14,bg=bg,border=bd,w=320,justify='center',gap=8)
for tone,(bg,fg) in {'success':('successBg','success'),'warning':('warningBg','warning'),'danger':('dangerBg','danger'),'info':('infoBg','info'),'neutral':('subtle','muted'),'hero':('accent','onAccent')}.items():
 COMPONENTS['Badge/'+tone]=H('Badge',T('{label}',12,700,fg,name='label'),bg=bg,radius=8,padX=10,padY=5,gap=4)

def nav(selected):
 items=[]
 for key,label,ico in [('home','홈','home'),('tasks','작업','play'),('data','데이터','folder'),('settings','설정','settings')]:
  d=V('Nav/'+key,H('SelectedIndicator',I(ico,'onNavSelected' if selected==key else 'muted',22),w=58,h=32,bg='navSelected' if selected==key else None,radius=16,justify='center'),T(label,12,700 if selected==key else 500,'ink' if selected==key else 'muted'),align='center',gap=4,w='fill',minH=56)
  d['action']=key;items.append(d)
 return H('Navigation',*items,padX=12,padTop=10,padBottom=16,gap=6,w='fill',bg='surface',borderTop='border',shrink=0)
def header(title='JETSON-01',root=True,offline=False,action=None):
 top=H('TitleRow',V('Title',T('현장 컨트롤' if title=='JETSON-01' else 'JETSON-01 · 선택 장비',11,500,'muted'),H('Heading',T(title,26,700),I('down','muted',18) if title=='JETSON-01' else T('',1),gap=8),gap=3,w='fill'),iconbutton('plus',action or 'preflight','작업 추가') if title=='작업' else iconbutton('search',action or 'search','현재 목록 검색') if title=='데이터' else iconbutton('bell','alerts','알림'),w='fill',gap=8)
 if root and title in ['작업','데이터']:
  top['children'].append(iconbutton('bell','alerts','알림'))
 if root:
  top['children'][0]['action']='devices'
 if not root:top=H('DetailTitle',iconbutton('back',action or 'home','뒤로'),V('Title',T('JETSON-01',12,500,'muted'),T(title,24,700),gap=2,w='fill'),iconbutton('more','details','더 보기'),gap=10,w='fill')
 return V('AppHeader',top,H('ConnectionContext',I('linkoff' if offline else 'network','warning' if offline else 'primary',14),T('연결 끊김 · 마지막 확인 38초 전' if offline else '제어 가능 · LAN',12,500,'warning' if offline else 'primary',w='fill'),T('데모',10,500,'muted'),w='fill',gap=6),padTop=18,padBottom=14,padX=20,gap=10,w='fill',bg='canvas',shrink=0)

def hero(offline=False):
 if offline:
  return V('CurrentTask/Stale',H('Meta',T('마지막으로 확인한 작업',12,500,'muted',w='fill'),badge('현재 상태 미확인','warning'),w='fill'),T('야간 라인 검사',23,700),T('마지막 확인: 실행 중',16,500),T('연결이 끊겨 현재 실행 상태는 알 수 없습니다.\n장비에서는 수집이 계속되고 있을 수 있습니다.',14,400,'muted',w='fill'),B('저장된 상태 보기','task-detail','secondary'),pad=20,gap=14,bg='surface',radius=24,w='fill')
 return V('CurrentTask/Running',H('Meta',T('현재 작업',12,500,'heroMuted',w='fill'),badge('실행 중','hero'),w='fill'),T('야간 라인 검사',24,700,'heroText'),H('TaskMetrics',V('Elapsed',T('42분',28,700,'heroText'),T('실행 시간',12,400,'heroMuted'),gap=2,w='fill'),N('Divider',[],w=1,h=42,bg='heroMuted',opacity=.35),V('Saved',T('128개',28,700,'heroText'),T('저장된 파일 · 02:14',12,400,'heroMuted'),gap=2,w='fill'),gap=22,w='fill'),H('Actions',B('상태 보기  →','task-detail','primary'),C('Button/heroSecondary','중지','stop-confirm',w=86),gap=10,w='fill'),pad=20,gap=16,bg='hero',radius=24,w='fill')
def metric(title,value,detail,ico):
 d=V('Metric/'+title,H('MetricTitle',I(ico,'primary',19),T(title,13,500,'muted'),gap=7),T(value,23,700),T(detail,12,400,'muted'),gap=5,pad=16,bg='surface',radius=18,w='fill');d['action']='sensors' if ico=='sensor' else 'data';return d
def home(offline=False):
 out=[]
 if offline:out.append(V('ConnectionNotice',H('WarningHeading',I('linkoff','warning'),T('장비 연결을 확인해 주세요',16,700,'warning',w='fill'),gap=8,w='fill'),T('화면과 선택은 그대로 보관했습니다.',14,400,'warning'),B('연결 상태 확인','connection','secondary'),bg='warningBg',radius=18,pad=16,gap=12,w='fill'))
 out.append(hero(offline))
 if offline:
  out.append(section('마지막 확인 정보'))
  out.append(V('ReadOnly',row('센서 상태','38초 전 · 수신 여부 확인 필요','sensor','sensors'),line(),row('전송 대기','연결 후 전송할 수 있습니다','cloud','data'),radius=18,bg='surface',w='fill'))
 else:
  out.append(V('Readiness',section('준비 상태','전체 보기','sensors'),H('ReadinessTiles',metric('센서','3개 수신','카메라 · GNSS · IMU','sensor'),metric('저장공간','153 GB','남음 · 68% 사용','storage'),gap=12,w='fill'),H('QuickTools',row('카메라','영상 확인','camera','camera',pad=12) if False else quick('카메라 확인','camera','camera'),quick('GNSS 위치','gnss','sensors'),gap=10,w='fill'),w='fill',gap=10))
  out.append(V('Result',H('ResultTitle',I('cloud','primary',24),V('ResultText',T('전송 대기 · 폴더 2개',17,700),T('대상 서버와 전송 내용을 확인하세요',13,400,'muted'),gap=5,w='fill'),I('chevron',size=18),gap=12,w='fill'),bg='surface',radius=18,pad=18,w='fill',action='data'))
 return out

def quick(label,ico,action):
 d=H('Quick/'+label,I(ico,'primary',20),T(label,13,500,'ink'),gap=7,justify='center',minH=52,bg='subtle',radius=14,w='fill');d['action']=action;return d

def taskcard(title,status,sub,kind='normal',action='task-detail'):
 tone={'running':'hero','pending':'info','failure':'danger','normal':'neutral'}[kind]
 bg='hero' if kind=='running' else 'surface';fg='heroText' if kind=='running' else 'ink';muted='heroMuted' if kind=='running' else 'muted'
 actions=H('TaskActions',B('상태 보기','task-detail','primary'),C('Button/heroSecondary','중지','stop-confirm',w=86),gap=10,w='fill') if kind=='running' else B('시작 요청 중…',None,'disabled') if kind=='pending' else B('문제 확인','task-failure','secondary')
 return V('Task/'+title,H('TaskHeading',T(title,20,700,fg,w='fill'),I('more',fg,20),w='fill'),badge(status,tone),T(sub,14,400,muted,w='fill'),actions,gap=12,pad=20,bg=bg,radius=22,w='fill')

def tasks():
 return [H('TaskFilters',badge('전체 3','neutral'),badge('실행 중 1','info'),badge('확인 필요 1','warning'),w='fill',gap=8),taskcard('야간 라인 검사','실행 중 · 42분','마지막 결과 02:14 · 파일 128개 저장','running'),taskcard('정밀 측위 로그','시작 요청 중','요청을 보냈습니다. 장비의 실행 확인을\n기다리고 있습니다.','pending'),taskcard('표면 결함 수집','실패','장비 인증을 확인하지 못해 시작하지 못했습니다.','failure')]

def tabs(a,b,which='a'):
 ar=[]
 for i,l in [('a',a),('b',b)]:
  d=H('Tab/'+l,T(l,14,700 if which==i else 500,'ink' if which==i else 'muted',alignText='center',w='fill'),minH=44,pad=10,bg='surface' if which==i else None,radius=12,w='fill');d['action']='data' if i=='a' else 'files';ar.append(d)
 return H('Subtabs',*ar,gap=4,pad=4,bg='subtle',radius=16,w='fill')

def selection(title,details,meta,selected=False,index=0):
 d=H('Selection/'+str(index),I('checked' if selected else 'emptycheck','primary',24),V('Content',T(title,16,700),T(details,13,400,'muted'),T(meta,12,500,'primary'),gap=6,w='fill'),gap=14,pad=18,bg='surface',border='primary' if selected else 'border',radius=18,w='fill',minH=112)
 d['action']='select:'+str(index);d['selected']=selected;return d

def data(files=False):
 return [tabs('전송 대기','장비 파일','b' if files else 'a'),V('Destination',H('DestinationTop',I('cloud','primary',22),T('GEON 수집 서버',16,700,w='fill'),T('변경',13,500,'primary'),gap=10,w='fill'),T('/ 현장조사 / JETSON-01',13,400,'muted'),T('LAN · 대상 서버 접근은 전송 시 확인',12,500,'muted'),pad=18,gap=8,bg='subtle',radius=18,w='fill',action='server'),section('전송할 폴더' if not files else '장비 저장소','선택 해제','clear-selection'),selection('야간 라인 검사','2026.09.08 / 02:14','파일 128개 · 42.8 MB',True,0),selection('정밀 측위 로그','2026.09.08 / 01:30','파일 24개 · 11.6 MB',False,1),H('HonestUnit',I('info','muted',18),T('폴더 단위로 전송합니다. 원본은 유지됩니다.',12,400,'muted',w='fill'),w='fill',gap=8),section('최근 전송','전체 이력','history'),row('전송 완료 · 폴더 1개','어제 22:04 · 서버 검증 완료','check','history')]

def datafooter():
 return V('TransferAction',H('SelectionSummary',T('폴더 1개 선택',14,700,w='fill',name='selectedLabel'),T('42.8 MB',14,700,'primary',name='selectedSize'),w='fill'),B('선택한 폴더 전송','transfer-confirm'),gap=10,padX=20,padTop=12,padBottom=12,w='fill',bg='surface',borderTop='border',shrink=0)

def settings():
 return [V('DeviceSummary',H('Device',I('sensor','heroText',32),V('Identity',T('JETSON-01',20,700,'heroText'),T('현장 장비 · 제어 가능',13,400,'heroMuted'),gap=4,w='fill'),I('down','heroText',18),w='fill',gap=14),pad=20,bg='hero',radius=22,w='fill',action='devices'),section('장비 설정'),V('DeviceSettings',row('네트워크 연결','연결 경로와 복구 방법','network','connection',trailing='LAN'),line(),row('센서 상태','카메라 · GNSS · IMU','sensor','sensors'),line(),row('팬 제어','자동 온도 조절','fan','fan'),line(),row('서버 대상','업로드할 서버와 인증 설정','cloud','server'),line(),row('장비 정보 · 진단','버전, 식별 정보, 상태 기록','info','diagnostics'),radius=18,bg='surface',w='fill'),section('앱 설정'),V('AppSettings',row('알림','문제가 생기면 필요한 정보만','bell','alerts'),line(),row('화면 테마','시스템 설정을 따릅니다','moon','theme'),radius=18,bg='surface',w='fill'),H('AutomaticPreserve',I('lock',size=18),T('화면 위치와 선택은 장비별로 자동 보관됩니다.',12,400,'muted',w='fill'),w='fill',gap=8),section('위험 동작'),V('DangerSettings',row('장비 재부팅','실행 중인 작업이 중단됩니다','refresh','reboot-confirm',tone='danger'),line(),row('전원 종료','앱에서 다시 켤 수 없습니다','power','shutdown-confirm',tone='danger'),line(),row('이 장비 등록 삭제','측정 파일 삭제와는 별개입니다','close','forget-confirm',tone='danger'),radius=18,bg='surface',w='fill')]

def taskdetail():
 return [hero(),V('TaskDetailTabs',H('DetailSegments',badge('요약','success'),badge('결과','neutral'),badge('로그','neutral'),gap=10),section('수집 상태'),row('결과 저장','파일 128개 · 마지막 저장 02:14','folder','data'),line(),row('센서 수신','카메라 · GNSS · IMU 수신 중','sensor','sensors'),w='fill',gap=8),V('TaskTimeline',T('최근 기록',16,700),T('02:14   결과 파일 저장 완료',14),T('02:13   센서 상태 동기화',14),T('01:32   장비에서 실행 확인',14),T('시각과 기록은 시안용 예시입니다.',12,400,'muted'),bg='surface',radius=18,pad=18,gap=12,w='fill'),B('작업 설정 열기','task-settings','secondary')]

def sensors():
 return [V('SensorSummary',T('센서 수신 상태',24,700),T('장비의 최신 응답을 기준으로 표시합니다.',14,400,'muted'),gap=8,w='fill'),V('SensorGroup',row('카메라','수신 중 · 마지막 프레임 1초 전','camera','camera'),line(),row('GNSS','RTK FIX · 장비 위치 · 2초 전','gnss','gnss'),line(),row('IMU','수신 중 · 외부 IMU · 1초 전','sensor','imu'),w='fill',radius=20,bg='surface'),V('PreviewOnDemand',H('PreviewIcon',I('camera','primary',36),w=72,h=72,bg='subtle',radius=22,justify='center'),T('영상은 필요할 때만',22,700),T('홈에서 자동 재생하지 않습니다.\n확인할 때 연결해 화면과 네트워크 부담을 줄입니다.',14,400,'muted',w='fill'),B('카메라 열기','camera'),pad=22,gap=16,w='fill',bg='surface',radius=22),H('SensorNote',I('info',size=18),T('보정 데이터 수신과 RTK FIX는 별도로 판정합니다.',12,400,'muted',w='fill'),gap=8,w='fill')]

def preflight():
 return [V('PreflightTitle',badge('시작 전 확인','neutral'),T('표면 결함 수집',26,700),T('필수 조건만 확인하고 작업을 시작하세요.',14,400,'muted'),gap=12,w='fill'),V('PreflightChecks',row('장비 제어 연결','LAN 연결 확인','network'),line(),row('저장공간','153 GB 남음','storage'),line(),row('카메라','작업 시작 후 수신 상태를 확인합니다','camera'),line(),row('GNSS','이 작업의 선택 조건 · 사용 가능','gnss'),radius=20,bg='surface',w='fill'),V('StartNote',T('시작 요청과 실행 확인은 다릅니다.',15,700,'info'),T('요청을 보낸 뒤 장비의 실행 응답을 기다립니다.\n응답이 늦어도 같은 요청을 반복하지 않습니다.',14,400,'info',w='fill'),bg='infoBg',radius=18,pad=18,gap=8,w='fill')]

def onboarding():
 return [V('Welcome',H('QrMark',I('qr','primary',72),w=128,h=128,bg='subtle',radius=32,justify='center'),T('첫 장비를\n연결해 볼까요?',32,700,w='fill'),T('장비를 한 번 등록하면\n수집 작업과 상태를 여기에서 확인합니다.',15,400,'muted',w='fill'),gap=22,padTop=32,w='fill'),V('Steps',row('01  장비 등록','장비의 QR 또는 등록 코드 사용','qr','scan'),line(),row('02  안전하게 연결','인증 정보를 확인한 뒤 제어 연결','lock','scan'),line(),row('03  상태 확인','연결 경로와 지원 기능 확인','sensor','scan'),w='fill',bg='surface',radius=20),B('QR로 장비 등록','scan'),B('등록 코드 직접 입력','manual','secondary')]

def connection():
 return [V('ConnectionIssue',badge('연결 복구','warning'),T('연결 경로를\n차례로 확인하세요',28,700),T('장비의 실행 작업을 임의로 종료하지 않습니다.',14,400,'muted',w='fill'),gap=14,w='fill'),V('ConnectionChecks',row('휴대전화 ↔ 장비','마지막 확인: LAN · 현재 응답 없음','linkoff','retry'),line(),row('장비 ↔ 서버','장비 연결 후 별도로 확인합니다','cloud','server'),line(),row('모바일 RTK 중계','파일 전송과 다른 연결 경로입니다','gnss','sensors'),bg='surface',radius=20,w='fill'),V('ConnectionActions',B('현재 경로로 다시 확인','retry'),B('다른 연결 방법 보기','network-options','secondary'),T('Wi-Fi Direct로 전환하면 공유기 연결이나\n진행 중인 전송에 영향을 줄 수 있습니다.',13,400,'muted',w='fill'),gap=12,w='fill')]

SCREENS=[]
def add(key,title,body,theme='light',selected='home',offline=False,root=True,footer=None,scale=1,dialog=None):
 hd=header(title,root,offline)
 content=V('Content',*body,padX=20,padTop=6,padBottom=24,gap=18,w='fill',grow=1,scroll=True)
 kids=[hd,content]
 if footer:kids.append(footer)
 if root:kids.append(nav(selected))
 else:kids.append(N('BottomSafeArea',[],w='fill',h=20,bg='canvas',shrink=0))
 tree=V('Screen/'+key,*kids,w=412,h=892,bg='canvas',clip=True)
 SCREENS.append(dict(key=key,title=title,theme=theme,scale=scale,tree=tree,dialog=dialog))

add('01-home-light','JETSON-01',home())
add('02-tasks','작업',tasks(),selected='tasks')
add('03-data','데이터',data(),selected='data',footer=datafooter())
add('04-settings','설정',settings(),selected='settings')
add('05-home-dark','JETSON-01',home(),theme='dark')
add('06-home-offline','JETSON-01',home(True),offline=True)
add('07-task-detail','작업 상세',taskdetail(),root=False)
add('08-sensors','센서 상태',sensors(),root=False)
add('09-preflight','시작 전 확인',preflight(),root=False,footer=V('StartAction',B('작업 시작 요청','start-demo'),padX=20,padTop=12,padBottom=8,w='fill',bg='surface'))
add('10-onboarding','장비 등록',onboarding(),root=False)
add('11-connection','연결 확인',connection(),root=False,offline=True)
add('12-home-large-type','JETSON-01',home(),scale=2)

def dialog(title,message,confirm,action,icon='refresh',danger=False):
 return V('Dialog',H('DialogIcon',I(icon,'danger' if danger else 'primary',28),w=52,h=52,bg='dangerBg' if danger else 'subtle',radius=18,justify='center'),T(title,22,700,w='fill'),T(message,15,400,'muted',w='fill'),H('DialogActions',B('취소','cancel','secondary'),B(confirm,action,'danger' if danger else 'primary'),w='fill',gap=12),pad=24,gap=18,w='fill',bg='surface',radius=26)
add('13-reboot-confirm','설정',settings(),selected='settings',dialog=dialog('JETSON-01을\n재부팅할까요?','실행 중인 야간 라인 검사와 장비 연결이\n중단됩니다. 다시 연결될 때까지 기다려 주세요.','재부팅','confirmed-reboot',danger=True))
add('14-transfer-confirm','데이터',data(),selected='data',footer=datafooter(),dialog=dialog('폴더 1개를 전송할까요?','JETSON-01 → GEON 수집 서버\n야간 라인 검사 · 파일 128개 · 42.8 MB\n장비의 원본 파일은 삭제하지 않습니다.','전송 요청','confirmed-transfer','cloud'))
add('15-settings-danger','설정',[section('앱 설정'),V('AppSettings',row('화면 테마','시스템 설정을 따릅니다','moon','theme'),line(),row('알림','중요한 문제와 작업 결과 알림','bell','alerts'),radius=18,bg='surface',w='fill'),H('AutomaticPreserve',I('lock',size=18),T('화면 위치와 선택은 장비별로 자동 보관됩니다.',12,400,'muted',w='fill'),w='fill',gap=8),section('위험 동작'),V('DangerSettings',row('장비 재부팅','실행 중인 작업이 중단됩니다','refresh','reboot-confirm',tone='danger'),line(),row('전원 종료','앱에서 다시 켤 수 없습니다','power','shutdown-confirm',tone='danger'),line(),row('이 장비 등록 삭제','측정 파일 삭제와는 별개입니다','close','forget-confirm',tone='danger'),radius=18,bg='surface',w='fill')],selected='settings')
add('16-data-files','데이터',data(True),selected='data',footer=datafooter())

add('17-device-picker','내 장비',[section('등록된 장비'),V('DeviceList',row('JETSON-01','현재 선택 · LAN · 제어 가능','sensor','home'),line(),row('JETSON-02','등록됨 · 현재 연결 안 됨','sensor','device-demo'),w='fill',bg='surface',radius=20),V('DeviceSwitchNote',T('장비를 바꿔도 작업은 그대로',22,700),T('장비별 화면 위치와 선택을 따로 보관합니다.\n장비 전환만으로 작업을 시작하거나 중지하지 않습니다.',14,400,'muted',w='fill'),w='fill',bg='subtle',radius=20,pad=20,gap=12),B('새 장비 등록','onboarding','secondary')],root=False)

# State-specific corrections: no connected device fiction on first registration.
first=next(x for x in SCREENS if x['key']=='10-onboarding')
first['tree']['children'][0]=V('OnboardingHeader',T('Jetson Controller',24,700),T('등록된 장비가 없습니다',13,400,'muted'),padX=20,padTop=24,padBottom=16,gap=8,w='fill',bg='canvas')
pf=next(x for x in SCREENS if x['key']=='09-preflight')
for r in pf['tree']['children'][1]['children'][1]['children']:
 if r.get('name')=='Row/카메라':r['children'][-1]=I('info','info',20)

# V7 evidence-informed color/section refinement; no command, capability or navigation changes.
# Group identity (not list order) chooses its background.
def paint_rows(group, bg):
 for child in group.get('children', []):
  if child.get('name', '').startswith('Row/'):
   child['style']['bg'] = bg
 return group

def soft_section(title_node, content_nodes, bg, name):
 return V(name, title_node, *content_nodes, w='fill', pad=14, gap=10,
          bg=bg, radius=20)

def retune_body(body):
 out=[];i=0
 while i < len(body):
  node=body[i];name=node.get('name','')
  if name=='Readiness':
   node['style'].update(bg='sectionSoft',pad=14,radius=20)
   out.append(node)
  elif name=='Result':
   node['style'].update(bg='sectionRaised')
   out.append(node)
  elif name in ['Section/장비 설정','Section/앱 설정','Section/위험 동작'] and i+1<len(body):
   role={'Section/장비 설정':'sectionSoft','Section/앱 설정':'sectionRaised','Section/위험 동작':'sectionDanger'}[name]
   group=body[i+1]
   if group.get('name') in ['DeviceSettings','AppSettings','DangerSettings']:
    paint_rows(group,role);group['style'].update(bg=role,clip=True)
    out.append(soft_section(node,[group],role,'SectionPanel/'+name.split('/',1)[1]));i+=1
   else:out.append(node)
  elif name in ['Section/전송할 폴더','Section/장비 저장소']:
   rows=[];j=i+1
   while j<len(body) and (body[j].get('name','').startswith('Selection/') or body[j].get('name')=='HonestUnit'):
    rows.append(body[j]);j+=1
   out.append(soft_section(node,rows,'sectionBase','SectionPanel/전송 대상'));i=j-1
  elif name=='Section/최근 전송' and i+1<len(body):
   group=body[i+1];group['style']['bg']='sectionRaised'
   out.append(soft_section(node,[group],'sectionRaised','SectionPanel/최근 전송'));i+=1
  elif name=='Destination':
   node['style'].update(bg='sectionSoft');out.append(node)
  elif name=='TaskFilters':
   node['style'].update(bg='sectionBase',pad=8,radius=14);out.append(node)
  elif name.startswith('Task/'):
   # Running is a softened task surface; pending/failure remain understated.
   if node['style']['bg']=='surface':node['style'].update(border='sectionBorder')
   out.append(node)
  elif name in ['SensorGroup','PreflightChecks','ConnectionChecks','DeviceList','ReadOnly']:
   node['style'].update(bg='sectionSoft',clip=True,border='sectionBorder');paint_rows(node,'sectionSoft');out.append(node)
  elif name in ['TaskDetailTabs','TaskTimeline','PreviewOnDemand']:
   bg='sectionRaised' if name=='TaskTimeline' else 'sectionSoft'
   node['style'].update(bg=bg,radius=20,pad=16,border='sectionBorder');out.append(node)
  else:out.append(node)
  i+=1
 return out

for screen in SCREENS:
 body=screen['tree']['children'][1]
 body['children']=retune_body(body['children'])
# Add dark counterparts so every root is explicitly reviewed in both themes.
import copy
for old,new in [('02-tasks','18-tasks-dark'),('03-data','19-data-dark'),('04-settings','20-settings-dark'),('15-settings-danger','21-settings-danger-dark'),('13-reboot-confirm','22-reboot-confirm-dark'),('06-home-offline','23-home-offline-dark'),('07-task-detail','24-task-detail-dark'),('08-sensors','25-sensors-dark'),('09-preflight','26-preflight-dark'),('10-onboarding','27-onboarding-dark'),('11-connection','28-connection-dark'),('12-home-large-type','29-home-large-type-dark'),('14-transfer-confirm','30-transfer-confirm-dark'),('16-data-files','31-data-files-dark'),('17-device-picker','32-device-picker-dark')]:
 dark=copy.deepcopy(next(x for x in SCREENS if x['key']==old));dark['key']=new;dark['theme']='dark';dark['tree']['name']='Screen/'+new;SCREENS.append(dark)

spec={'version':'7.0','name':'Jetson Controller / Slate Harmony V7','created':'2026-09-09','status':'local-prototype-not-native-app','screenSize':{'width':412,'height':892},'font':{'figma':'Noto Sans KR','local':'Noto Sans CJK KR','android':'FontFamily.SansSerif'},'themes':THEMES,'icons':ICONS,'components':COMPONENTS,'screens':SCREENS}
(P/'design'/'scene-spec.json').write_text(json.dumps(spec,ensure_ascii=False,indent=2),encoding="utf-8")
files=['70b5c464-6691-41f4-88e4-21009a22c53a.png','dddfe4d6-3fe8-4485-927e-c6f7c8337627.png','9ea46ed0-ce6d-41a3-a05e-f0892987154d.png','c9c35707-317e-4bf1-b76f-ed3a70fcd351.png','51447f44-33e7-44d7-97ec-d58e809e3c1f.png','cb2b1f2a-099a-4877-9d52-31094f5ef52e.png','bfd158ba-9792-4f62-a9dd-ade3883a4029.png','7b321118-3fd4-4c83-97a0-6d6f49f6bede.png']
manifest=[]
for i,f in enumerate(files,1):
 dest=P/'reference'/f'{i:02d}_user_reference.png'
 if not dest.exists():raise FileNotFoundError('Missing original reference: '+str(dest))
 manifest.append({'reference':f'S{i:02d}','file':str(dest.relative_to(P)),'source':f,'sha256':hashlib.sha256(dest.read_bytes()).hexdigest()})
(P/'reference'/'manifest.json').write_text(json.dumps(manifest,ensure_ascii=False,indent=2),encoding="utf-8")
print('Spec screens',len(SCREENS),'components',len(COMPONENTS),'icons',len(ICONS))
