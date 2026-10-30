import { afterEach, beforeEach, expect, it, vi } from 'vitest'
import * as Vue from 'vue'
import { createRenderer, h, nextTick, ssrContextKey } from 'vue'
import { compile } from '@vue/compiler-dom'
import { compileScript, parse } from '@vue/compiler-sfc'
import ts from 'typescript'
import { createPinia, setActivePinia } from 'pinia'
import { createMemoryHistory, createRouter, RouterView } from 'vue-router'
import View from './CampusView.vue'
import source from './CampusView.vue?raw'
const cutTargets=vi.hoisted(()=>[] as {kind:string}[])
import Markers from '../../components/campus/LocationSummaryMarkers.vue'
import markerSource from '../../components/campus/LocationSummaryMarkers.vue?raw'
import Selector from '../../components/campus/LocationSelector.vue'
import selectorSource from '../../components/campus/LocationSelector.vue?raw'
import Items from '../../components/campus/LocationItemsPanel.vue'
import itemsSource from '../../components/campus/LocationItemsPanel.vue?raw'
import Directory from '../../components/campus/LocationDirectory.vue'
import directorySource from '../../components/campus/LocationDirectory.vue?raw'
import { useAuthStore } from '../../stores/auth'
import type { CampusSummaries, LocationItems } from '../../api/campus'
import type { ReadResult } from '../../api/common'

const api=vi.hoisted(()=>({catalog:vi.fn(),summaries:vi.fn(),items:vi.fn()}))
vi.mock('../../api/account',async original=>({...await original<typeof import('../../api/account')>(),fetchCatalog:api.catalog}))
vi.mock('../../api/campus',async original=>({...await original<typeof import('../../api/campus')>(),fetchCampusSummaries:api.summaries,fetchLocationItems:api.items}))
vi.mock('../../components/campus/BusinessPanel.vue',()=>({default:{render:()=>null}}))
vi.mock('../../campus/CampusRenderer',()=>({CampusRenderer:class {
 constructor(private options:{onState:(s:object)=>void;onFrame:()=>void}){}
 async mount(){this.options.onState({level:'FLOOR',locationId:null,loading:false,error:''});this.options.onFrame()}
 async selectTarget(target:{kind:string;locationId?:string}){cutTargets.push(target);this.options.onState({level:target.kind==='LOCATION'?'DOOR':target.kind==='OVERVIEW'?'OVERVIEW':target.kind==='BUILDING'?'BUILDING':'FLOOR',locationId:target.locationId??null,loading:false,error:''});this.options.onFrame()}
 async enterLocation(id:string){cutTargets.push({kind:'INTERIOR'});this.options.onState({level:'INTERIOR',locationId:id,loading:false,error:''});this.options.onFrame()}
 projectAnchor(){return{visible:true,x:80,y:60}}
 projectBuildingAnchor(){return{visible:true,x:90,y:70}}
 resetOverview(){this.options.onState({level:'OVERVIEW',locationId:null,loading:false,error:''});this.options.onFrame()}
 dispose(){}
}}))
function client(feature:Vue.Component,raw:string,id:string){const d=parse(raw).descriptor;return{...feature,render:new Function('Vue',ts.transpileModule(compile(d.template!.content,{mode:'function',prefixIdentifiers:true,bindingMetadata:compileScript(d,{id}).bindings}).code,{compilerOptions:{target:ts.ScriptTarget.ES2022}}).outputText)(Vue)}}
Selector.render=client(Selector,selectorSource,'campus-selector-client').render
Markers.render=client(Markers,markerSource,'campus-marker-client').render
Items.render=client(Items,itemsSource,'campus-items-client').render
Directory.render=client(Directory,directorySource,'campus-directory-client').render
const component=client(View,source,'campus-view-client')
interface Node{tag:string;text:string;props:Record<string,unknown>;children:Node[];parent:Node|null;addEventListener:()=>void;getRootNode:()=>object}
const node=(tag:string,text=''):Node=>({tag,text,props:{},children:[],parent:null,addEventListener(){},getRootNode:()=>({})})
const renderer=createRenderer<Node,Node>({createElement:tag=>node(tag),createText:text=>node('#text',text),createComment:text=>node('#comment',text),insert(child,parent,anchor){if(child.parent)child.parent.children.splice(child.parent.children.indexOf(child),1);child.parent=parent;const i=anchor?parent.children.indexOf(anchor):-1;if(i<0)parent.children.push(child);else parent.children.splice(i,0,child)},remove(child){if(child.parent)child.parent.children.splice(child.parent.children.indexOf(child),1)},setText(child,text){child.text=text},setElementText(child,text){child.text=text;child.children=[]},patchProp(child,key,_old,value){child.props[key]=value},parentNode:child=>child.parent,nextSibling:child=>child.parent?.children[child.parent.children.indexOf(child)+1]??null,insertStaticContent(text,parent){const child=node('#static',text);child.parent=parent;parent.children.push(child);return[child,child]}})
const all=(root:Node):Node[]=>[root,...root.children.flatMap(all)]
const text=(root:Node):string=>all(root).map(n=>n.text).join(' ')
const classes=(n:Node)=>String(n.props.class??'')
const success=<T,>(data:T):ReadResult<T>=>({kind:'SUCCESS',data,requestId:'controlled-campus'})
const time='2026-10-19T13:00:00Z'
const counts={pendingAccept:1,reassignPending:0,inProgress:0,rework:0,pendingAcceptance:0,needsSupplement:0,pendingReview:0}
const location={id:'31',floorId:'21',code:'wb1-f1-r101',name:'101',type:'ROOM',use:'DORM_ROOM',reportable:true,canCreateRequest:false,displayOrder:0}
const summary=(category='FAULT_PROCESSING',overrides={})=>({configVersion:'campus-v1',queriedAt:time,locations:[{locationId:'31',category,visibleTotal:1,counts:{...counts,...overrides},queriedAt:time}]})
const items=():LocationItems=>({location:{...location,type:'ROOM'},counts:{...counts},category:'FAULT_PROCESSING',visibleTotal:1,items:{records:[{id:'41',resourceType:'WORK_ORDER',businessNo:'WO-41',equipmentName:'投影仪',status:'PENDING_ACCEPT',stage:'pendingAccept',createdAt:time,allowedActions:[]}],total:1,page:1,pageSize:20},queriedAt:time})
const apps:Vue.App[]=[];let browser:EventTarget
beforeEach(()=>{
 browser=new EventTarget();Object.assign(browser,{matchMedia:()=>Object.assign(new EventTarget(),{matches:false}),sessionStorage:{getItem:()=>null,removeItem:()=>{}}})
 vi.stubGlobal('window',browser);vi.stubGlobal('document',Object.assign(new EventTarget(),{visibilityState:'visible'}));vi.stubGlobal('Document',class{});vi.stubGlobal('ShadowRoot',class{})
 vi.stubGlobal('fetch',vi.fn(async()=>new Response('{}')))
 api.catalog.mockReset().mockResolvedValue(success({configVersion:'campus-v1',buildings:[{id:'11',code:'wb1',name:'文博1',type:'DORMITORY',displayOrder:0}],floors:[{id:'21',buildingId:'11',code:'wb1-f1',name:'首层',level:1,displayOrder:0}],locations:[location]}))
 api.summaries.mockReset().mockResolvedValue(success(summary()));api.items.mockReset().mockResolvedValue(success(items()))
})
afterEach(()=>{apps.splice(0).forEach(app=>app.unmount());vi.unstubAllGlobals()})
async function mount(role:'ADMIN'|'REPORTER'|'TECHNICIAN'='REPORTER'){
 const pinia=createPinia();setActivePinia(pinia);const auth=useAuthStore();auth.setConfirmedUser({id:'10',loginName:'synthetic',name:'测试',phone:'13800000000',role,enabled:true,reviewStatus:role==='TECHNICIAN'?'APPROVED':null,version:0,capabilities:{canBrowseCampus:true,canCreateRequest:role==='REPORTER',canManage:role==='ADMIN',canWorkOnTasks:role==='TECHNICIAN'}})
 const router=createRouter({history:createMemoryHistory(),routes:[{path:'/campus',component},{path:'/work-orders/:id',component:{render:()=>h('p','detail')}},{path:'/requests/:id',component:{render:()=>h('p')}},{path:'/admin/requests/:id',component:{render:()=>h('p')}},{path:'/list',component:{render:()=>h('p')}}]})
 await router.push('/campus');await router.isReady();const root=node('root'),app=renderer.createApp({render:()=>h(RouterView)}).use(pinia).use(router);apps.push(app);app.provide(ssrContextKey,{modules:new Set()});app.mount(root)
 await vi.waitFor(()=>expect(all(root).some(n=>n.tag==='button'&&classes(n).includes('summary-marker'))).toBe(true))
 return{root,router,auth}
}
function marker(root:Node){return all(root).find(n=>n.tag==='button'&&classes(n).split(' ').includes('summary-marker'))!}
it('cutting_a_single_area_floor_directly_enters_the_furnished_interior',async()=>{
 api.catalog.mockResolvedValue(success({configVersion:'campus-v1',buildings:[{id:'11',code:'ct1',name:'一食堂',type:'CANTEEN',displayOrder:0}],floors:[{id:'21',buildingId:'11',code:'ct1-f1',name:'首层',level:1,displayOrder:0}],locations:[{...location,code:'ct1-f1-area',type:'AREA',use:'CANTEEN_AREA'}]}))
 const {root}=await mount();cutTargets.length=0
 await click(all(root).find(n=>n.tag==='button'&&text(n)==='剖开当前层')!)
 expect(cutTargets.map(t=>t.kind)).toEqual(['LOCATION','INTERIOR'])
})
it.each(['ct1','ct2','ct3','gym'])('only %s keeps its authorized marker inside and removes return-floor',async code=>{
 api.catalog.mockResolvedValue(success({configVersion:'campus-v1',buildings:[{id:'11',code,name:code,type:code==='gym'?'GYM':'CANTEEN',displayOrder:0}],floors:[{id:'21',buildingId:'11',code:code+'-f1',name:'首层',level:1,displayOrder:0}],locations:[{...location,code:code+'-f1-area',type:'AREA',use:code==='gym'?'GYM_COURT':'CANTEEN_AREA'}]}))
 const {root,router}=await mount()
 await click(all(root).find(n=>n.tag==='button'&&text(n)==='剖开当前层')!)
 await vi.waitFor(()=>expect(marker(root)).toBeDefined())
 expect(all(root).some(n=>n.tag==='button'&&text(n)==='返回楼层')).toBe(false)
 await click(marker(root));await vi.waitFor(()=>expect(router.currentRoute.value.path).toBe('/work-orders/41'))
 expect(router.currentRoute.value.query.returnTo).toContain('observation=INTERIOR')
})
it.each(['wb1','wb2','wb3','zy','js','ba'])('%s retains its return-floor and existing hidden interior marker',async code=>{
 api.catalog.mockResolvedValue(success({configVersion:'campus-v1',buildings:[{id:'11',code,name:code,type:code.startsWith('wb')?'DORMITORY':'TEACHING',displayOrder:0}],floors:[{id:'21',buildingId:'11',code:code+'-f1',name:'首层',level:1,displayOrder:0}],locations:[{...location,code:code+'-f1-r101',use:code.startsWith('wb')?'DORM_ROOM':'CLASSROOM'}]}))
 const {root}=await mount();await click(all(root).find(n=>n.tag==='button'&&text(n)==='定位101')!);await click(all(root).find(n=>n.tag==='button'&&text(n)==='进入观察（开门）')!)
 expect(marker(root)).toBeUndefined();expect(all(root).some(n=>n.tag==='button'&&text(n)==='返回楼层')).toBe(true)
})
it('area interior removes its marker when refreshed authorized summaries no longer contain it',async()=>{
 api.catalog.mockResolvedValue(success({configVersion:'campus-v1',buildings:[{id:'11',code:'gym',name:'体育馆',type:'GYM',displayOrder:0}],floors:[{id:'21',buildingId:'11',code:'gym-f1',name:'首层',level:1,displayOrder:0}],locations:[{...location,code:'gym-f1-court',type:'AREA',use:'GYM_COURT'}]}))
 const {root}=await mount();await click(all(root).find(n=>n.tag==='button'&&text(n)==='剖开当前层')!);expect(marker(root)).toBeDefined()
 api.summaries.mockResolvedValue(success({...summary(),locations:[]}));await click(all(root).find(n=>n.tag==='button'&&text(n)==='刷新空间事项')!)
 await vi.waitFor(()=>expect(marker(root)).toBeUndefined())
})
function overviewFixture(){
 const bs=['wb1','gym','wb2','wb3','zy','js','ba','ct1','ct2','ct3'].map((code,i)=>({id:String(11+i),code,name:code,type:code==='gym'?'GYM':'DORMITORY',displayOrder:i}))
 api.catalog.mockResolvedValue(success({configVersion:'campus-v1',buildings:bs,floors:[{id:'21',buildingId:'11',code:'wb1-f1',name:'首层',level:1,displayOrder:0},{id:'22',buildingId:'11',code:'wb1-f2',name:'2层',level:2,displayOrder:1},{id:'23',buildingId:'12',code:'gym-f1',name:'首层',level:1,displayOrder:0}],locations:[location,{...location,id:'32',floorId:'22',code:'wb1-f2-r201',name:'201'},{...location,id:'33',floorId:'23',code:'gym-f1-court',type:'AREA',use:'GYM_COURT'}]}))
 const campus={configVersion:'campus-v1',queriedAt:time,locations:[{...summary().locations[0]!,visibleTotal:3,counts:{...counts,pendingAccept:1,pendingReview:2}},{...summary('NEEDS_SUPPLEMENT',{pendingAccept:0,needsSupplement:1}).locations[0]!,locationId:'32'},{...summary('PENDING_ACCEPTANCE',{pendingAccept:0,pendingAcceptance:1}).locations[0]!,locationId:'33'}]}
 api.summaries.mockImplementation((q:{floorId?:string})=>Promise.resolve(success(q.floorId?summary():campus)))
}
const buildingButtons=(root:Node)=>all(root).filter(n=>n.tag==='button'&&classes(n).split(' ').includes('building-label'))
it('overview sums authorized unfinished items over every floor and stage, including three items in one room',async()=>{
 overviewFixture();const {root}=await mount();await click(all(root).find(n=>n.tag==='button'&&text(n)==='回到校园总览')!)
 await vi.waitFor(()=>expect(buildingButtons(root)).toHaveLength(10))
 const b=buildingButtons(root);expect(text(b[0]!)).toContain('4');expect(classes(b[0]!)).toContain('building-has-repairs');expect(text(b[1]!)).toContain('1');expect(classes(b[1]!)).toContain('building-has-repairs')
 for(const empty of b.slice(2)){expect(text(empty)).toContain('0');expect(classes(empty)).not.toContain('building-has-repairs')}
 expect(api.summaries.mock.calls.some(([q])=>Object.keys(q).length===0)).toBe(true)
 await click(b[1]!);await click(all(root).find(n=>n.tag==='button'&&text(n)==='回到校园总览')!);await vi.waitFor(()=>expect(text(buildingButtons(root)[0]!)).toContain('4'))
})
it('overview clears red and shows zero after a successful refresh removes all visible items',async()=>{
 overviewFixture();const {root}=await mount();await click(all(root).find(n=>n.tag==='button'&&text(n)==='回到校园总览')!)
 await vi.waitFor(()=>expect(text(buildingButtons(root)[0]!)).toContain('4'))
 api.summaries.mockImplementation((q:{floorId?:string})=>Promise.resolve(success(q.floorId?summary():{configVersion:'campus-v1',queriedAt:time,locations:[]})))
 await click(all(root).find(n=>n.tag==='button'&&text(n)==='刷新空间事项')!)
 await vi.waitFor(()=>expect(buildingButtons(root).every(b=>text(b).includes('0')&&!classes(b).includes('building-has-repairs'))).toBe(true))
})
it('overview never reports a successful zero when the campus-wide count read failed',async()=>{
 overviewFixture();api.summaries.mockImplementation((q:{floorId?:string})=>Promise.resolve(q.floorId?success(summary()):{kind:'READ_FAILED',message:'无法读取楼栋数量'}))
 const {root}=await mount();await click(all(root).find(n=>n.tag==='button'&&text(n)==='回到校园总览')!)
 await vi.waitFor(()=>expect(buildingButtons(root)).toHaveLength(10));expect(buildingButtons(root).every(b=>text(b).includes('—'))).toBe(true)
})
it.each(['wb1','wb2','wb3','zy','js','ba','ct1','ct2','ct3','gym'])('building label %s performs the same action as cutting its floor',async code=>{
 const area=code.startsWith('ct')||code==='gym'
 api.catalog.mockResolvedValue(success({configVersion:'campus-v1',buildings:[{id:'11',code,name:code,type:area?(code==='gym'?'GYM':'CANTEEN'):code.startsWith('wb')?'DORMITORY':'TEACHING',displayOrder:0}],floors:[{id:'21',buildingId:'11',code:code+'-f1',name:'首层',level:1,displayOrder:0}],locations:[{...location,code:code+'-f1-target',type:area?'AREA':'ROOM',use:area?(code==='gym'?'GYM_COURT':'CANTEEN_AREA'):location.use}]}))
 const {root}=await mount();await click(all(root).find(n=>n.tag==='button'&&text(n)==='回到校园总览')!);cutTargets.length=0
 await click(buildingButtons(root)[0]!)
 expect(cutTargets.at(-1)?.kind).toBe(area?'INTERIOR':'FLOOR')
 expect(cutTargets.some(t=>t.kind==='FLOOR'||t.kind==='INTERIOR')).toBe(true)
})
it.each(['ct1','ct2','ct3','gym','wb1','wb2','wb3','zy','js','ba'])('floor dropdown enters the interior only for the four area buildings: %s',async code=>{
 const area=['ct1','ct2','ct3','gym'].includes(code)
 api.catalog.mockResolvedValue(success({configVersion:'campus-v1',buildings:[{id:'11',code,name:code,type:area?(code==='gym'?'GYM':'CANTEEN'):code.startsWith('wb')?'DORMITORY':'TEACHING',displayOrder:0}],floors:[1,2].map((level,i)=>({id:String(21+i),buildingId:'11',code:code+'-f'+level,name:level===1?'首层':'2层',level,displayOrder:i})),locations:[1,2].map((level,i)=>({...location,id:String(31+i),floorId:String(21+i),code:code+'-f'+level+'-target',name:level===1?'首层区域':'二层区域',type:area?'AREA':'ROOM',use:area?(code==='gym'?'GYM_COURT':'CANTEEN_AREA'):location.use}))}))
 const {root}=await mount()
 const floorSelect=()=>all(root).find(n=>n.tag==='label'&&text(n).trimStart().startsWith('楼层'))!.children.find(n=>n.tag==='select')!
 for(const id of ['22','21']){
  cutTargets.length=0
  ;(floorSelect().props.onChange as (event:object)=>void)({target:{value:id}})
  await vi.waitFor(()=>expect(cutTargets.at(-1)?.kind).toBe(area?'INTERIOR':'FLOOR'))
  expect(text(root).includes('已进入，可旋转与缩放观察。')).toBe(area)
  expect(floorSelect().props.value).toBe(id)
 }
})
it('single_marker_waits_for_focus_invalidated_read_before_direct_navigation',async()=>{
 const releases:((result:ReadResult<LocationItems>)=>void)[]=[]
 api.items.mockImplementation(()=>new Promise<ReadResult<LocationItems>>(resolve=>releases.push(resolve)))
 const {root,router}=await mount();const opening=click(marker(root))
 await vi.waitFor(()=>expect(releases).toHaveLength(1));browser.dispatchEvent(new Event('focus'))
 releases[0]!(success(items()));await vi.waitFor(()=>expect(releases).toHaveLength(2))
 expect(router.currentRoute.value.path).toBe('/campus');releases[1]!(success(items()));await opening
 await vi.waitFor(()=>expect(router.currentRoute.value.path).toBe('/work-orders/41'))
})
it('teaching_floor_keeps_authorized_projector_marker_and_business_entry',async()=>{
 api.catalog.mockResolvedValue(success({configVersion:'campus-v1',buildings:[{id:'11',code:'zy',name:'致用楼',type:'TEACHING',displayOrder:0}],floors:[{id:'21',buildingId:'11',code:'zy-f1',name:'首层',level:1,displayOrder:0}],locations:[{...location,code:'zy-f1-r101',use:'CLASSROOM'}]}))
 const {root,router}=await mount()
 expect(marker(root)).toBeDefined()
 await click(marker(root));await vi.waitFor(()=>expect(router.currentRoute.value.path).toBe('/work-orders/41'))
})
async function click(n:Node){await(n.props.onClick as()=>Promise<void>|void)();await nextTick()}
it.each(['ADMIN','REPORTER','TECHNICIAN'] as const)('single authorized marker directly opens shared order for %s',async role=>{
 const{root,router}=await mount(role);await click(marker(root));await vi.waitFor(()=>expect(router.currentRoute.value.path).toBe('/work-orders/41'))
 expect(router.currentRoute.value.query.returnTo).toContain('/campus?buildingId=11&floorId=21')
 expect(router.currentRoute.value.query.returnTo).toContain('itemsLocationId=31')
 expect(vi.mocked(fetch).mock.calls.every(([,options])=>!options?.method||options.method==='GET')).toBe(true)
})
it('multiple authorized items open a choice list instead of silently choosing its first record',async()=>{
 const data=items();data.visibleTotal=2;data.counts.pendingAccept=2;data.items.total=2;data.items.records.push({...data.items.records[0]!,id:'42',businessNo:'WO-42'});api.items.mockResolvedValue(success(data))
 const{root,router}=await mount();await click(marker(root));await vi.waitFor(()=>expect(text(root)).toContain('WO-42'));expect(router.currentRoute.value.path).toBe('/campus')
})
it('directory and projected marker share current category and remove completed status together',async()=>{
 const{root}=await mount();const directory=()=>all(root).find(n=>classes(n).includes('directory-card'))!;const card=()=>all(directory()).find(n=>n.tag==='li'&&text(n).includes('101'))!
 expect(classes(card())).toContain('FAULT_PROCESSING');expect(text(card())).toContain('故障处理中');expect(text(card())).toContain('待接单 1')
 api.summaries.mockResolvedValue(success(summary('PENDING_ACCEPTANCE',{pendingAccept:0,pendingAcceptance:1})));browser.dispatchEvent(new Event('campus:business-confirmed'))
 await vi.waitFor(()=>expect(classes(card())).toContain('PENDING_ACCEPTANCE'));expect(classes(marker(root))).toContain('PENDING_ACCEPTANCE');expect(text(card())).toContain('待验收 1')
 api.summaries.mockResolvedValue(success({configVersion:'campus-v1',queriedAt:time,locations:[]} as CampusSummaries));browser.dispatchEvent(new Event('campus:business-confirmed'))
 await vi.waitFor(()=>expect(classes(card())).not.toContain('PENDING_ACCEPTANCE'));expect(all(root).some(n=>n.tag==='button'&&classes(n).split(' ').includes('summary-marker'))).toBe(false)
 expect(text(card())).not.toContain('无故障')
})
it('a delayed old marker response cannot redirect after leaving campus',async()=>{
 let release!:(value:ReadResult<LocationItems>)=>void;api.items.mockImplementation(()=>new Promise<ReadResult<LocationItems>>(resolve=>{release=resolve}))
 const{root,router}=await mount();const pending=click(marker(root));await vi.waitFor(()=>expect(release).toBeTypeOf('function'));await router.push('/list');release(success(items()));await pending;expect(router.currentRoute.value.path).toBe('/list')
})
it.each([['ADMIN','/admin/requests/51'],['REPORTER','/requests/51']] as const)('single application uses the authorized detail entry for %s',async(role,expected)=>{
 const data=items();data.category='NEEDS_SUPPLEMENT';data.counts={...counts,pendingAccept:0,needsSupplement:1};data.items.records=[{...data.items.records[0]!,id:'51',resourceType:'REPAIR_REQUEST',businessNo:'RR-51',status:'NEEDS_SUPPLEMENT',stage:'needsSupplement',allowedActions:[]}];api.items.mockResolvedValue(success(data))
 const{root,router}=await mount(role);await click(marker(root));await vi.waitFor(()=>expect(router.currentRoute.value.path).toBe(expected))
})
it('a stale single record cannot be used for automatic navigation when the new read fails',async()=>{
 const{root,router}=await mount();const listButton=all(root).find(n=>n.tag==='button'&&text(n)==='101 · 故障处理中 · 1项')!
 await click(listButton);await vi.waitFor(()=>expect(text(root)).toContain('WO-41'));api.items.mockResolvedValue({kind:'READ_FAILED',message:'控制读取失败'})
 await click(marker(root));await vi.waitFor(()=>expect(text(root)).toContain('控制读取失败'));expect(router.currentRoute.value.path).toBe('/campus')
})
it('a delayed old marker response cannot redirect after identity changes',async()=>{
 let release!:(value:ReadResult<LocationItems>)=>void;api.items.mockImplementation(()=>new Promise<ReadResult<LocationItems>>(resolve=>{release=resolve}))
 const{root,router,auth}=await mount();await click(marker(root));await vi.waitFor(()=>expect(release).toBeTypeOf('function'));auth.clearIdentity();release(success(items()));await nextTick();await nextTick();expect(router.currentRoute.value.path).toBe('/campus');expect(text(root)).not.toContain('WO-41')
})
