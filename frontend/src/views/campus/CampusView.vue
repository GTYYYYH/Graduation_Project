<script setup lang="ts">
import { computed, nextTick, onMounted, onUnmounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { fetchCatalog } from '../../api/account'
import type { CatalogVo } from '../../api/account'
import { useAuthStore } from '../../stores/auth'
import LocationSelector from '../../components/campus/LocationSelector.vue'
import BusinessPanel from '../../components/campus/BusinessPanel.vue'
import LocationSummaryMarkers from '../../components/campus/LocationSummaryMarkers.vue'
import LocationItemsPanel from '../../components/campus/LocationItemsPanel.vue'
import LocationDirectory from '../../components/campus/LocationDirectory.vue'
import { fetchCampusSummaries, fetchLocationItems, categoryNames } from '../../api/campus'
import { useCampusRefresh } from '../../composables/useCampusRefresh'
import { businessReturnTo, campusItemPath } from '../../router/businessNavigation'
import type { CampusRenderer } from '../../campus/CampusRenderer'
import type { CampusManifest, CampusTarget, RendererState } from '../../campus/types'
import { clearSceneRetry, isCurrentSceneRetry, makeSceneSessionScope, ObservationIntent, saveSceneRetry, takeSceneRetry, useEntryPermission } from '../../campus/observation'
import type { SavedSceneRetry } from '../../campus/observation'
import { withLoadTimeout } from '../../campus/LoadCoordinator'
const route = useRoute(), router = useRouter(), auth = useAuthStore()
const catalog = ref<CatalogVo | null>(null), loading = ref(false), message = ref(''), stale = ref(false)
const buildingId = ref(''), floorId = ref(''), locationId = ref(''), canvasHost = ref<HTMLElement | null>(null)
const sceneLoading = ref(false), sceneError = ref(''), reducedMotion = ref(window.matchMedia('(prefers-reduced-motion: reduce)').matches)
const sceneReady = ref(false)
const state = ref<RendererState>({ level: 'OVERVIEW', locationId: null, loading: false, error: '' })
const buildingLabels = ref<{ id: string; name: string; x: number; y: number }[]>([])
const labels = ref<{ id: string; name: string; fullName: string; x: number; y: number }[]>([])
const media = window.matchMedia('(prefers-reduced-motion: reduce)')
const observationIntent = new ObservationIntent()
let catalogGeneration = 0, sceneGeneration = 0, renderGeneration = 0, alive = true, lastLabels = 0
let renderer: CampusRenderer | null = null, manifestController: AbortController | null = null
let pendingReloadIntent: SavedSceneRetry | null = null, sceneModuleFailed = false
let desiredTarget: CampusTarget = { kind: 'OVERVIEW' }
const buildings = computed(() => catalog.value?.buildings ?? [])
const floors = computed(() => catalog.value?.floors.filter(f => f.buildingId === buildingId.value) ?? [])
const locations = computed(() => catalog.value?.locations.filter(l => l.floorId === floorId.value) ?? [])
const building = computed(() => buildings.value.find(b => b.id === buildingId.value))
const singleAreaBuilding = computed(() => ['ct1','ct2','ct3','gym'].includes(building.value?.code ?? ''))
const interiorMarkerPosition = ref({ x: 0, y: 140 })
const floor = computed(() => floors.value.find(f => f.id === floorId.value))
const location = computed(() => locations.value.find(l => l.id === locationId.value))
const sampleFloor = computed(() => !!building.value && !!floor.value)
const currentAddress = computed(() => [building.value?.name, floor.value?.name, location.value?.name].filter(Boolean).join(' · '))
const levelName = computed(() => ({ OVERVIEW: '校园外观', BUILDING: '建筑观察', FLOOR: '当前楼层剖开', DOOR: '门口定位', INTERIOR: '室内观察' })[state.value.level])
const canEnter = useEntryPermission(sceneReady, sceneError, state, locationId)
const itemsLocationId=ref(''), itemsPage=ref(1)
let routeRestored=false, navigationGeneration=0
const summaryRead=useCampusRefresh(() => catalog.value && route.path==='/campus' && floorId.value ? `floor/${floorId.value}` : null,
  signal=>fetchCampusSummaries({buildingId:buildingId.value,floorId:floorId.value},{signal}))
const campusSummaryRead=useCampusRefresh(() => catalog.value && route.path==='/campus' ? `campus/${catalog.value.configVersion}` : null,
  signal=>fetchCampusSummaries({},{signal}))
const buildingRepairCounts=computed(()=> {
  const data=campusSummaryRead.data.value, directory=catalog.value
  if (!data || !directory || data.configVersion!==directory.configVersion) return null
  const byFloor=new Map(directory.floors.map(f=>[f.id,f.buildingId]))
  const byLocation=new Map(directory.locations.map(l=>[l.id,byFloor.get(l.floorId)]))
  const active=new Map(directory.buildings.map(b=>[b.id,0]))
  for (const summary of data.locations) {
    const id=byLocation.get(summary.locationId)
    if (!id || !active.has(id)) return null
    active.set(id, active.get(id)! + summary.visibleTotal)
  }
  return Object.fromEntries(active)
})
function buildingCountLabel(id:string,name:string) {
  const count=buildingRepairCounts.value?.[id]
  return count===undefined ? `${name}，报修事项数待核对` : `${name}，未完结报修${count}项${campusSummaryRead.stale.value?'，数据可能过期':''}`
}
const itemsRead=useCampusRefresh(() => catalog.value && route.path==='/campus' && itemsLocationId.value ? `items/${itemsLocationId.value}/${itemsPage.value}` : null,
  signal=>fetchLocationItems(itemsLocationId.value,{page:itemsPage.value,pageSize:20},{signal}))
const projectedMarkers=computed(()=> {
  const summaries = summaryRead.data.value?.locations ?? []
  if (state.value.level === 'INTERIOR' && singleAreaBuilding.value && location.value?.type === 'AREA') {
    const summary = summaries.find(item=>item.locationId === locationId.value)
    return summary ? [{...summary,name:location.value.name,...interiorMarkerPosition.value}] : []
  }
  return summaries.flatMap(summary=> {
    const label=labels.value.find(label=>label.id===summary.locationId)
    return label?[{...summary,name:label.name,x:label.x,y:label.y}]:[]
  })
})
const plainLabels=computed(()=>labels.value.filter(label=>!projectedMarkers.value.some(marker=>marker.locationId===label.id)))
const currentSummaries=computed(()=>summaryRead.data.value?.locations ?? [])
const returnContext=computed(()=> {
  const query=new URLSearchParams()
  if(buildingId.value)query.set('buildingId',buildingId.value)
  if(floorId.value)query.set('floorId',floorId.value)
  if(locationId.value)query.set('locationId',locationId.value)
  query.set('observation',state.value.level)
  if(itemsLocationId.value) {query.set('itemsLocationId',itemsLocationId.value);query.set('page',String(itemsPage.value))}
  return `/campus?${query}`
})
function openItems(id:string) {navigationGeneration++;itemsPage.value=1;itemsLocationId.value=id}
function closeItems() {navigationGeneration++;itemsLocationId.value='';itemsPage.value=1}
async function openMarker(id:string) {
  openItems(id)
  const own=navigationGeneration, epoch=auth.identityEpoch, source=route.fullPath
  await nextTick()
  await itemsRead.load()
  const data=itemsRead.data.value
  if(!alive || own!==navigationGeneration || epoch!==auth.identityEpoch || route.fullPath!==source || route.path!=='/campus'
    || itemsLocationId.value!==id || document.visibilityState==='hidden' || itemsRead.loading.value || itemsRead.stale.value || itemsRead.message.value
    || !data || data.location.id!==id || data.visibleTotal!==1 || data.items.total!==1 || data.items.records.length!==1) return
  await router.push({path:campusItemPath(data.items.records[0]!,!!auth.currentUser?.capabilities.canManage),query:{returnTo:returnContext.value}})
}
async function refreshBusiness() {await Promise.all([summaryRead.load(),campusSummaryRead.load(),itemsRead.load()])}
function restoreRouteContext() {
  if(routeRestored || !catalog.value || !businessReturnTo(route.fullPath,''))return
  routeRestored=true
  const query=route.query
  if(typeof query.buildingId==='string' && buildings.value.some(b=>b.id===query.buildingId)) selectBuilding(query.buildingId)
  if(typeof query.floorId==='string' && floors.value.some(f=>f.id===query.floorId)) selectFloor(query.floorId)
  if(typeof query.locationId==='string' && locations.value.some(l=>l.id===query.locationId)) {
    selectLocation(query.locationId)
    if(query.observation==='INTERIOR')observationIntent.enter(query.locationId)
  } else if(query.observation==='FLOOR')selectFloor(floorId.value, false)
  else if(query.observation==='BUILDING')selectBuilding(buildingId.value)
  if(query.observation==='OVERVIEW')desiredTarget={kind:'OVERVIEW'}
  if(typeof query.itemsLocationId==='string' && locations.value.some(l=>l.id===query.itemsLocationId)) {
    itemsLocationId.value=query.itemsLocationId;itemsPage.value=typeof query.page==='string'?Number(query.page):1
  }
}
function stopScene() {
  sceneGeneration++; manifestController?.abort(); manifestController = null
  renderer?.dispose(); renderer = null; labels.value = []; buildingLabels.value=[]; sceneLoading.value = false; sceneReady.value = false
}
function projectLabels() {
  buildingLabels.value = renderer && ['OVERVIEW','BUILDING'].includes(state.value.level) ? buildings.value.flatMap(b=> {
    const p=renderer?.projectBuildingAnchor?.(b.id)
    return p?.visible ? [{id:b.id,name:b.name,x:p.x,y:p.y}]:[]
  }) : []
  if (singleAreaBuilding.value && state.value.level === 'INTERIOR' && canvasHost.value) {
    const x = canvasHost.value.clientWidth / 2, y = Math.min(140, canvasHost.value.clientHeight - 16)
    if (x !== interiorMarkerPosition.value.x || y !== interiorMarkerPosition.value.y) interiorMarkerPosition.value = {x,y}
  }
  if (!alive || !renderer || !sampleFloor.value || state.value.level === 'INTERIOR') { labels.value = []; return }
  if (performance.now() - lastLabels < 100) return
  lastLabels = performance.now()
  labels.value = locations.value.flatMap(l => {
    const p = renderer!.projectAnchor(l.id)
    const slot = renderer?.layoutSlot?.(l.id)
    const name = slot ? `房间${slot}\n${l.name}` : l.name
    return p?.visible ? [{ id: l.id, name, fullName: `${building.value?.name} · ${floor.value?.name} · ${l.name}`, x: p.x, y: p.y }] : []
  })
}
async function startScene() {
  if (!catalog.value || route.path !== '/campus' || !auth.businessRouteGranted) return
  const resume = observationIntent.capture(), sceneCatalog = catalog.value
  stopScene(); const own = sceneGeneration, epoch = auth.identityEpoch
  const current = () => alive && own === sceneGeneration && epoch === auth.identityEpoch && route.path === '/campus'
  sceneLoading.value = true; sceneError.value = ''; sceneModuleFailed = false; await nextTick()
  if (!current()) return
  const controller = new AbortController(); manifestController = controller
  let moduleFailed = false
  try {
    const instance = await withLoadTimeout(async () => {
      const [module, response] = await Promise.all([import('../../campus/CampusRenderer').catch(error => { moduleFailed = true; throw error }), fetch('/campus/manifest.json', { signal: controller.signal, cache: 'no-cache' })])
      if (!response.ok) throw new Error('渲染清单读取失败')
      const manifest = await response.json() as CampusManifest
      if (!current() || !canvasHost.value) return null
      const mounted = new module.CampusRenderer({ catalog: sceneCatalog, reducedMotion: () => reducedMotion.value,
      onState: next => { if (alive && own === sceneGeneration && epoch === auth.identityEpoch) { state.value = next; projectLabels() } },
      onSelect: target => { if (target.kind === 'LOCATION') selectLocation(target.locationId); else if (target.kind === 'BUILDING') selectBuilding(target.buildingId) },
      onEnter: id => { if (id === locationId.value) void enter() },
      onFrame: () => { if (own === sceneGeneration) projectLabels() }, onRestore: () => { if (alive && own === sceneGeneration) void load() } })
      renderer = mounted
      await mounted.mount(canvasHost.value, manifest)
      if (!current()) { mounted.dispose(); return null }
      return mounted
    }, () => {
      if (current()) { sceneLoading.value = false; sceneError.value = '3D准备已超过15秒，可重试或打开普通业务入口。加载完成后会恢复画面。' }
    })
    if (!instance || !current()) return
    sceneLoading.value = false; sceneError.value = ''; sceneReady.value = true
    await instance.selectTarget(desiredTarget, ++renderGeneration)
    if (current() && observationIntent.canResume(resume, locationId.value)) await instance.enterLocation(locationId.value, ++renderGeneration)
  } catch (error) {
    if (alive && own === sceneGeneration && epoch === auth.identityEpoch) {
      renderer?.dispose(); renderer = null; sceneLoading.value = false
      sceneModuleFailed = moduleFailed
      sceneError.value = moduleFailed ? '3D场景代码下载失败，重试将重新加载页面并保留当前位置。' : error instanceof Error ? error.message : '3D加载失败，请重试'
    }
  } finally { if (manifestController === controller) manifestController = null }
}
async function load() {
  if (loading.value || route.path !== '/campus') return
  const own = ++catalogGeneration, epoch = auth.identityEpoch; loading.value = true; message.value = ''
  const result = await fetchCatalog()
  if (!alive || own !== catalogGeneration || epoch !== auth.identityEpoch || route.path !== '/campus') return
  loading.value = false
  if (result.kind === 'SUCCESS') {
    catalog.value = result.data; stale.value = false
    if (!buildings.value.some(b => b.id === buildingId.value)) buildingId.value = buildings.value[0]?.id ?? ''
    if (!floors.value.some(f => f.id === floorId.value)) floorId.value = floors.value[0]?.id ?? ''
    if (!locations.value.some(l => l.id === locationId.value)) locationId.value = ''
    restoreRouteContext()
    await restoreReloadIntent(own, epoch)
    if (!alive || own !== catalogGeneration || epoch !== auth.identityEpoch || route.path !== '/campus') return
    if (buildings.value.length) void startScene()
  } else { discardReloadIntent(); stale.value = catalog.value !== null; message.value = result.kind === 'HTTP_ERROR' ? result.response.message : result.message }
}
function selectBuilding(id: string) {
  closeItems()
  observationIntent.select()
  buildingId.value = id; floorId.value = floors.value[0]?.id ?? ''; locationId.value = ''
  desiredTarget = { kind: 'BUILDING', buildingId: id }; const generation = ++renderGeneration
  void renderer?.selectTarget(desiredTarget, generation)
}
async function openBuildingFloor(id: string) {
  if (!sceneReady.value || !buildings.value.some(b=>b.id===id)) return
  if (id !== buildingId.value) selectBuilding(id)
  await cutCurrentFloor()
}
function selectFloor(id: string, locateArea = true) {
  closeItems()
  observationIntent.select()
  floorId.value = id; locationId.value = ''; desiredTarget = { kind: 'FLOOR', floorId: id }
  const generation = ++renderGeneration; void renderer?.selectTarget(desiredTarget, generation)
  if(locateArea && locations.value.length===1 && locations.value[0]?.type==='AREA')selectLocation(locations.value[0].id)
}
async function selectFloorFromSelector(id: string) {
  if (!floors.value.some(f => f.id === id)) return
  const autoEnter = singleAreaBuilding.value
  selectFloor(id, !autoEnter)
  if (autoEnter) await cutCurrentFloor()
}
async function selectLocation(id: string) {
  closeItems()
  if (!id) { locationId.value = ''; selectFloor(floorId.value); return }
  const selected = catalog.value?.locations.find(l => l.id === id)
  if (!selected) return
  const selectedFloor = catalog.value!.floors.find(f => f.id === selected.floorId)
  if (!selectedFloor) return
  observationIntent.select()
  buildingId.value = selectedFloor.buildingId; floorId.value = selectedFloor.id; locationId.value = id
  desiredTarget = { kind: 'LOCATION', locationId: id }; const generation = ++renderGeneration
  await renderer?.selectTarget(desiredTarget, generation)
}
async function cutCurrentFloor() {
  const area = locations.value.length === 1 && locations.value[0]?.type === 'AREA' ? locations.value[0] : null
  if (!area) { selectFloor(floorId.value, false); return }
  const expectedGeneration = renderGeneration + 1, epoch = auth.identityEpoch
  await selectLocation(area.id)
  if (renderGeneration === expectedGeneration && auth.identityEpoch === epoch && alive && route.path === '/campus' && locationId.value === area.id) await enter()
}
async function enter() { if (canEnter.value && renderer) { navigationGeneration++; observationIntent.enter(locationId.value); await renderer.enterLocation(locationId.value, ++renderGeneration) } }
function discardReloadIntent() { pendingReloadIntent = null; try { clearSceneRetry(window.sessionStorage) } catch { /* 可用入口不依赖浏览器存储。 */ } }
async function restoreReloadIntent(generation: number, epoch: number) {
  const retry = pendingReloadIntent; pendingReloadIntent = null
  if (!retry || retry.configVersion !== catalog.value?.configVersion) return
  const ownRender = renderGeneration
  let scope: string | null
  try { scope = await makeSceneSessionScope(auth.currentUser?.id, auth.expiresAt) } catch { return }
  if (!scope || !isCurrentSceneRetry(retry, scope) || !alive || ownRender !== renderGeneration || generation !== catalogGeneration || epoch !== auth.identityEpoch || route.path !== '/campus') return
  if (retry.kind === 'BUILDING') { const b = catalog.value.buildings.find(b => b.code === retry.code); if (b) selectBuilding(b.id) }
  else if (retry.kind === 'FLOOR') { const f = catalog.value.floors.find(f => f.code === retry.code); if (f) { selectBuilding(f.buildingId); selectFloor(f.id, false) } }
  else if (retry.kind === 'LOCATION') {
    const l = catalog.value.locations.find(l => l.code === retry.code)
    if (l) { selectLocation(l.id); if (retry.enter && locationId.value === l.id) observationIntent.enter(l.id) }
  }
}
async function retryScene() {
  const resume = observationIntent.capture()
  if (sceneModuleFailed || state.value.failure === 'MODULE') {
    if (!catalog.value?.configVersion) return
    const kind = desiredTarget.kind
    const code = kind === 'LOCATION' ? location.value?.code : kind === 'FLOOR' ? floor.value?.code : kind === 'BUILDING' ? building.value?.code : ''
    const ownRender = renderGeneration, ownScene = sceneGeneration, epoch = auth.identityEpoch
    try {
      const scope = await makeSceneSessionScope(auth.currentUser?.id, auth.expiresAt)
      if (!scope || !alive || ownRender !== renderGeneration || ownScene !== sceneGeneration || epoch !== auth.identityEpoch || route.path !== '/campus' || !auth.businessRouteGranted) return
      saveSceneRetry(window.sessionStorage, { configVersion: catalog.value.configVersion, kind, code: code ?? '', enter: observationIntent.canResume(resume, locationId.value) }, scope)
      stopScene(); window.location.reload()
    } catch { sceneError.value = '浏览器无法保留重试位置，请刷新页面后重新选择位置。普通业务入口仍可使用。' }
  } else if (renderer && sceneReady.value && state.value.error && state.value.failure === 'INTERIOR' && observationIntent.canResume(resume, locationId.value)) {
    await renderer.enterLocation(locationId.value, ++renderGeneration)
  } else await startScene()
}
function returnFloor() { navigationGeneration++; observationIntent.select(); renderGeneration++; locationId.value = ''; desiredTarget = { kind: 'FLOOR', floorId: floorId.value }; renderer?.returnToFloor() }
function reset() { navigationGeneration++; observationIntent.select(); renderGeneration++; desiredTarget = { kind: 'OVERVIEW' }; locationId.value = ''; renderer?.resetOverview() }
function mediaChanged(event: MediaQueryListEvent) { reducedMotion.value = event.matches; if (state.value.loading && desiredTarget.kind === 'LOCATION') void renderer?.selectTarget(desiredTarget, ++renderGeneration) }
watch(() => route.path, path => { discardReloadIntent(); observationIntent.select(); catalogGeneration++; loading.value = false; stopScene(); if (path === '/campus') void load() })
watch(() => auth.identityEpoch, () => { closeItems(); discardReloadIntent(); observationIntent.select(); catalogGeneration++; stopScene(); catalog.value = null })
onMounted(() => { try { pendingReloadIntent = takeSceneRetry(window.sessionStorage) } catch { pendingReloadIntent = null }; media.addEventListener('change', mediaChanged); void load() })
onUnmounted(() => { alive = false; discardReloadIntent(); observationIntent.select(); catalogGeneration++; stopScene(); media.removeEventListener('change', mediaChanged) })
</script>
<template>
  <section v-if="route.path === '/tasks'" class="card"><p class="eyebrow">维修资格已通过</p><h1>我的任务</h1><p class="muted">审核通过不会自动分派工单。工单及维修任务将在 B04—B05 接入；当前可浏览校园和查看审核历史。</p><RouterLink to="/account/review">查看审核记录</RouterLink></section>
  <section v-else-if="route.path === '/list'" class="card"><h1>普通业务入口</h1><p class="muted">业务页面可独立使用，报修与补充共用同次登录草稿。</p><div v-if="auth.currentUser?.capabilities.canManage" class="actions"><RouterLink to="/admin/requests">申请审核与首次派单</RouterLink><RouterLink to="/equipment">设备台账</RouterLink><RouterLink to="/work-orders">工单查询</RouterLink><RouterLink to="/admin/personnel">人员注册审核</RouterLink></div><div v-else-if="auth.currentUser?.role === 'TECHNICIAN'" class="actions"><RouterLink v-if="auth.currentUser.capabilities.canWorkOnTasks" to="/tasks">我的任务与历史</RouterLink><RouterLink to="/account/review">查看我的资格审核</RouterLink></div><div v-else class="actions"><RouterLink to="/requests/new">新建报修</RouterLink><RouterLink to="/requests">我的申请</RouterLink></div></section>
  <template v-else>
    <div class="page-heading"><div><p class="eyebrow">示意校园 · 10栋建筑</p><h1>校园总览</h1><p class="muted">真实目录连接示意空间。{{ singleAreaBuilding ? '选择楼层后直接进入室内观察。' : '选择只定位入口，主动开门进入观察。' }}</p></div><div class="heading-actions"><RouterLink class="secondary link-button" to="/list">普通业务入口</RouterLink><button class="secondary" :disabled="loading" @click="load">{{ loading ? '加载中…' : '刷新目录' }}</button></div></div>
    <p v-if="message" class="notice" role="alert">{{ message }} {{ stale ? '保留上次数据，当前可能已过期。' : '' }}</p>
    <div v-if="catalog" class="catalog-counts"><span><strong>{{ buildings.length }}</strong> 楼栋</span><span><strong>{{ catalog.floors.length }}</strong> 楼层</span><span><strong>{{ catalog.locations.length }}</strong> 观察位置</span><span><strong>{{ catalog.locations.filter(l => l.reportable).length }}</strong> 报修位置</span><small>配置版本 {{ catalog.configVersion || '未初始化' }}</small></div>
    <section class="card selectors-card" aria-label="空间目录选择">
      <p v-if="loading && !catalog" class="muted" role="status">正在读取目录…</p><p v-else-if="catalog && !buildings.length" class="muted">目录尚未初始化，请由项目维护者核对本地校园配置。</p>
      <LocationSelector v-else-if="catalog" :catalog="catalog" :building-id="buildingId" :floor-id="floorId" :location-id="locationId" @building="selectBuilding" @floor="selectFloorFromSelector" @location="selectLocation" />
    </section>
    <div class="campus-columns">
      <section class="card scene-card" aria-label="校园三维场景">
        <div class="scene-toolbar"><div><strong>{{ state.level === 'OVERVIEW' ? '10栋示意校园' : `${building?.name} · ${floor?.name}` }}</strong><span>{{ levelName }}</span></div><div class="scene-actions"><button class="secondary" :disabled="!sceneReady || !sampleFloor" @click="cutCurrentFloor">剖开当前层</button><button v-if="!singleAreaBuilding" class="secondary" :disabled="!sceneReady || !sampleFloor" @click="returnFloor">返回楼层</button><button class="secondary" :disabled="!sceneReady" @click="reset">回到校园总览</button></div></div>
        <p v-if="sceneLoading" class="scene-message" role="status">正在准备校园三维场景，位置选择与普通业务入口已可使用…</p>
        <div v-if="sceneError || state.error" class="notice" role="alert">{{ sceneError || state.error }} <button class="text-button" :disabled="sceneLoading" @click="retryScene">重试3D</button> <RouterLink to="/list">打开普通业务入口</RouterLink></div>
        <div ref="canvasHost" class="canvas-host"><div class="model-labels" aria-label="当前层入口标签"><button v-for="label in buildingLabels" :key="`building-${label.id}`" class="model-label building-label" :class="{'building-has-repairs':(buildingRepairCounts?.[label.id] ?? 0)>0}" :style="{left:`${label.x}px`,top:`${label.y}px`}" :aria-label="buildingCountLabel(label.id,label.name)" :title="buildingCountLabel(label.id,label.name)" @click="openBuildingFloor(label.id)"><span>{{ label.name }}</span><strong class="building-repair-count">{{ buildingRepairCounts?.[label.id] ?? '—' }}</strong></button><button v-for="label in plainLabels" :key="label.id" class="model-label" :style="{ left: `${label.x}px`, top: `${label.y}px` }" :title="label.fullName" :aria-label="`定位门口：${label.fullName}`" @click="selectLocation(label.id)">{{ label.name }}</button></div><LocationSummaryMarkers :markers="projectedMarkers" @open="openMarker" /><span v-if="sceneError" class="canvas-fallback">三维场景暂不可用，空间目录仍可浏览</span></div>
        <div class="scene-help"><p>鼠标拖动旋转 · 右键拖动平移 · 滚轮缩放<br>触控：单指旋转，双指平移与缩放；键盘使用位置选择、进入及返回按钮。</p><label class="motion-option"><input v-model="reducedMotion" type="checkbox">减少动态效果</label></div>
        <small>示意布局与尺寸，非学校实测资料。北侧教学楼、东侧体育馆、南侧食堂、西侧宿舍；内景按位置加载。</small>
      </section>
      <aside class="card observation-panel" aria-label="当前位置观察">
        <p class="eyebrow">当前位置</p><h2>{{ currentAddress || '选择楼栋与位置' }}</h2>
        <p class="muted">{{ state.loading ? '正在定位／准备内景…' : state.level === 'INTERIOR' ? '已进入，可旋转与缩放观察。' : location ? '已选择位置，开门由你主动触发。' : '从位置列表选择房间，或剖开当前层查看入口。' }}</p>
        <button class="primary full" :disabled="!canEnter" @click="enter">进入观察（开门）</button>
        <template v-if="location"><p v-if="!location.reportable" class="public-note">仅供观察，本区域不提供报修。</p><p v-else class="muted">范围内报修位置，业务填写独立于空间观察。</p>
          <p v-if="state.level === 'INTERIOR' && location.use === 'CLASSROOM'" class="facility-note">30桌30椅（5列×6排） · 讲台电脑 · 1空调 · 6灯 · 4插座；投影仪属于报修范围，无需对应模型。</p>
          <p v-if="state.level === 'INTERIOR' && location.use === 'OFFICE'" class="facility-note">4套桌椅 · 1空调 · 2灯 · 4插座。</p>
          <p v-if="state.level === 'INTERIOR' && location.use === 'CANTEEN_AREA'" class="facility-note">12张四人桌 · 48椅 · 4窗口 · 4立式空调 · 12灯。厨房仓储封闭，不可进入；报修按楼层空调组或照明组，无需单灯号或角落。</p>
          <p v-if="state.level === 'INTERIOR' && location.use.startsWith('GYM_')" class="facility-note">体育馆仅两层，中央挑空，两端楼梯；1层2篮球架／12灯／4空调，二层32席／6灯／2空调。顶部灯归1层，座椅不报修。</p>
          <p v-if="state.level === 'INTERIOR' && location.use === 'DORM_ROOM'" class="facility-note">左右各一组上床下桌 · 独立卫浴 · 空调 · 宿舍与卫浴灯 · 两桌位插座</p>
          <p v-if="state.level === 'INTERIOR' && location.use === 'DORM_DUTY'" class="facility-note">宿管值班室：1桌1椅，仅供观察。</p>
          <p v-if="state.level === 'INTERIOR' && location.use === 'DORM_LOBBY'" class="facility-note">门厅：服务导览与简化公告板，仅供观察。</p>
          <BusinessPanel v-if="catalog && location.canCreateRequest" :location-id="location.id" :catalog="catalog" :return-to="returnContext" />
        </template><p class="scope-note">观察不会触发任何申请、派单或维修操作。</p>
        <section aria-label="当前楼层业务提示"><h2>当前有权事项</h2><p class="summary-legend"><span class="legend campus-location-status FAULT_PROCESSING">故障处理中</span><span class="legend campus-location-status PENDING_ACCEPTANCE">待验收</span><span class="legend campus-location-status NEEDS_SUPPLEMENT">退回待补充</span><span class="legend campus-location-status PENDING_REVIEW">待审核</span></p>
        <button type="button" class="secondary" :disabled="summaryRead.loading.value" @click="refreshBusiness">刷新空间事项</button>
        <p v-if="summaryRead.loading.value && !summaryRead.data.value" role="status">正在读取空间事项…</p>
        <p v-if="summaryRead.message.value" class="notice" role="alert">{{ summaryRead.message.value }} {{ summaryRead.stale.value ? `资料可能过期，上次成功 ${summaryRead.lastSuccessAt.value}。` : '' }} <button type="button" class="text-button" @click="refreshBusiness">重试空间读取</button></p>
        <template v-if="summaryRead.data.value"><p v-if="!currentSummaries.length" class="muted">当前范围没有可查看的未完结记录</p><ul class="summary-directory"><li v-for="summary in currentSummaries" :key="summary.locationId"><button type="button" class="secondary full" @click="openItems(summary.locationId)">{{ locations.find(l=>l.id===summary.locationId)?.name }} · {{ categoryNames[summary.category] }} · {{ summary.visibleTotal }}项</button></li></ul><small>上次成功 {{ summaryRead.lastSuccessAt.value }}{{ summaryRead.stale.value ? ' · 可能过期' : '' }}</small></template>
        </section>
        <LocationItemsPanel v-if="itemsLocationId" :data="itemsRead.data.value" :loading="itemsRead.loading.value" :message="itemsRead.message.value" :stale="itemsRead.stale.value" :last-success-at="itemsRead.lastSuccessAt.value" :return-to="returnContext" :page="itemsPage" :manage-requests="!!auth.currentUser?.capabilities.canManage" @retry="itemsRead.load" @page="itemsPage=$event" @close="closeItems" />
      </aside>
    </div>
    <LocationDirectory v-if="catalog" :title="`${building?.name} · ${floor?.name}`" :sample-floor="sampleFloor" :locations="locations" :summaries="currentSummaries" :selected-id="locationId" @select="selectLocation" @open="openMarker" />
  </template>
</template>
<style scoped>
.summary-legend{display:flex;gap:8px;flex-wrap:wrap}.legend{padding:5px 8px;border-radius:4px;font-size:12px;background:var(--campus-status-background);color:var(--campus-status-text)}.summary-directory{list-style:none;padding:0;display:grid;gap:8px}.heading-actions { display: flex; gap: 10px; flex-wrap: wrap; }.heading-actions .link-button { margin: 0; }
.catalog-counts { display: flex; gap: 18px; flex-wrap: wrap; align-items: center; margin: 0 0 20px; font-size: 13px; color: #617b85; }.catalog-counts strong { color: #155e75; font-size: 18px; }.catalog-counts small { margin-left: auto; }
.selectors-card { padding: 20px; }.campus-columns { display: grid; grid-template-columns: minmax(0,1fr); gap: 18px; align-items: start; }.scene-card { padding: 16px; }.scene-toolbar { display: flex; flex-wrap: wrap; justify-content: space-between; gap: 12px; align-items: center; margin-bottom: 14px; }.scene-toolbar strong { font-size: 14px; }.scene-toolbar span { display: block; color: #718891; font-size: 12px; margin-top: 5px; }.scene-actions { display: flex; gap: 6px; flex-wrap: wrap; }.scene-actions button { padding: 10px; font-size: 12px; }
.canvas-host { position: relative; min-width: 0; height: 460px; overflow: hidden; border-radius: 8px; background: #e8eef0; }.model-labels { position: absolute; inset: 0; pointer-events: none; }.model-label { white-space: pre-line; position: absolute; transform: translate(-50%,-80%); border: 1px solid #b6c6cd; color: #254856; background: #ffffffed; min-width: 44px; min-height: 44px; border-radius: 5px; font-size: 12px; cursor: pointer; pointer-events: auto; box-shadow: 0 2px 7px #203c4b14; padding: 6px; }.model-label:hover { background: #e8f4f8; }.canvas-fallback { display: grid; height: 100%; place-items: center; padding: 24px; text-align: center; color: #617b85; font-size: 14px; }
.scene-help { display: flex; flex-wrap: wrap; justify-content: space-between; align-items: center; gap: 10px; }.scene-help p { font-size: 12px; color: #617b85; margin: 14px 0 10px; }.motion-option { display: flex; align-items: center; gap: 8px; min-height: 44px; font-size: 12px; cursor: pointer; }.motion-option input { width: 18px; min-height: 18px; margin: 0; accent-color: #155e75; }.observation-panel { padding: 22px 18px; }.observation-panel h2 { margin: 12px 0; line-height: 1.65; }.observation-panel p { font-size: 13px; }.public-note { padding: 12px; border-radius: 6px; background: #edf2f4; color: #4d6671; }.facility-note { border-top: 1px solid #dae4e8; padding-top: 14px; color: #4d6671; }.scope-note { margin: 24px 0 0; color: #718891; }
.location-heading { display: flex; flex-wrap: wrap; justify-content: space-between; align-items: center; gap: 6px; margin-bottom: 14px; }.location-heading strong { margin: 0; }.location-grid { margin-top: 12px; grid-template-columns: repeat(auto-fill,minmax(160px,1fr)); }.location-grid li.selected { border-color: #49869b; background: #f2f8fa; }.scene-message { font-size: 13px; color: #617b85; }button:focus-visible, a:focus-visible, select:focus-visible, input:focus-visible { outline: 3px solid #478aa6; outline-offset: 3px; }
@media(max-width:1250px) { .canvas-host { height: 440px; } }
@media(max-width:767px) { .catalog-counts { gap: 10px 14px; }.catalog-counts small { margin-left: 0; width: 100%; }.observation-panel { display: block; }.observation-panel p { margin: 12px 0; }.observation-panel h2 { margin: 10px 0; }.canvas-host { height: 340px; }.scene-card { padding: 12px; }.scene-actions { width: 100%; }.scene-actions button { flex: 1; }.location-grid { grid-template-columns: 1fr; }.scene-toolbar strong { font-size: 13px; } }
.building-label { display:flex;align-items:center;justify-content:center;gap:7px;white-space:nowrap; }
.building-label.building-has-repairs { color:#9f2020;border-color:#c74242;background:#fff1f0; }
.building-label.building-has-repairs:hover { background:#ffe3e0; }
.building-repair-count { font-variant-numeric:tabular-nums;font-weight:700; }
</style>
