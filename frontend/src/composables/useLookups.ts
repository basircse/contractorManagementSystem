import { ref } from 'vue'
import { http } from '@/api/http'
import type { Client, Material, Party, Site, WorkItem } from '@/api/types'

/** Reference lists used by the income / expense forms. Each page loads what it needs once. */
export function useLookups() {
  const clients = ref<Client[]>([])
  const sites = ref<Site[]>([])
  const workItems = ref<WorkItem[]>([])
  const materials = ref<Material[]>([])
  const parties = ref<Party[]>([])

  const loaders = {
    clients: async () => (clients.value = (await http.get<Client[]>('/app/clients')).data),
    sites: async () => (sites.value = (await http.get<Site[]>('/app/sites')).data),
    workItems: async () => (workItems.value = (await http.get<WorkItem[]>('/app/work-items')).data),
    materials: async () => (materials.value = (await http.get<Material[]>('/app/materials')).data),
    parties: async () => (parties.value = (await http.get<Party[]>('/app/parties')).data),
  }

  async function load(...which: (keyof typeof loaders)[]) {
    await Promise.all(which.map((w) => loaders[w]()))
  }

  const sitesOf = (clientId: number | null | undefined) => sites.value.filter((s) => !clientId || s.clientId === clientId)

  return { clients, sites, workItems, materials, parties, load, sitesOf }
}

/** PrimeVue Tag severity for the document statuses. */
export function statusSeverity(status: string | null | undefined): string {
  switch (status) {
    case 'ACCEPTED':
    case 'SUBMITTED':
    case 'ACTIVE':
    case 'PAID':
    case 'OUT':
      return 'success'
    case 'SENT':
    case 'PARTIAL':
      return 'info'
    case 'UNPAID':
    case 'DRAFT':
      return 'warn'
    case 'REJECTED':
    case 'CANCELLED':
      return 'danger'
    default:
      return 'secondary'
  }
}
