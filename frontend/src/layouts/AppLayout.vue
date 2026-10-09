<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { useAuthStore } from '@/stores/auth'
import { useLanguage } from '@/composables/useLanguage'
import ChangePasswordDialog from '@/components/ChangePasswordDialog.vue'

const { t } = useI18n()
const auth = useAuthStore()
const router = useRouter()
const route = useRoute()
const { locale, setLang } = useLanguage()

const sidebarOpen = ref(false)
const pwdOpen = ref(false)
watch(() => route.fullPath, () => (sidebarOpen.value = false))

interface MenuItem { to: string; label: string; icon: string }
interface MenuGroup { label?: string; items: MenuItem[] }

const menu = computed<MenuGroup[]>(() => {
  const app: MenuGroup[] = [
    { items: [{ to: '/app/dashboard', label: t('nav.dashboard'), icon: 'pi pi-chart-bar' }] },
    {
      label: t('nav.work'),
      items: [
        { to: '/app/attendance', label: t('nav.attendance'), icon: 'pi pi-calendar-plus' },
        { to: '/app/payments', label: t('nav.payments'), icon: 'pi pi-wallet' },
      ],
    },
    {
      label: t('nav.income'),
      items: [
        { to: '/app/quotations', label: t('nav.quotations'), icon: 'pi pi-file' },
        { to: '/app/work-orders', label: t('nav.workOrders'), icon: 'pi pi-briefcase' },
        { to: '/app/bills', label: t('nav.bills'), icon: 'pi pi-receipt' },
        { to: '/app/receipts', label: t('nav.receipts'), icon: 'pi pi-money-bill' },
      ],
    },
    {
      label: t('nav.expenses'),
      items: [
        { to: '/app/purchases', label: t('nav.purchases'), icon: 'pi pi-shopping-cart' },
        { to: '/app/rentals', label: t('nav.rentals'), icon: 'pi pi-box' },
        { to: '/app/subcontracts', label: t('nav.subcontracts'), icon: 'pi pi-sitemap' },
        { to: '/app/expenses', label: t('nav.siteExpenses'), icon: 'pi pi-tags' },
        { to: '/app/parties', label: t('nav.parties'), icon: 'pi pi-id-card' },
      ],
    },
    {
      label: t('nav.reports'),
      items: [
        { to: '/app/reports/profit', label: t('nav.profit'), icon: 'pi pi-chart-line' },
        { to: '/app/reports/items', label: t('nav.itemReport'), icon: 'pi pi-chart-pie' },
        { to: '/app/reports/labour', label: t('nav.labourReport'), icon: 'pi pi-history' },
      ],
    },
    {
      label: t('nav.setup'),
      items: [
        { to: '/app/projects', label: t('nav.projects'), icon: 'pi pi-building' },
        { to: '/app/labours', label: t('nav.labours'), icon: 'pi pi-users' },
        { to: '/app/rate-cards', label: t('nav.rateCards'), icon: 'pi pi-money-bill' },
        { to: '/app/work-items', label: t('nav.workItems'), icon: 'pi pi-list' },
        { to: '/app/materials', label: t('nav.materials'), icon: 'pi pi-th-large' },
      ],
    },
  ]
  if (!auth.isAdmin) return app
  const admin: MenuGroup = { label: t('nav.admin'), items: [{ to: '/admin/contractors', label: t('nav.contractors'), icon: 'pi pi-briefcase' }] }
  return auth.viewing ? [admin, ...app] : [admin]
})

const langOptions = [
  { label: 'বাংলা', value: 'bn' },
  { label: 'English', value: 'en' },
]
const lang = computed({
  get: () => locale.value,
  set: (v: string) => v && setLang(v as 'bn' | 'en'),
})

function stopViewing() {
  auth.stopViewing()
  router.push({ name: 'contractors' })
}

function logout() {
  auth.logout()
  router.replace({ name: 'login' })
}
</script>

<template>
  <div class="layout" :class="{ open: sidebarOpen }">
    <aside class="sidebar">
      <div class="brand">
        <i class="pi pi-building-columns" />
        <span>{{ t('app.name') }}</span>
      </div>
      <nav>
        <div v-for="(g, gi) in menu" :key="gi" class="group">
          <div v-if="g.label" class="group-label">{{ g.label }}</div>
          <RouterLink v-for="m in g.items" :key="m.to" :to="m.to" class="item" active-class="active">
            <i :class="m.icon" />
            <span>{{ m.label }}</span>
          </RouterLink>
        </div>
      </nav>
    </aside>
    <div class="backdrop" @click="sidebarOpen = false" />

    <div class="main">
      <header class="topbar">
        <Button class="hamburger" icon="pi pi-bars" text rounded aria-label="menu" @click="sidebarOpen = !sidebarOpen" />
        <div class="business">{{ auth.businessName }}</div>
        <div class="spacer" />
        <SelectButton v-model="lang" :options="langOptions" option-label="label" option-value="value" :allow-empty="false" size="small" />
        <div class="user">
          <i class="pi pi-user" />
          <span class="uname">{{ auth.user?.fullName || auth.user?.username }}</span>
        </div>
        <Button icon="pi pi-key" text rounded v-tooltip.bottom="t('auth.changePassword')" :aria-label="t('auth.changePassword')" @click="pwdOpen = true" />
        <Button icon="pi pi-sign-out" text rounded severity="secondary" v-tooltip.bottom="t('auth.logout')" :aria-label="t('auth.logout')" @click="logout" />
      </header>

      <div v-if="auth.readOnly" class="viewing no-print">
        <i class="pi pi-eye" />
        <span>{{ t('admin.viewing', { name: auth.viewing?.name }) }} · {{ t('common.readOnly') }}</span>
        <Button :label="t('admin.exitView')" size="small" severity="warn" outlined @click="stopViewing" />
      </div>

      <main>
        <RouterView :key="auth.viewing?.id ?? 0" />
      </main>
    </div>
    <ChangePasswordDialog v-model:visible="pwdOpen" />
  </div>
</template>

<style scoped>
.layout { display: flex; min-height: 100vh; }
.sidebar {
  width: 240px; flex-shrink: 0; background: var(--app-sidebar); color: var(--app-sidebar-text);
  position: sticky; top: 0; height: 100vh; overflow-y: auto;
}
.brand { display: flex; align-items: center; gap: 10px; padding: 18px 18px 14px; font-weight: 700; font-size: 1.05rem; color: #fff; }
.brand i { font-size: 1.3rem; color: #5eead4; }
.group { padding: 6px 0; }
.group-label { padding: 8px 18px 4px; font-size: 0.75rem; text-transform: uppercase; letter-spacing: 0.04em; opacity: 0.6; }
.item { display: flex; align-items: center; gap: 10px; padding: 9px 18px; text-decoration: none; color: inherit; border-left: 3px solid transparent; }
.item:hover { background: rgba(255, 255, 255, 0.06); }
.item.active { background: var(--app-sidebar-active); color: #fff; border-left-color: #5eead4; }
.main { flex: 1; min-width: 0; display: flex; flex-direction: column; }
.topbar {
  display: flex; align-items: center; gap: 10px; padding: 8px 16px; background: var(--app-surface);
  border-bottom: 1px solid var(--app-border); position: sticky; top: 0; z-index: 5;
}
.business { font-weight: 600; font-size: 1.05rem; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.user { display: flex; align-items: center; gap: 6px; color: var(--app-muted); margin-left: 6px; }
.viewing { display: flex; align-items: center; gap: 10px; padding: 8px 20px; background: var(--app-warn-bg); color: var(--app-warn-text); font-weight: 500; flex-wrap: wrap; }
.hamburger { display: none; }
.backdrop { display: none; }

@media (max-width: 900px) {
  .sidebar { position: fixed; left: 0; top: 0; z-index: 20; transform: translateX(-100%); transition: transform 0.2s; }
  .layout.open .sidebar { transform: none; }
  .layout.open .backdrop { display: block; position: fixed; inset: 0; background: rgba(0, 0, 0, 0.35); z-index: 15; }
  .hamburger { display: inline-flex; }
  .uname { display: none; }
}
</style>
