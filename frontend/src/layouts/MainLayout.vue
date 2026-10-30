<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref, shallowRef, useTemplateRef, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '../stores/auth'
import { useReviewNotifications } from '../composables/useReviewNotifications'
const auth = useAuthStore(), router = useRouter(), route = useRoute()
const busy = ref(false), message = ref('')
const header = useTemplateRef<HTMLElement>('header'), sidebar = useTemplateRef<HTMLElement>('sidebar')
const headerHeight = shallowRef(84), sidebarHeight = shallowRef(0)
const shellStyle = computed(() => ({ '--header-height': `${headerHeight.value}px`, '--sidebar-height': `${sidebarHeight.value}px` }))
const { count: reviewCount, stale: reviewStale, loading: reviewLoading, message: reviewMessage,
  badgeVisible: reviewBadgeVisible } = useReviewNotifications(() => route.fullPath)
const reviewStatusText = computed(() => {
  if (!auth.businessRouteGranted || auth.currentUser?.role !== 'ADMIN') return ''
  if (reviewStale.value) return '数量已过期'
  if (reviewCount.value === null) return reviewLoading.value ? '数量核对中' : '数量暂未知'
  return ''
})
const reviewBadgeLabel = computed(() => `${reviewCount.value}项待审核申请${reviewStale.value ? '，数量已过期' : ''}`)
const reviewTitle = computed(() => `${reviewCount.value === null ? '待审核数量暂未知' : reviewBadgeLabel.value}${reviewMessage.value ? `：${reviewMessage.value}` : ''}`)
const personnel = useReviewNotifications(() => route.fullPath, 'personnel')
const personnelStatusText = computed(() => {
  if (!auth.businessRouteGranted || auth.currentUser?.role !== 'ADMIN') return ''
  if (personnel.stale.value) return '数量已过期'
  return personnel.count.value === null ? personnel.loading.value ? '数量核对中' : '数量暂未知' : ''
})
const personnelBadgeLabel = computed(() => `${personnel.count.value}名待审核维修人员${personnel.stale.value ? '，数量已过期' : ''}`)
const personnelTitle = computed(() => `${personnel.count.value === null ? '待审核人员数量暂未知' : personnelBadgeLabel.value}${personnel.message.value ? `：${personnel.message.value}` : ''}`)
const tasks = useReviewNotifications(() => route.fullPath, 'tasks')
const orders = useReviewNotifications(() => route.fullPath, 'orders')
const managementOrders = useReviewNotifications(() => route.fullPath, 'management')
function workOrderStatus(notification: ReturnType<typeof useReviewNotifications>) {
  return notification.stale.value ? '数量已过期' : notification.count.value === null ? notification.loading.value ? '数量核对中' : '数量暂未知' : ''
}
function workOrderLabel(notification: ReturnType<typeof useReviewNotifications>, label: string) {
  return `${notification.count.value ?? '未知'}项${label}${notification.stale.value ? '，数量已过期' : ''}`
}
const roleName = computed(() => ({ REPORTER: '报修用户', TECHNICIAN: '维修人员', ADMIN: '管理员' })[auth.currentUser?.role ?? 'REPORTER'])
let shellObserver: ResizeObserver | null = null
function measureShell() {
  if (header.value) headerHeight.value = Math.ceil(header.value.getBoundingClientRect().height)
  if (sidebar.value) sidebarHeight.value = Math.ceil(sidebar.value.getBoundingClientRect().height)
}
onMounted(() => {
  measureShell()
  if (typeof ResizeObserver !== 'undefined') {
    shellObserver = new ResizeObserver(measureShell)
    if (header.value) shellObserver.observe(header.value)
    if (sidebar.value) shellObserver.observe(sidebar.value)
  }
  window.addEventListener('resize', measureShell)
})
onUnmounted(() => { shellObserver?.disconnect(); window.removeEventListener('resize', measureShell) })
watch(() => auth.recovery, state => { if (state === 'ANONYMOUS' && !route.meta.public) void router.replace('/login') })
async function retry() { if (busy.value) return; busy.value = true; try { if (await auth.restore()) await router.replace('/campus') } finally { busy.value = false } }
async function logout() {
  if (busy.value) return; busy.value = true; message.value = ''
  try { const r = await auth.logout(); if (r.kind === 'SUCCESS') await router.replace('/login'); else message.value = r.kind === 'HTTP_ERROR' ? r.response.message : r.message }
  finally { busy.value = false }
}
</script>
<template>
  <div class="app-shell" :style="shellStyle">
    <a class="skip" href="#main">跳到主要内容</a>
    <header ref="header" class="app-header">
      <RouterLink class="app-brand" to="/campus"><span class="brand-mark" aria-hidden="true">维</span><span><strong>校园设备维护</strong><small>本机 · 示意校园</small></span></RouterLink>
      <div class="identity"><RouterLink to="/account/settings">账号设置</RouterLink><span v-if="auth.currentUser">{{ auth.currentUser.name }} · {{ roleName }}</span><button class="secondary" type="button" :disabled="busy" @click="logout">{{ busy ? '处理中…' : '退出登录' }}</button></div>
    </header>
    <div class="workspace">
      <aside ref="sidebar" class="sidebar">
        <p class="nav-label">工作入口</p>
        <nav aria-label="角色导航">
          <RouterLink to="/campus">校园总览</RouterLink>
          <RouterLink v-if="auth.currentUser?.role !== 'TECHNICIAN' || auth.currentUser.capabilities.canWorkOnTasks" to="/list">普通业务入口</RouterLink>
          <RouterLink v-if="auth.currentUser?.capabilities.canCreateRequest" to="/requests/new">新建报修</RouterLink>
          <RouterLink v-if="auth.currentUser?.capabilities.canCreateRequest" to="/requests">我的申请</RouterLink>
          <RouterLink v-if="auth.currentUser?.role === 'TECHNICIAN'" to="/account/review">审核状态</RouterLink>
          <RouterLink v-if="auth.currentUser?.capabilities.canWorkOnTasks" class="review-nav" to="/tasks" :title="workOrderLabel(tasks, '待处理工单')">
            <span>我的任务</span>
            <span v-if="tasks.badgeVisible.value" class="review-badge work-order-badge" :class="{ stale: tasks.stale.value }" role="status" :aria-label="workOrderLabel(tasks, '待处理工单')">{{ tasks.count.value }}</span>
            <span v-if="workOrderStatus(tasks)" class="review-status" role="status">{{ workOrderStatus(tasks) }}</span>
          </RouterLink>
          <RouterLink v-if="auth.currentUser?.role === 'REPORTER'" class="review-nav" to="/work-orders" :title="workOrderLabel(orders, '待验收工单')">
            <span>我的工单</span>
            <span v-if="orders.badgeVisible.value" class="review-badge work-order-badge" :class="{ stale: orders.stale.value }" role="status" :aria-label="workOrderLabel(orders, '待验收工单')">{{ orders.count.value }}</span>
            <span v-if="workOrderStatus(orders)" class="review-status" role="status">{{ workOrderStatus(orders) }}</span>
          </RouterLink>
          <RouterLink v-if="auth.currentUser?.capabilities.canManage" class="review-nav" to="/admin/work-orders" :title="workOrderLabel(managementOrders, '未完成工单')">
            <span>工单管理</span>
            <span v-if="managementOrders.badgeVisible.value" class="review-badge management-order-badge" :class="{ stale: managementOrders.stale.value }" role="status" :aria-label="workOrderLabel(managementOrders, '未完成工单')">{{ managementOrders.count.value }}</span>
            <span v-if="workOrderStatus(managementOrders)" class="review-status" role="status">{{ workOrderStatus(managementOrders) }}</span>
          </RouterLink>
          <RouterLink v-if="auth.currentUser?.capabilities.canManage" to="/equipment">设备台账</RouterLink>
          <RouterLink v-if="auth.currentUser?.capabilities.canManage" class="review-nav" to="/admin/requests" :title="reviewTitle">
            <span>申请审核与首次派单</span>
            <span v-if="reviewBadgeVisible" class="review-badge" :class="{ stale: reviewStale }" role="status" :aria-label="reviewBadgeLabel">{{ reviewCount }}</span>
            <span v-if="reviewStatusText" class="review-status" role="status">{{ reviewStatusText }}</span>
          </RouterLink>
          <RouterLink v-if="auth.currentUser?.capabilities.canManage" class="review-nav" to="/admin/personnel" :title="personnelTitle">
            <span>人员管理</span>
            <span v-if="personnel.badgeVisible.value" class="review-badge personnel-badge" :class="{ stale: personnel.stale.value }" role="status" :aria-label="personnelBadgeLabel">{{ personnel.count.value }}</span>
            <span v-if="personnelStatusText" class="review-status" role="status">{{ personnelStatusText }}</span>
          </RouterLink>
          <RouterLink v-if="auth.currentUser?.capabilities.canManage" to="/admin/statistics">统计看板</RouterLink>
        </nav>
        <p class="side-note">空间身份来自真实目录。按楼栋、楼层选择观察，报修与普通页面共用同次登录草稿。</p>
      </aside>
      <main id="main" class="main" tabindex="-1">
        <p class="breadcrumb">{{ roleName }} <span>/</span> {{ route.meta.entryLabel }}</p>
        <p v-if="message" class="notice" role="alert">{{ message }} <button type="button" class="text-button" @click="retry">核对会话</button></p>
        <p v-if="route.query.denied" class="notice" role="alert">当前身份没有该入口的权限，可继续浏览校园或查看本人审核。</p>
        <RouterView v-if="auth.businessRouteGranted" :key="auth.identityEpoch" />
        <section v-else class="card"><h1>{{ auth.recovery === 'RESTORING' ? '正在核实会话…' : '会话恢复未完成' }}</h1><p class="muted">{{ auth.recoveryError || '确认后再进入业务页面。' }}</p><button class="primary" :disabled="busy || auth.recovery === 'RESTORING'" @click="retry">重新核对会话</button></section>
        <footer>示意数据与布局 · 账号和业务以本机数据库为准</footer>
      </main>
    </div>
  </div>
</template>

