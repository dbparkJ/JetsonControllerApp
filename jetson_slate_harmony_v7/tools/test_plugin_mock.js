// Structural smoke test. This mock does NOT render Figma and does NOT validate
// font metrics, auto-layout fidelity, subscription permissions or real APIs.
const fs=require('fs'),vm=require('vm'),path=require('path');let seq=0, posts=[],vars=[],styles=[],nodes=[];
class Node{
 constructor(type){this.type=type;this.id='mock:'+ ++seq;this.name=type;this.children=[];this.width=100;this.height=100;this.x=0;this.y=0;this.fills=[];this.strokes=[];this.layoutMode='NONE';this.componentPropertyReferences={};nodes.push(this);}
 appendChild(n){if(n.parent)n.parent.children=n.parent.children.filter(c=>c!==n);this.children.push(n);n.parent=this;}
 resize(w,h){if(!Number.isFinite(w)||!Number.isFinite(h)||w<=0||h<=0)throw Error('Invalid size '+w+'/'+h);this.width=w;this.height=h;}
 rescale(s){if(!(s>0))throw Error('Bad scale');this.width*=s;this.height*=s;}
 setBoundVariable(k,v){if(!v||!v.id)throw Error('Missing variable');(this.boundVariables??={})[k]=v.id;}
 findAll(f){return this.children.flatMap(n=>[...(f(n)?[n]:[]),...n.findAll(f)]);}
 findOne(f){return this.findAll(f)[0]||null;}
 addComponentProperty(name,type,value){const k=name+'#'+this.id;(this.props??={})[k]={type,value};return k;}
 clone(){let n=new Node(this.type);for(const k of Object.keys(this)){if(!['id','children','parent'].includes(k))n[k]=typeof this[k]==='object'?JSON.parse(JSON.stringify(this[k])):this[k];}for(const c of this.children)n.appendChild(c.clone());return n;}
 createInstance(){const n=this.clone();n.type='INSTANCE';n.mainComponent=this;return n;}
 setProperties(p){for(const[k,v]of Object.entries(p)){if(!this.props[k])throw Error('Unknown property '+k);const t=this.findOne(x=>x.componentPropertyReferences?.characters===k);if(t)t.characters=v;}}
 async setTextStyleIdAsync(id){if(!styles.some(s=>s.id===id))throw Error('Missing style');this.textStyleId=id;}
 async setReactionsAsync(v){this.reactions=v;}
 async exportAsync(){return new Uint8Array([137,80,78,71]);}
}
let page=new Node('PAGE');const figma={editorType:'figma',currentPage:page,root:{children:[page]},ui:{postMessage:m=>posts.push(m)},viewport:{scrollAndZoomIntoView(){}},commitUndo(){},closePlugin(){},showUI(){},
 async listAvailableFontsAsync(){return ['Regular','Medium','Bold'].map(style=>({fontName:{family:'Noto Sans KR',style}}))},async loadFontAsync(n){if(!n.family)throw Error('Missing font');},
 createFrame:()=>{let n=new Node('FRAME');page.appendChild(n);return n},createComponent:()=>{let n=new Node('COMPONENT');page.appendChild(n);return n},createText:()=>new Node('TEXT'),createNodeFromSvg:svg=>{if(!svg.includes('<svg'))throw Error('Invalid SVG');let n=new Node('FRAME'),v=new Node('VECTOR');v.strokes=[{type:'SOLID',color:{r:0,g:0,b:0}}];n.appendChild(v);return n},createTextStyle(){let s={id:'style:'+styles.length};styles.push(s);return s},combineAsVariants(list,parent){let n=new Node('COMPONENT_SET');parent.appendChild(n);for(let v of list)n.appendChild(v);return n},
 variables:{createVariableCollection(name){return{id:'collection:'+ ++seq,name,defaultModeId:'mode:0'}},createVariable(name,collection,type){if(!collection.id)throw Error('Missing collection');let v={id:'var:'+vars.length,name,type,setValueForMode(mode,value){this.value=value;},setVariableCodeSyntax(platform,syntax){this.syntax=syntax;}};vars.push(v);return v;},createVariableAlias(v){return{type:'VARIABLE_ALIAS',id:v.id}},setBoundVariableForPaint(p,k,v){if(!v?.id)throw Error('Missing paint variable');return{...p,boundVariables:{[k]:{type:'VARIABLE_ALIAS',id:v.id}}}}}};
const src=fs.readFileSync(path.join(__dirname,'../figma-plugin/code.js'),'utf8');const ctx=vm.createContext({figma,__html__:'<html></html>',console,Map,Set,JSON,Number,String,Object,Array,Promise,parseInt,Math});
(async()=>{vm.runInContext(src,ctx);await figma.ui.onmessage({type:'build'});let built=posts.find(p=>p.type==='built'),error=posts.find(p=>p.type==='error');if(error)throw Error(JSON.stringify(error));const expected=JSON.parse(fs.readFileSync(path.join(__dirname,'../design/scene-spec.json'),'utf8')).screens.length;if(built?.screens.length!==expected)throw Error('Screen count mismatch');const count=nodes.length;await figma.ui.onmessage({type:'build'});let dup=posts.at(-1);if(dup.type!=='error'||nodes.length!==count)throw Error('Duplicate guard failed');await figma.ui.onmessage({type:'export',key:'01-home-light'});if(posts.at(-1).type!=='export')throw Error('Export dispatch failed');const result={scope:'MOCK_ONLY_NOT_REAL_FIGMA',syntax:'PASS',importControlFlow:'PASS',screenCount:built.screens.length,componentVariants:built.componentVariants,mockNodeCount:nodes.length,variables:vars.length,textStyles:styles.length,duplicateGuard:'PASS',exportDispatch:'PASS',warnings:built.warnings,realFigmaExecution:'NOT_RUN',realFigmaPngExport:'NOT_RUN'};fs.writeFileSync(path.join(__dirname,'../evidence/plugin-mock-test.json'),JSON.stringify(result,null,2));console.log(result);})().catch(e=>{console.error(e);process.exit(1)});
