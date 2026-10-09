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
        { path: 'app/reports/profit', name: 'profit', component: () => import('@/views/app/ProfitView.vue') },
        { path: 'app/quotations', name: 'quotations', component: () => import('@/views/app/income/QuotationsView.vue') },
        { path: 'app/work-orders', name: 'workOrders', component: () => import('@/views/app/income/WorkOrdersView.vue') },
        { path: 'app/work-orders/:id', name: 'workOrder', component: () => import('@/views/app/income/WorkOrderDetailView.vue') },
        { path: 'app/bills', name: 'bills', component: () => import('@/views/app/income/BillsView.vue') },
        { path: 'app/receipts', name: 'receipts', component: () => import('@/views/app/income/ReceiptsView.vue') },
        { path: 'app/print/:kind(quotation|bill)/:id', name: 'print', component: () => import('@/views/app/income/DocumentView.vue') },
        { path: 'app/parties', name: 'parties', component: () => import('@/views/app/expense/PartiesView.vue') },
        { path: 'app/purchases', name: 'purchases', component: () => import('@/views/app/expense/PurchasesView.vue') },
        { path: 'app/rentals', name: 'rentals', component: () => import('@/views/app/expense/RentalsView.vue') },
        { path: 'app/subcontracts', name: 'subcontracts', component: () => import('@/views/app/expense/SubcontractsView.vue') },
        { path: 'app/expenses', name: 'expenses', component: () => import('@/views/app/expense/ExpensesView.vue') },
        { path: 'app/materials', name: 'materials', component: () => import('@/views/app/MaterialsView.vue') },
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
