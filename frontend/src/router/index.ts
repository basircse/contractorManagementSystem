import { createRouter, createWebHistory } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/login', name: 'login', component: () => import('@/views/LoginView.vue'), meta: { public: true } },
    {
      path: '/',
      component: () => import('@/layouts/AppLayout.vue'),
      children: [
        { path: '', redirect: '/app/dashboard' },
        { path: 'admin/contractors', name: 'contractors', component: () => import('@/views/admin/ContractorsView.vue'), meta: { admin: true } },
        { path: 'app/dashboard', name: 'dashboard', component: () => import('@/views/app/DashboardView.vue') },
        { path: 'app/projects', name: 'projects', component: () => import('@/views/app/ProjectsView.vue') },
        { path: 'app/work-items', name: 'workItems', component: () => import('@/views/app/WorkItemsView.vue') },
        { path: 'app/labours', name: 'labours', component: () => import('@/views/app/LaboursView.vue') },
        { path: 'app/rate-cards', name: 'rateCards', component: () => import('@/views/app/RateCardsView.vue') },
        { path: 'app/attendance', name: 'attendance', component: () => import('@/views/app/AttendanceView.vue') },
        { path: 'app/payments', name: 'payments', component: () => import('@/views/app/PaymentsView.vue') },
        { path: 'app/reports/items', name: 'itemReport', component: () => import('@/views/app/ItemCostReportView.vue') },
        { path: 'app/reports/labour/:id?', name: 'labourReport', component: () => import('@/views/app/LabourHistoryView.vue') },
      ],
    },
    { path: '/:pathMatch(.*)*', redirect: '/' },
  ],
})

router.beforeEach(async (to) => {
  const auth = useAuthStore()
  if (to.meta.public) {
    return auth.isLoggedIn && to.name === 'login' ? '/' : true
  }
  if (!auth.isLoggedIn) return { name: 'login' }
  if (!auth.user) {
    try {
      await auth.fetchMe()
    } catch {
      auth.logout()
      return { name: 'login' }
    }
  }
  if (auth.isAdmin) {
    // Admin sees contractor pages only while "viewing" one of them.
    if (!to.meta.admin && !auth.viewing) return { name: 'contractors' }
  } else if (to.meta.admin) {
    return { name: 'dashboard' }
  }
  return true
})

export default router
