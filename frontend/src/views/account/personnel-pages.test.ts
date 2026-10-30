import { afterEach,expect,it,vi } from 'vitest'
import * as Vue from 'vue'
import {createRenderer,nextTick,ssrContextKey} from 'vue'
import {compile} from '@vue/compiler-dom'
import {compileScript,parse} from '@vue/compiler-sfc'
import ts from 'typescript'
import {createPinia,setActivePinia} from 'pinia'
import {createMemoryHistory,createRouter} from 'vue-router'
import {useAuthStore} from '../../stores/auth'
import ListView from './PersonnelListView.vue'
import listSource from './PersonnelListView.vue?raw'
import DetailView from './PersonnelDetailView.vue'
import detailSource from './PersonnelDetailView.vue?raw'
import WorkbenchView from './WorkbenchView.vue'
import workbenchSource from './WorkbenchView.vue?raw'
const api=vi.hoisted(()=>({people:vi.fn(),history:vi.fn(),workbench:vi.fn()}))
vi.mock('../../api/account',async original=>({...await original<object>(),fetchPersonnel:api.people,fetchPersonnelHistory:api.history}))
vi.mock('../../api/workbench',()=>({fetchWorkbench:api.workbench}))
vi.mock('../../components/account/PersonnelCard.vue',()=>({default:{render:()=>null}}))
interface Surface {tag:string;text:string;value:unknown;props:Record<string,unknown>;children:Surface[];parent:Surface|null;tagName:string;multiple:boolean;selectedIndex:number;options:Surface[];addEventListener:()=>void;getRootNode:()=>object}
function node(tag:string,text=''):Surface {const n={tag,text,value:'',props:{},children:[],parent:null,tagName:tag.toUpperCase(),multiple:false,selectedIndex:0,addEventListener(){},getRootNode:()=>({})} as unknown as Surface;Object.defineProperty(n,'options',{get:()=>all(n).filter(c=>c.tag==='option')});return n}
const all=(root:Surface):Surface[]=>[root,...root.children.flatMap(all)]
const text=(n:Surface):string=>n.text+n.children.map(text).join('')
const renderer=createRenderer<Surface,Surface>({createElement:tag=>node(tag),createText:t=>node('#text',t),createComment:t=>node('#comment',t),insert(child,parent,anchor=null){if(child.parent)child.parent.children.splice(child.parent.children.indexOf(child),1);child.parent=parent;const index=anchor?parent.children.indexOf(anchor):-1;if(index<0)parent.children.push(child);else parent.children.splice(index,0,child)},remove(child){if(child.parent)child.parent.children.splice(child.parent.children.indexOf(child),1)},setText(n,t){n.text=t},setElementText(n,t){n.text=t;n.children=[]},patchProp(n,key,_old,v){n.props[key]=v;if(key==='value')n.value=v},parentNode:n=>n.parent,nextSibling:n=>n.parent?.children[n.parent.children.indexOf(n)+1]??null,insertStaticContent(t,parent){const n=node('#static',t);n.parent=parent;parent.children.push(n);return[n,n]}})
function client(view:object,source:string){const d=parse(source).descriptor;return{...view,render:new Function('Vue',ts.transpileModule(compile(d.template!.content,{mode:'function',prefixIdentifiers:true,bindingMetadata:compileScript(d,{id:'personnel-page-test'}).bindings}).code,{compilerOptions:{target:ts.ScriptTarget.ES2022}}).outputText)(Vue)}}
const apps:Vue.App[]=[]
afterEach(()=>{for(const app of apps.splice(0))app.unmount();vi.unstubAllGlobals();vi.clearAllMocks()})
async function settle(){for(let i=0;i<15;i++){await Promise.resolve();await nextTick()}}
async function mount(view:object,source:string,path:string,unqualified=false){vi.stubGlobal('Document',class{});vi.stubGlobal('ShadowRoot',class{});const pinia=createPinia();setActivePinia(pinia);useAuthStore().setConfirmedUser({id:'1',loginName:'synthetic',name:'合成',phone:'13800000000',role:unqualified?'TECHNICIAN':'ADMIN',enabled:true,reviewStatus:unqualified?'PENDING':null,version:0,capabilities:{canBrowseCampus:true,canCreateRequest:false,canWorkOnTasks:false,canManage:!unqualified}});const component=client(view,source),router=createRouter({history:createMemoryHistory(),routes:[{path:'/:pathMatch(.*)*',component}]});await router.push(path);await router.isReady();const root=node('root'),app=renderer.createApp(component).use(pinia).use(router);app.provide(ssrContextKey,{modules:new Set()});apps.push(app);app.mount(root);await settle();return{root,router}}
const success=(data:unknown)=>({kind:'SUCCESS',data,requestId:'mounted-page-test'})
it('list_pagination_keeps_applied_filters_until_explicit_query',async()=>{
  api.people.mockResolvedValue(success({records:[],page:1,pageSize:20,listTotal:45,allPeopleTotal:45,categoryCounts:{},pendingReviewCount:0,queriedAt:'2026-10-25T00:00:00.000Z'}))
  const {root,router}=await mount(ListView,listSource,'/admin/personnel?keyword=已应用&page=1')
  const input=all(root).find(n=>n.tag==='input')!;(input.props['onUpdate:modelValue'] as (v:string)=>void)('尚未提交')
  await (all(root).find(n=>n.tag==='button'&&text(n)==='下一页')!.props.onClick as ()=>unknown)();await settle()
  expect(router.currentRoute.value.query.keyword).toBe('已应用');expect(router.currentRoute.value.query.page).toBe('2')
})
it('detail_pagination_keeps_applied_business_filters',async()=>{
  api.history.mockResolvedValue(success({person:{id:'12',allowedActions:[]},availableCategories:['REQUESTS','RELATED_ORDERS'],categoryTotals:{REQUESTS:45,RELATED_ORDERS:0},statusCounts:{PENDING_REVIEW:45},records:{records:[],total:45,page:1,pageSize:20},queriedAt:'2026-10-25T00:00:00.000Z'}))
  const {root,router}=await mount(DetailView,detailSource,'/admin/personnel/12?category=REQUESTS&keyword=已应用&page=1')
  const input=all(root).find(n=>n.tag==='input')!;(input.props['onUpdate:modelValue'] as (v:string)=>void)('尚未提交')
  await (all(root).find(n=>n.tag==='button'&&text(n)==='下一页')!.props.onClick as ()=>unknown)();await settle()
  expect(router.currentRoute.value.query.keyword).toBe('已应用');expect(router.currentRoute.value.query.page).toBe('2')
})
it('unqualified_workbench_page_has_review_entry_without_task_cards_or_requests',async()=>{
  api.workbench.mockResolvedValue(success({counts:{},items:[],queriedAt:'2026-10-25T00:00:00.000Z'}))
  const {root}=await mount(WorkbenchView,workbenchSource,'/list',true)
  expect(text(root)).not.toContain('待接单');expect(text(root)).toContain('审核状态');expect(api.workbench).not.toHaveBeenCalled()
})
