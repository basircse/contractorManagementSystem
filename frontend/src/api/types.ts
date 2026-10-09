export type Role = 'ADMIN' | 'CONTRACTOR'
export type SkillTier = 'HELPER' | 'MASON' | 'JUNIOR' | 'SENIOR' | 'EXPERT'
export const SKILL_TIERS: SkillTier[] = ['HELPER', 'MASON', 'JUNIOR', 'SENIOR', 'EXPERT']
export type RecordStatus = 'ACTIVE' | 'ARCHIVED'

export interface Me {
  id: number
  username: string
  fullName: string | null
  role: Role
  contractorId: number | null
  contractorName: string | null
  preferredLang: 'bn' | 'en'
}

export interface Contractor {
  id: number
  name: string
  ownerName: string | null
  phone: string | null
  email: string | null
  address: string | null
  status: 'ACTIVE' | 'BLOCKED'
  plan: string
  blockedReason: string | null
  username: string | null
  lastLoginAt: string | null
  createdAt: string
}

interface Node {
  id: number
  name: string
  code: string | null
  description: string | null
  status: RecordStatus
}
export interface Client extends Node { phone: string | null; email: string | null; address: string | null }
export interface Site extends Node { clientId: number; address: string | null; startDate: string | null }
export interface Building extends Node { siteId: number }
export interface Floor extends Node { buildingId: number; levelNo: number | null }
export interface Unit extends Node { floorId: number }

export interface WorkItem {
  id: number
  code: string
  nameBn: string
  nameEn: string
  uom: string
  sortOrder: number
  active: boolean
}

export interface Labour {
  id: number
  name: string
  phone: string | null
  nid: string | null
  address: string | null
  skillTier: SkillTier
  joinedOn: string | null
  active: boolean
}

export interface RateCard {
  id: number
  skillTier: SkillTier
  siteId: number | null
  dailyRate: number
  otHourlyRate: number
  skillAllowance: number
  effectiveFrom: string
}

export interface LabourPayment {
  id: number
  labourId: number
  payDate: string
  amount: number
  type: 'WAGE' | 'ADVANCE'
  note: string | null
}

export interface Slice {
  siteId: number | null
  buildingId: number | null
  floorId: number | null
  unitId: number | null
  workItemId: number | null
  dayFraction: number
  otHours: number
}

export interface Allocation extends Slice { id: number; locationLabel: string }

export interface AttendanceDay {
  id: number
  labourId: number
  labourName: string
  skillTier: SkillTier
  workDate: string
  note: string | null
  totalFraction: number
  totalOtHours: number
  cost: number
  allocations: Allocation[]
}

export interface ItemCostRow {
  workItemId: number
  code: string
  nameBn: string
  nameEn: string
  uom: string
  days: number
  otHours: number
  amount: number
  workers: number
}
export interface SiteItemRow { siteId: number; siteName: string; workItemId: number; days: number; amount: number }
export interface ItemCostReport { items: ItemCostRow[]; bySite: SiteItemRow[]; totalDays: number; totalAmount: number }

export interface LabourLine {
  date: string
  siteId: number
  siteName: string
  buildingName: string | null
  floorName: string | null
  unitName: string | null
  workItemId: number
  itemBn: string
  itemEn: string
  days: number
  otHours: number
  amount: number
}
export interface LabourHistory {
  labour: Labour
  lines: LabourLine[]
  byItem: { workItemId: number; itemBn: string; itemEn: string; days: number; amount: number }[]
  bySite: { siteId: number; siteName: string; days: number; amount: number }[]
  periodDays: number
  periodAmount: number
  totalEarned: number
  totalPaid: number
  due: number
}
export interface LabourBalance {
  labourId: number
  name: string
  skillTier: SkillTier
  active: boolean
  earned: number
  paid: number
  due: number
}
export interface NamedAmount { id: number; name: string; nameEn: string; amount: number }
export interface Dashboard {
  periodCost: number
  todayCost: number
  workersToday: number
  activeSites: number
  activeLabours: number
  totalDue: number
  byItem: NamedAmount[]
  bySite: NamedAmount[]
  trend: { date: string; amount: number }[]
}

export interface ApiError {
  code: string
  message: string
  params?: Record<string, unknown>
  fieldErrors?: { field: string; message: string }[]
}
