import { createRouter, createWebHistory } from 'vue-router'
import MainLayout from '../layouts/MainLayout.vue'
import { useAuthStore } from '../stores/auth'
import { clearSceneRetry } from '../campus/observation'
function discardSceneRetry() { try { clearSceneRetry(window.sessionStorage) } catch { /* 3D恢复记录不影响认证门禁。 */ } }
const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/login', component: () => import('../views/account/LoginView.vue'), meta: { public: true } },
    { path: '/register', component: () => import('../views/account/RegisterView.vue'), meta: { public: true } },
    { path: '/', component: MainLayout, children: [
      { path: '', redirect: '/campus' },
      { path: 'campus', component: () => import('../views/campus/CampusView.vue'), meta: { entryLabel: '校园总览' } },
      { path: 'requests', component: () => import('../views/request/RequestListView.vue'), meta: { role: 'REPORTER', entryLabel: '我的申请' } },
      { path: 'requests/new', component: () => import('../views/request/RequestListView.vue'), meta: { role: 'REPORTER', entryLabel: '新建报修' } },
      { path: 'requests/:id', component: () => import('../views/request/RequestDetailView.vue'), meta: { entryLabel: '申请详情' } },
      { path: 'requests/:id/supplement', component: () => import('../views/request/SupplementView.vue'), meta: { role: 'REPORTER', entryLabel: '补充原申请' } },
      { path: 'list', component: () => import('../views/account/WorkbenchView.vue'), meta: { entryLabel: '普通业务入口' } },
      { path: 'tasks', component: () => import('../views/workorder/WorkOrderListView.vue'), meta: { entryLabel: '我的任务', technicianApproved: true } },
      { path: 'work-orders', component: () => import('../views/workorder/WorkOrderListView.vue'), meta: { entryLabel: '工单查询' } },
      { path: 'admin/work-orders', component: () => import('../views/workorder/WorkOrderListView.vue'), meta: { role: 'ADMIN', entryLabel: '工单管理' } },
      { path: 'admin/statistics', component: () => import('../views/statistics/StatisticsView.vue'), meta: { role: 'ADMIN', entryLabel: '统计看板' } },
      { path: 'admin/statistics/details', component: () => import('../views/statistics/StatisticsDetailsView.vue'), meta: { role: 'ADMIN', entryLabel: '统计明细' } },
      { path: 'work-orders/:id', component: () => import('../views/workorder/WorkOrderDetailView.vue'), meta: { entryLabel: '工单详情' } },
      { path: 'admin/requests', component: () => import('../views/request/RequestReviewListView.vue'), meta: { role: 'ADMIN', entryLabel: '申请审核与首次派单' } },
      { path: 'admin/requests/:id', component: () => import('../views/request/RequestReviewView.vue'), meta: { role: 'ADMIN', entryLabel: '报修申请审核' } },
      { path: 'equipment', component: () => import('../views/equipment/EquipmentListView.vue'), meta: { role: 'ADMIN', entryLabel: '设备台账' } },
      { path: 'equipment/new', component: () => import('../views/equipment/EquipmentFormView.vue'), meta: { role: 'ADMIN', entryLabel: '补录设备' } },
      { path: 'equipment/:id', component: () => import('../views/equipment/EquipmentDetailView.vue'), meta: { role: 'ADMIN', entryLabel: '设备详情' } },
      { path: 'equipment/:id/edit', component: () => import('../views/equipment/EquipmentFormView.vue'), meta: { role: 'ADMIN', entryLabel: '维护设备' } },
      { path: 'account/settings', component: () => import('../views/account/AccountSettingsView.vue'), meta: { entryLabel: '账号设置' } },
      { path: 'account/review', component: () => import('../views/account/ReviewStatusView.vue'), meta: { role: 'TECHNICIAN', entryLabel: '审核状态' } },
      { path: 'admin/personnel', component: () => import('../views/account/PersonnelListView.vue'), meta: { role: 'ADMIN', entryLabel: '人员管理' } },
      { path: 'admin/personnel/:id', component: () => import('../views/account/PersonnelDetailView.vue'), meta: { role: 'ADMIN', entryLabel: '人员详情' } },
      { path: 'admin/personnel/:id/reset-password', component: () => import('../views/account/PasswordResetView.vue'), meta: { role: 'ADMIN', entryLabel: '随机重置密码' } },
      { path: 'admin/accounts/:id/registration-review', component: () => import('../views/account/RegistrationReviewView.vue'), meta: { role: 'ADMIN', entryLabel: '注册审核' } },
    ] },
    { path: '/:pathMatch(.*)*', redirect: '/campus' },
  ],
})
router.beforeEach(async to => {
  const auth = useAuthStore()
  if (to.path !== '/campus') discardSceneRetry()
  if (to.meta.public) return true
  if (auth.recovery !== 'CONFIRMED') await auth.restore()
  if (!auth.businessRouteGranted) discardSceneRetry()
  if (auth.recovery === 'ANONYMOUS') return '/login'
  if (!auth.businessRouteGranted) return true
  const user = auth.currentUser!
  if ((to.meta.role && user.role !== to.meta.role) || (to.meta.technicianApproved && !user.capabilities.canWorkOnTasks)) return { path: '/campus', query: { denied: '1' } }
  return true
})
export default router



