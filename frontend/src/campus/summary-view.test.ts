import { afterEach, expect, it, vi } from 'vitest'
import * as Vue from 'vue'
import { createRenderer, nextTick, ssrContextKey } from 'vue'
import { compile } from '@vue/compiler-dom'
import { compileScript, parse } from '@vue/compiler-sfc'
import ts from 'typescript'
const components=import.meta.glob<{default: Vue.Component}>('../components/campus/Location*Panel.vue',{eager:true})
const markers=import.meta.glob<{default: Vue.Component}>('../components/campus/LocationSummaryMarkers.vue',{eager:true})
const raw=import.meta.glob<string>('../components/campus/Location*.vue',{eager:true,query:'?raw',import:'default'})
interface Node {tag:string;text:string;props:Record<string,unknown>;children:Node[];parent:Node|null}
const node=(tag:string,text=''):Node=>({tag,text,props:{},children:[],parent:null})
const renderer=createRenderer<Node,Node>({createElement:tag=>node(tag),createText:text=>node('#text',text),createComment:text=>node('#comment',text),insert(child,parent,anchor){if(child.parent){const i=child.parent.children.indexOf(child);if(i>=0)child.parent.children.splice(i,1)}child.parent=parent;const i=anchor?parent.children.indexOf(anchor):-1;if(i>=0)parent.children.splice(i,0,child);else parent.children.push(child)},remove(child){if(child.parent)child.parent.children.splice(child.parent.children.indexOf(child),1)},setText(child,text){child.text=text},setElementText(child,text){child.text=text;child.children=[]},patchProp(child,key,_old,value){child.props[key]=value},parentNode:child=>child.parent,nextSibling:child=>child.parent?.children[child.parent.children.indexOf(child)+1]??null,insertStaticContent(text,parent){const child=node('#static',text);child.parent=parent;parent.children.push(child);return[child,child]}})
const all=(root:Node):Node[]=>[root,...root.children.flatMap(all)]
function mount(name:string,props:Record<string,unknown>) {
  const path=`../components/campus/${name}.vue`, feature=(name==='LocationSummaryMarkers'?markers:components)[path]?.default
  expect(feature,'The actual campus component must exist').toBeDefined()
  const descriptor=parse(raw[path]!).descriptor,script=compileScript(descriptor,{id:name})
  const code=compile(descriptor.template!.content,{mode:'function',prefixIdentifiers:true,bindingMetadata:script.bindings}).code
  const component={...feature,render:new Function('Vue',ts.transpileModule(code,{compilerOptions:{target:ts.ScriptTarget.ES2022}}).outputText)(Vue)}
  const root=node('root'),app=renderer.createApp(component,props); app.provide(ssrContextKey,{modules:new Set()}); app.component('RouterLink',Vue.defineComponent({props:['to'],setup(props,{slots}){return ()=>Vue.h('a',{'data-to':JSON.stringify(props.to)},slots.default?.())}}));app.mount(root)
  return {root,app}
}
afterEach(()=>vi.unstubAllGlobals())
const counts={pendingAccept:1,reassignPending:0,inProgress:0,rework:0,pendingAcceptance:0,needsSupplement:0,pendingReview:1}
it('marker_click_only_opens_items',async()=> {
  const requests:{method:string}[]=[];vi.stubGlobal('fetch',vi.fn((_url,init)=>{requests.push({method:init?.method??'GET'});return Promise.resolve(new Response('{}')) }))
  const opened:string[]=[]
  const {root,app}=mount('LocationSummaryMarkers',{markers:[{locationId:'9007199254740993',name:'文博1 · 首层 · 101',category:'FAULT_PROCESSING',counts,visibleTotal:2,x:80,y:60}],onOpen:(id:string)=>opened.push(id)})
  const buttons=all(root).filter(n=>n.tag==='button');expect(buttons).toHaveLength(1)
  await (buttons[0]!.props.onClick as ()=>void)(); await nextTick()
  const mutationRequestsAfterMarkerClick=requests.filter(r=>r.method!=='GET').length
  expect(mutationRequestsAfterMarkerClick).toBe(0)
  expect(opened).toEqual(['9007199254740993']);expect(JSON.stringify(root.props)).not.toContain('mutation')
  expect(buttons[0]!.props['aria-label']).toContain('故障处理中'); app.unmount()
})
it('empty_location_has_exact_authorized_empty_copy_and_never_selects_a_first_record',()=> {
  const {root,app}=mount('LocationItemsPanel',{data:{location:{id:'22',name:'101'},counts:{...counts,pendingAccept:0,pendingReview:0},category:'NONE',visibleTotal:0,items:{records:[],total:0,page:1,pageSize:20},queriedAt:'2026-10-18T13:00:00.000Z'},loading:false,message:'',stale:false,lastSuccessAt:'',returnTo:'/campus?floorId=2&locationId=22',page:1})
  expect(all(root).map(n=>n.text).join(' ')).toContain('当前没有可查看的未完结记录')
  expect(all(root).filter(n=>n.tag==='a')).toHaveLength(0);app.unmount()
})
