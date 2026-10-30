import { afterEach, expect, it, vi } from 'vitest'
import * as Vue from 'vue'
import { createRenderer, nextTick, ssrContextKey } from 'vue'
import { compile } from '@vue/compiler-dom'
import { compileScript, parse } from '@vue/compiler-sfc'
import ts from 'typescript'
import { createPinia, setActivePinia } from 'pinia'
import { useAuthStore } from '../../stores/auth'
import { useDraftStore } from '../../stores/drafts'
import ReassignmentPanel from './ReassignmentPanel.vue'
import source from './ReassignmentPanel.vue?raw'
const control=vi.hoisted(()=>({emit:((_event:string,_value:unknown)=>{}) as (event:string,value:unknown)=>void,resolve:((_v:boolean)=>{}) as (value:boolean)=>void}))
vi.mock('./AssignmentPanel.vue',()=>({default:{props:['disabled'],emits:['selected','ready'],setup(_props:unknown,{emit}:{emit:(event:string,value:unknown)=>void}){control.emit=emit;return()=>null}}}))
vi.mock('../common/InlineConfirmation.vue',()=>({default:{render:()=>null}}))
vi.mock('../../composables/useBusinessConfirmation',async()=>{const {shallowRef}=await import('vue');return {useBusinessConfirmation:()=>{const message=shallowRef('');return {message,ask:async()=>{message.value='确认中';return new Promise<boolean>(resolve=>{control.resolve=resolve})},answer(){}}}}})
interface Node {tag:string;text:string;props:Record<string,unknown>;children:Node[];parent:Node|null;addEventListener:()=>void;getRootNode:()=>object}
const node=(tag:string,text=''):Node=>({tag,text,props:{},children:[],parent:null,addEventListener(){},getRootNode:()=>({})})
const renderer=createRenderer<Node,Node>({createElement:tag=>node(tag),createText:text=>node('#text',text),createComment:text=>node('#comment',text),insert(child,parent){child.parent=parent;parent.children.push(child)},remove(child){if(child.parent)child.parent.children.splice(child.parent.children.indexOf(child),1)},setText(child,text){child.text=text},setElementText(child,text){child.text=text;child.children=[]},patchProp(child,key,_old,value){child.props[key]=value},parentNode:child=>child.parent,nextSibling:()=>null,insertStaticContent(text,parent){const child=node('#static',text);child.parent=parent;parent.children.push(child);return [child,child]}})
const all=(root:Node):Node[]=>[root,...root.children.flatMap(all)]
afterEach(()=>{vi.restoreAllMocks();vi.unstubAllGlobals()})
it('qualification_loss_during_confirmation_clears_invalid_person_but_keeps_reason',async()=>{
  const pinia=createPinia();setActivePinia(pinia);vi.stubGlobal('Document',class {});vi.stubGlobal('ShadowRoot',class {});useAuthStore().setConfirmedUser({id:'10',loginName:'admin',name:'管理员',phone:'13800000000',role:'ADMIN',enabled:true,reviewStatus:null,version:0,capabilities:{canBrowseCampus:true,canCreateRequest:false,canWorkOnTasks:false,canManage:true}})
  const descriptor=parse(source).descriptor,code=compile(descriptor.template!.content,{mode:'function',prefixIdentifiers:true,bindingMetadata:compileScript(descriptor,{id:'assignment-confirm-test'}).bindings}).code
  const component={...ReassignmentPanel,render:new Function('Vue',ts.transpileModule(code,{compilerOptions:{target:ts.ScriptTarget.ES2022}}).outputText)(Vue)}
  const root=node('root'),app=renderer.createApp(component,{detail:{id:'44',workOrderNo:'WO44',version:0,status:'REASSIGN_PENDING',allowedActions:['ASSIGN'],assignedTechnician:null},refresh:async()=>{}}).use(pinia);app.provide(ssrContextKey,{modules:new Set()});app.mount(root)
  try {
    const draft=useDraftStore().action('10','ASSIGN_DRAFT','44');draft.reason='必须保留的改派原因';control.emit('ready',true);control.emit('selected',{id:'51',name:'维修人',enabled:true,reviewStatus:'APPROVED',eligible:true,refusal:[]});await nextTick()
    const submit=all(root).find(n=>n.tag==='button' && n.text==='核对并重新分派')!;const pending=(submit.props.onClick as ()=>Promise<void>)();await nextTick()
    control.emit('selected',null);await nextTick();expect(draft.selectedId).toBe('');expect(draft.reason).toBe('必须保留的改派原因');expect(submit.props.disabled).toBe(true)
    control.resolve(false);await pending
  } finally {app.unmount()}
})
