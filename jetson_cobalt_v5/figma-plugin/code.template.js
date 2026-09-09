/** Cobalt Field V5 — local Figma development plugin.
 * No network, API key, repository writes or device commands.
 * Main process tested with a mock API only; real Figma execution remains unverified.
 */
const DATA = __DATA__;
const VERSION = 'Cobalt Field V5';
let busy=false, screenNodes=new Map(), componentCache=new Map(), iconCache=new Map();
let colors={},dims={},textStyles=new Map(),fontNames={},library,stage='idle',allCreated=[];
const actionNodes=[]; const warnings=[];
const rgb=h=>({r:parseInt(h.slice(1,3),16)/255,g:parseInt(h.slice(3,5),16)/255,b:parseInt(h.slice(5,7),16)/255});
const paint=(hex)=>({type:'SOLID',color:rgb(hex)});
const camel=s=>s[0].toUpperCase()+s.slice(1);
const track=n=>{allCreated.push(n.id);return n};
const log=(message)=>figma.ui.postMessage({type:'progress',message,stage});
function colorPaint(theme,role){const base=paint(DATA.themes[theme][role]||role||'#1D293B');return colors[theme]&&colors[theme][role]?figma.variables.setBoundVariableForPaint(base,'color',colors[theme][role]):base;}
function dimension(n,property,value,scope='GAP'){
 if(!Number.isFinite(value))return;
 n[property]=value;
 const k=scope+'/'+value;
 if(dims[k]&&typeof n.setBoundVariable==='function')n.setBoundVariable(property,dims[k]);
}
async function fonts(){
 const available=await figma.listAvailableFontsAsync();
 for(const [weight,preferences]of [[400,['Regular']],[500,['Medium','Regular']],[700,['Bold','Medium']]]){
  let found;
  for(const family of ['Noto Sans KR','Noto Sans CJK KR','Noto Sans']){
   found=available.find(f=>f.fontName.family===family&&preferences.includes(f.fontName.style));
   if(found)break;
  }
  if(!found)throw new Error('한글 폰트 Noto Sans KR의 Regular/Medium/Bold를 사용할 수 없습니다. Figma 글꼴 가용성을 확인해 주세요.');
  fontNames[weight]=found.fontName;await figma.loadFontAsync(found.fontName);
 }
}
async function createTokens(){
 const primitives=figma.variables.createVariableCollection('CFV5 / Primitives');
 const raw={};const unique=new Set(Object.values(DATA.themes).flatMap(t=>Object.values(t)));
 for(const hex of unique){let v=figma.variables.createVariable('color/'+hex.slice(1),primitives,'COLOR');v.scopes=['FRAME_FILL','SHAPE_FILL','TEXT_FILL','STROKE'];v.hiddenFromPublishing=true;v.setValueForMode(primitives.defaultModeId,rgb(hex));raw[hex]=v;}
 // Separate single-mode collections avoid depending on multiple-mode entitlements.
 for(const theme of ['light','dark']){
  let c=figma.variables.createVariableCollection('CFV5 / '+camel(theme));colors[theme]={};
  for(const [role,hex]of Object.entries(DATA.themes[theme])){
   let v=figma.variables.createVariable('color/'+role,c,'COLOR');v.scopes=['FRAME_FILL','SHAPE_FILL','TEXT_FILL','STROKE'];v.setValueForMode(c.defaultModeId,figma.variables.createVariableAlias(raw[hex]));v.setVariableCodeSyntax('ANDROID','CobaltField.colors.'+role);colors[theme][role]=v;
  }
 }
 const d=figma.variables.createVariableCollection('CFV5 / Dimensions');
 const nums=new Set([0,1,2,3,4,5,6,7,8,10,12,14,16,18,20,22,24,26,28,32,40,48,52,56,72,76,86,112,128,320,412,892]);
 for(const scope of ['GAP','CORNER_RADIUS'])for(const value of nums){const v=figma.variables.createVariable((scope==='GAP'?'spacing/':'radius/')+value,d,'FLOAT');v.scopes=[scope];v.setValueForMode(d.defaultModeId,value);v.setVariableCodeSyntax('ANDROID',value+'.dp');dims[scope+'/'+value]=v;}
}
async function styleFor(snap){
 const weight=snap.style.weight||400;const size=snap.fontSize||snap.style.size||14;const lh=snap.lineHeight||size*1.5;const key=weight+'/'+size+'/'+lh;
 if(textStyles.has(key))return textStyles.get(key);
 const st=figma.createTextStyle();st.name='CFV5/'+(size>=24?'Display':size>=16?'Body':'Label')+'/'+size+'/'+weight;st.fontName=fontNames[weight>=600?700:weight>=500?500:400];st.fontSize=size;st.lineHeight={unit:'PIXELS',value:lh};st.letterSpacing={unit:'PIXELS',value:-.2};textStyles.set(key,st);return st;
}
function sizeFrame(n,snap){
 const s=snap.style||{};n.resize(Math.max(1,snap.w||100),Math.max(1,snap.h||40));
 n.layoutMode=s.dir==='h'?'HORIZONTAL':'VERTICAL';
 n.primaryAxisSizingMode=s.h||s.scroll||s.grow?'FIXED':(s.dir==='h'?'FIXED':'AUTO');
 n.counterAxisSizingMode=s.dir==='h'&&!s.h?'AUTO':'FIXED';
 n.counterAxisAlignItems=s.align==='center'?'CENTER':s.align==='end'?'MAX':'MIN';
 n.primaryAxisAlignItems=s.justify==='center'?'CENTER':s.justify==='between'?'SPACE_BETWEEN':s.justify==='end'?'MAX':'MIN';
 const pd=s.pad||0;dimension(n,'paddingTop',s.padTop??s.padY??pd);dimension(n,'paddingBottom',s.padBottom??s.padY??pd);dimension(n,'paddingLeft',s.padX??pd);dimension(n,'paddingRight',s.padX??pd);dimension(n,'itemSpacing',s.gap||0);
 if(s.radius)dimension(n,'cornerRadius',s.radius,'CORNER_RADIUS');
 if(s.minH)n.minHeight=s.minH;
 n.clipsContent=!!(s.clip||s.scroll);
 if(s.scroll)n.overflowDirection='VERTICAL_SCROLLING';
 if(s.opacity!==undefined)n.opacity=s.opacity;
}
function look(n,s,theme){
 n.fills=s.bg?[colorPaint(theme,s.bg)]:[];
 if(s.border||s.borderTop){n.strokes=[colorPaint(theme,s.border||s.borderTop)];n.strokeWeight=1;n.strokeAlign='INSIDE';if(s.borderTop&&'strokeTopWeight'in n){n.strokeTopWeight=1;n.strokeBottomWeight=0;n.strokeLeftWeight=0;n.strokeRightWeight=0;}}
}
function attach(parent,node,snap){
 parent.appendChild(node);const s=snap.style||{};
 if(parent.layoutMode==='VERTICAL'&&s.w==='fill')node.layoutAlign='STRETCH';
 if(parent.layoutMode==='HORIZONTAL'&&s.w==='fill')node.layoutGrow=1;
 if(parent.layoutMode==='VERTICAL'&&s.grow)node.layoutGrow=1;
}
async function createIconMasters(){
 let x=24,y=24,i=0;
 for(const [name,path]of Object.entries(DATA.icons)){
  const c=track(figma.createComponent());c.name='CFV5/Icon/'+name;c.description='24px outline icon. Change semantic stroke role on the instance; decorative icons have no spoken label in the app.';c.resize(24,24);c.fills=[];
  const svg=track(figma.createNodeFromSvg(`<svg xmlns="http://www.w3.org/2000/svg" width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="#1D293B" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round">${path}</svg>`));
  c.appendChild(svg);svg.x=0;svg.y=0;svg.constraints={horizontal:'SCALE',vertical:'SCALE'};library.appendChild(c);c.x=x+(i%20)*72;c.y=y+Math.floor(i/20)*64;iconCache.set(name,c);i++;
 }
}
async function createNode(snap,parent,theme,scale=1,master=false){
 const s=snap.style||{};let n;
 if(snap.component&&!master){
  const id=snap.component+'/'+theme+'/'+scale;
  let entry=componentCache.get(id);
  if(!entry){
   const copy=JSON.parse(JSON.stringify(snap));delete copy.component;
   n=track(figma.createComponent());sizeFrame(n,copy);look(n,s,theme);library.appendChild(n);n.x=24;n.y=200+componentCache.size*90;
   n.name='Tone='+snap.component.split('/')[1]+', Theme='+theme+', TextScale='+scale;
   n.description='Cobalt Field '+snap.component+'. Editable label, auto layout, semantic colors. Sample state is not device telemetry.';
   for(const child of(copy.children||[]))await createNode(child,n,theme,scale);
   const label=n.findOne(x=>x.type==='TEXT'&&x.name==='label');let prop;
   if(label){prop=n.addComponentProperty('Label','TEXT',label.characters);label.componentPropertyReferences={...label.componentPropertyReferences,characters:prop};}
   entry={node:n,prop,family:snap.component.split('/')[0]};componentCache.set(id,entry);
  }
  n=track(entry.node.createInstance());
  const l=(snap.children||[]).find(x=>x.name==='label');if(entry.prop&&l)n.setProperties({[entry.prop]:l.text});
  n.resize(Math.max(1,snap.w),Math.max(1,snap.h));n.name=snap.name;attach(parent,n,snap);
 }else if(snap.kind==='text'){
  n=track(figma.createText());n.fontName=fontNames[(s.weight||400)>=600?700:(s.weight||400)>=500?500:400];await figma.loadFontAsync(n.fontName);n.characters=snap.text||'';n.name=snap.name;
  await n.setTextStyleIdAsync((await styleFor(snap)).id);n.fills=[colorPaint(theme,s.color||'ink')];n.textAutoResize='HEIGHT';n.resize(Math.max(1,snap.w),Math.max(1,snap.h));n.textAlignHorizontal=s.alignText==='center'?'CENTER':'LEFT';attach(parent,n,snap);
 }else if(snap.kind==='icon'){
  const c=iconCache.get(snap.icon);if(!c)throw new Error('알 수 없는 아이콘: '+snap.icon);
  n=track(c.createInstance());n.rescale((snap.w||24)/24);n.name=snap.name;attach(parent,n,snap);
  for(const sub of n.findAll(x=>'strokes'in x&&Array.isArray(x.strokes)&&x.strokes.length)){sub.strokes=sub.strokes.map(p=>p.type==='SOLID'?colorPaint(theme,s.color||'muted'):p);}
 }else{
  n=track(figma.createFrame());n.name=snap.name;sizeFrame(n,snap);look(n,s,theme);attach(parent,n,snap);
  for(const child of(snap.children||[]))await createNode(child,n,theme,scale);
 }
 if(snap.action)actionNodes.push({node:n,action:snap.action});
 return n;
}
async function createDocumentation(parent){
 const doc=track(figma.createFrame());doc.name='00 / Style guide & provenance';doc.resize(1800,270);doc.layoutMode='VERTICAL';doc.primaryAxisSizingMode='AUTO';doc.counterAxisSizingMode='FIXED';doc.paddingTop=32;doc.paddingBottom=32;doc.paddingLeft=32;doc.paddingRight=32;doc.itemSpacing=14;doc.fills=[paint('#F7F5F2')];parent.appendChild(doc);
 const texts=[['COBALT FIELD / 2026',16,700],['현장에서 중요한 것을 먼저.',42,700],['딥 네이비 · 코발트 블루 · 아이스 블루 · 따뜻한 오프화이트 / 홈 · 작업 · 데이터 · 설정',20,500],['이 파일은 로컬 플러그인으로 생성한 편집 가능한 디자인입니다. 수치와 상태는 시안용 예시이며 실제 앱 또는 장비 검증 결과가 아닙니다.',16,400],['레이어: Auto Layout · 텍스트 스타일 · 단일 모드 Light/Dark 변수 · Button/Badge variants · SVG 아이콘 인스턴스',14,400]];
 for(const [text,size,weight]of texts)await createNode({kind:'text',name:text,text,w:1736,h:size*1.5,fontSize:size,lineHeight:size*1.5,style:{w:'fill',size,weight,color:'ink'}},doc,'light');
 return doc;
}
async function build(){
 if(busy)return;busy=true;
 let root;
 try{
  if(figma.editorType!=='figma')throw new Error('Figma Design 파일에서 실행해 주세요. FigJam/Slides는 대상이 아닙니다.');
  if(figma.currentPage.children.some(n=>n.name==='CFV5 / Screens'))throw new Error('현재 페이지에 CFV5 / Screens가 이미 있습니다. 중복 생성을 막았습니다. 새 빈 페이지에서 실행하거나 기존 결과를 사용하세요.');
  allCreated=[];screenNodes=new Map();componentCache=new Map();iconCache=new Map();textStyles=new Map();actionNodes.length=0;warnings.length=0;
  stage='Fonts';log('한글 폰트 확인');await fonts();
  stage='Tokens';log('색상 · 간격 변수 생성');await createTokens();
  let maxX=0;for(const n of figma.currentPage.children)maxX=Math.max(maxX,n.x+n.width);
  library=track(figma.createFrame());library.name='CFV5 / Components';library.resize(1800,1900);library.x=maxX+100;library.y=100;library.fills=[paint('#F7F5F2')];library.clipsContent=false;
  await createIconMasters();
  root=track(figma.createFrame());root.name='CFV5 / Screens';root.layoutMode='VERTICAL';root.primaryAxisSizingMode='AUTO';root.counterAxisSizingMode='AUTO';root.itemSpacing=64;root.paddingTop=32;root.paddingBottom=32;root.paddingLeft=32;root.paddingRight=32;root.fills=[paint('#E7E9EF')];root.x=maxX+2020;root.y=100;
  await createDocumentation(root);
  stage='Screens';let row;
  for(let i=0;i<DATA.screens.length;i++){
   const snap=DATA.screens[i];log(`${i+1}/${DATA.screens.length} ${snap.key}`);
   if(i%4===0){row=track(figma.createFrame());row.name='Screen row '+(1+Math.floor(i/4));row.layoutMode='HORIZONTAL';row.primaryAxisSizingMode='AUTO';row.counterAxisSizingMode='AUTO';row.itemSpacing=48;row.fills=[];root.appendChild(row);}
   const group=track(figma.createFrame());group.name=snap.key+' / '+snap.title;group.layoutMode='VERTICAL';group.primaryAxisSizingMode='AUTO';group.counterAxisSizingMode='FIXED';group.resize(412,950);group.itemSpacing=12;group.fills=[];row.appendChild(group);
   await createNode({kind:'text',name:'Frame label',text:snap.key,w:412,h:24,fontSize:14,lineHeight:21,style:{w:'fill',size:14,weight:700,color:'ink'}},group,'light');
   const screen=await createNode(snap.tree,group,snap.theme,snap.scale);screen.exportSettings=[{format:'PNG',constraint:{type:'SCALE',value:2},suffix:'@2x'}];screenNodes.set(snap.key,screen);
   if(snap.dialog){
    const shade=track(figma.createFrame());shade.name='Confirmation scrim';shade.resize(412,892);shade.layoutMode='VERTICAL';shade.primaryAxisSizingMode='FIXED';shade.counterAxisSizingMode='FIXED';shade.primaryAxisAlignItems='CENTER';shade.counterAxisAlignItems='CENTER';shade.paddingLeft=24;shade.paddingRight=24;shade.fills=[{...paint('#101A30'),opacity:.4}];screen.appendChild(shade);shade.layoutPositioning='ABSOLUTE';shade.x=0;shade.y=0;await createNode(snap.dialog,shade,snap.theme,snap.scale);
   }
  }
  stage='Variants';log('버튼·배지 컴포넌트 정리');let cy=220;
  for(const family of ['Button','Badge']){
   const variants=[...componentCache.values()].filter(e=>e.family===family).map(e=>e.node);if(!variants.length)continue;
   const set=track(figma.combineAsVariants(variants,library));set.name='CFV5/'+family;set.description='Theme and tone variants. Typography is Noto Sans KR; app implementation uses native Compose semantics.';set.x=24;set.y=cy;set.layoutMode='HORIZONTAL';set.layoutWrap='WRAP';set.resize(1680,600);set.primaryAxisSizingMode='FIXED';set.counterAxisSizingMode='AUTO';set.itemSpacing=24;set.counterAxisSpacing=24;set.paddingTop=24;set.paddingBottom=24;set.paddingLeft=24;set.paddingRight=24;set.fills=[];cy+=family==='Button'?1100:600;
  }
  stage='Prototype';const links={home:'01-home-light',tasks:'02-tasks',data:'03-data',settings:'04-settings',devices:'17-device-picker',onboarding:'10-onboarding','task-detail':'07-task-detail',sensors:'08-sensors',preflight:'09-preflight',connection:'11-connection',files:'16-data-files','reboot-confirm':'13-reboot-confirm','transfer-confirm':'14-transfer-confirm'};
  for(const item of actionNodes){let dst=screenNodes.get(links[item.action]);if(!dst||!item.node.setReactionsAsync)continue;try{await item.node.setReactionsAsync([{trigger:{type:'ON_CLICK'},actions:[{type:'NODE',destinationId:dst.id,navigation:'NAVIGATE',transition:null,preserveScrollPosition:false}]}]);}catch(e){warnings.push('Prototype link '+item.action+': '+String(e));}}
  stage='Done';figma.currentPage.selection=[screenNodes.get('01-home-light')];figma.viewport.scrollAndZoomIntoView([screenNodes.get('01-home-light')]);figma.commitUndo();
  figma.ui.postMessage({type:'built',screens:[...screenNodes].map(([key,n])=>({key,id:n.id})),createdNodes:allCreated.length,componentVariants:componentCache.size,warnings,fontNames});
 }catch(e){if(root)root.name='CFV5 / PARTIAL — '+stage;figma.ui.postMessage({type:'error',message:String(e),stage,createdNodes:allCreated.length});}finally{busy=false;}
}
async function exportScreen(key){
 if(busy)return;const node=screenNodes.get(key);if(!node){figma.ui.postMessage({type:'error',message:'이 실행에서 생성한 화면이 없습니다. 먼저 화면을 생성해 주세요.'});return;}
 busy=true;try{const bytes=await node.exportAsync({format:'PNG',constraint:{type:'SCALE',value:2}});figma.ui.postMessage({type:'export',name:key+'-figma@2x.png',bytes:Array.from(bytes)});}catch(e){figma.ui.postMessage({type:'error',message:'PNG 내보내기 실패: '+String(e)});}finally{busy=false;}
}
figma.showUI(__html__,{width:420,height:600});
figma.ui.onmessage=async msg=>{if(!msg||typeof msg.type!=='string')return;if(msg.type==='build')await build();else if(msg.type==='export'&&typeof msg.key==='string')await exportScreen(msg.key);else if(msg.type==='close')figma.closePlugin();};
