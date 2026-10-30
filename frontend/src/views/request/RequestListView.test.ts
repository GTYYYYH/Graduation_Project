import { afterEach, expect, it, vi } from 'vitest'
import * as Vue from 'vue'
import { createRenderer, h, nextTick, ssrContextKey } from 'vue'
import { compile } from '@vue/compiler-dom'
import { compileScript, parse } from '@vue/compiler-sfc'
import ts from 'typescript'
import { createPinia, setActivePinia } from 'pinia'
import { createMemoryHistory, createRouter, RouterView } from 'vue-router'
import View from './RequestListView.vue'
import source from './RequestListView.vue?raw'
import { useAuthStore } from '../../stores/auth'

const form=vi.hoisted(()=>({save:()=>{}}))
vi.mock('./RequestForm.vue',()=>({default:{setup(_props:unknown,{emit}:{emit:(name:string,value:unknown)=>void}){form.save=()=>emit('saved',{resourceId:'42'});return()=>h('form')}}}))
const descriptor=parse(source).descriptor
const renderCode=compile(descriptor.template!.content,{mode:'function',prefixIdentifiers:true,bindingMetadata:compileScript(descriptor,{id:'request-list-navigation'}).bindings}).code
const component={...View,render:new Function('Vue',ts.transpileModule(renderCode,{compilerOptions:{target:ts.ScriptTarget.ES2022}}).outputText)(Vue)}
interface Node {tag:string;text:string;props:Record<string,unknown>;children:Node[];parent:Node|null}
const node=(tag:string,text=''):Node=>({tag,text,props:{},children:[],parent:null})
const renderer=createRenderer<Node,Node>({
 createElement:tag=>node(tag),createText:text=>node('#text',text),createComment:text=>node('#comment',text),
 insert(child,parent,anchor=null){if(child.parent)child.parent.children.splice(child.parent.children.indexOf(child),1);child.parent=parent;const i=anchor?parent.children.indexOf(anchor):-1;if(i<0)parent.children.push(child);else parent.children.splice(i,0,child)},
 remove(child){if(child.parent)child.parent.children.splice(child.parent.children.indexOf(child),1)},setText(child,text){child.text=text},setElementText(child,text){child.text=text;child.children=[]},patchProp(child,key,_old,value){child.props[key]=value},parentNode:child=>child.parent,nextSibling:child=>child.parent?.children[child.parent.children.indexOf(child)+1]??null,
 insertStaticContent(text,parent){const child=node('#static',text);child.parent=parent;parent.children.push(child);return[child,child]},
})
const apps:Vue.App[]=[]
afterEach(()=>apps.splice(0).forEach(app=>app.unmount()))
const all=(root:Node):Node[]=>[root,...root.children.flatMap(all)]
const context='/campus?buildingId=11&floorId=12&locationId=13&observation=INTERIOR&itemsLocationId=13&page=2'
it.each([[context,context],['https://outside.example/','/requests'],['/campus?returnTo=/requests','/requests']])('ordinary new request preserves only controlled return %s',async(returnTo,expected)=>{
 const pinia=createPinia();setActivePinia(pinia)
 useAuthStore().setConfirmedUser({id:'10',loginName:'synthetic',name:'测试',phone:'13800000000',role:'REPORTER',enabled:true,reviewStatus:null,version:0,capabilities:{canBrowseCampus:true,canCreateRequest:true,canWorkOnTasks:false,canManage:false}})
 const router=createRouter({history:createMemoryHistory(),routes:[{path:'/requests/new',component},{path:'/requests/:id',component:{render:()=>h('p')}},{path:'/campus',component:{render:()=>h('p')}},{path:'/requests',component:{render:()=>h('p')}}]})
 await router.push({path:'/requests/new',query:{locationId:'13',returnTo}});await router.isReady()
 const root=node('root'),app=renderer.createApp({render:()=>h(RouterView)}).use(pinia).use(router);apps.push(app);app.provide(ssrContextKey,{modules:new Set()});app.mount(root);await nextTick()
 expect(all(root).some(n=>n.tag==='a'&&n.props.href===expected)).toBe(true)
 form.save();await vi.waitFor(()=>expect(router.currentRoute.value.path).toBe('/requests/42'))
 expect(router.currentRoute.value.query.returnTo).toBe(expected)
})
