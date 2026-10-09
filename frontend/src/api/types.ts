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
  labour: number
  material: number
  rental: number
  subcontract: number
  expense: number
}
export interface SiteItemRow { siteId: number; siteName: string; workItemId: number; days: number; amount: number }
export interface ItemCostReport {
  items: ItemCostRow[]
  bySite: SiteItemRow[]
  totalDays: number
  totalAmount: number
  generalAmount: number
  split: CostSplit
}

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
  periodBilled: number
  periodReceived: number
  receivable: number
  overdueAmount: number
  overdueBills: number
  milestonesDue: number
  partyDue: number
  periodSplit: CostSplit
}

// ---------------------------------------------------------------- income

export type PayMethod = 'CASH' | 'BANK' | 'CHEQUE' | 'MOBILE'
export const PAY_METHODS: PayMethod[] = ['CASH', 'BANK', 'CHEQUE', 'MOBILE']

export interface PricedLine {
  id?: number | null
  lineNo?: number
  workItemId: number | null
  workOrderLineId?: number | null
  description: string
  uom: string | null
  quantity: number
  rate: number
  amount?: number
}

export type QuotationStatus = 'DRAFT' | 'SENT' | 'ACCEPTED' | 'REJECTED'
export interface Quotation {
  id: number
  quoteNo: string
  quoteDate: string
  validUntil: string | null
  clientId: number
  siteId: number | null
  title: string
  status: QuotationStatus
  subtotal: number
  discount: number
  total: number
  terms: string | null
  notes: string | null
  clientName: string
  siteName: string | null
  workOrderId: number | null
  workOrderNo: string | null
  lines: PricedLine[] | null
}

export type WorkOrderStatus = 'ACTIVE' | 'COMPLETED' | 'CANCELLED'
export interface WorkOrderTotals {
  contractValue: number
  billedGross: number
  retentionHeld: number
  deductions: number
  billedNet: number
  received: number
  receivable: number
  unbilled: number
}
export interface Milestone {
  id: number
  workOrderId: number
  seq: number
  title: string
  dueDate: string | null
  amount: number
  billId: number | null
  billNo?: string | null
  due?: boolean
}
interface WorkOrderBase {
  id: number
  woNo: string
  woDate: string
  clientId: number
  siteId: number
  quotationId: number | null
  title: string
  clientRef: string | null
  startDate: string | null
  endDate: string | null
  retentionPercent: number
  discount: number
  contractValue: number
  status: WorkOrderStatus
  notes: string | null
  clientName: string
  siteName: string
  totals: WorkOrderTotals
}
export interface WorkOrderSummary extends WorkOrderBase {
  nextMilestone: Milestone | null
  dueMilestones: number
}
export interface WorkOrderLine extends PricedLine { id: number; billedQty: number; billedAmount: number }
export interface WorkOrder extends WorkOrderBase {
  quotationNo: string | null
  lines: WorkOrderLine[]
  milestones: Milestone[]
  bills: BillSummary[]
  receipts: ClientReceipt[]
}

export type BillStatus = 'DRAFT' | 'SUBMITTED' | 'CANCELLED'
export type PayStatus = 'UNPAID' | 'PARTIAL' | 'PAID'
export interface BillSummary {
  id: number
  billNo: string
  billDate: string
  dueDate: string | null
  title: string
  status: BillStatus
  workOrderId: number
  woNo: string
  clientId: number
  clientName: string
  siteId: number
  siteName: string
  milestoneId: number | null
  grossAmount: number
  retentionAmount: number
  deductionAmount: number
  netAmount: number
  received: number
  balance: number
  payStatus: PayStatus | null
  overdue: boolean
}
export interface Bill {
  id: number
  billNo: string
  billDate: string
  dueDate: string | null
  workOrderId: number
  clientId: number
  siteId: number
  milestoneId: number | null
  periodFrom: string | null
  periodTo: string | null
  title: string
  grossAmount: number
  retentionAmount: number
  deductionAmount: number
  deductionNote: string | null
  netAmount: number
  status: BillStatus
  notes: string | null
  woNo: string
  woTitle: string
  clientRef: string | null
  contractValue: number
  client: Client
  site: Site
  milestoneTitle: string | null
  lines: PricedLine[]
  receipts: ClientReceipt[]
  received: number
  balance: number
  payStatus: PayStatus | null
  overdue: boolean
  previousGross: number
}
export interface ClientReceipt {
  id: number
  receiptDate: string
  clientId: number
  workOrderId: number
  billId: number | null
  amount: number
  method: PayMethod
  reference: string | null
  note: string | null
  clientName?: string
  woNo?: string
  billNo?: string | null
}
export interface Profile { id: number; name: string; ownerName: string | null; phone: string | null; email: string | null; address: string | null }

// ---------------------------------------------------------------- expenses

export type PartyType = 'SUPPLIER' | 'SUBCONTRACTOR' | 'RENTAL' | 'OTHER'
export const PARTY_TYPES: PartyType[] = ['SUPPLIER', 'SUBCONTRACTOR', 'RENTAL', 'OTHER']
export interface Party {
  id: number
  name: string
  type: PartyType
  trade: string | null
  phone: string | null
  address: string | null
  note: string | null
  active: boolean
  charged: number
  paid: number
  due: number
}
export interface PartyPayment {
  id: number
  partyId: number
  payDate: string
  amount: number
  method: PayMethod
  reference: string | null
  purchaseId: number | null
  note: string | null
  partyName?: string
}
export interface StatementLine {
  date: string
  kind: 'MATERIAL' | 'RENTAL' | 'SUBCONTRACT' | 'EXPENSE' | 'PAYMENT' | string
  sourceId: number
  siteName: string | null
  charge: number
  payment: number
  balance: number
  note: string | null
}

export type MaterialKind = 'CONSUMABLE' | 'RENTABLE'
export interface Material {
  id: number
  code: string
  nameBn: string
  nameEn: string
  uom: string
  kind: MaterialKind
  sortOrder: number
  active: boolean
}

interface Located {
  siteId: number
  buildingId: number | null
  floorId: number | null
  unitId: number | null
  workItemId: number | null
  locationLabel: string
}
export interface PurchaseLine { id?: number; materialId: number | null; description: string | null; uom: string | null; quantity: number; rate: number; amount?: number }
export interface Purchase extends Located {
  id: number
  purchaseDate: string
  partyId: number | null
  partyName: string | null
  invoiceNo: string | null
  totalAmount: number
  paidAmount: number
  note: string | null
  lines: PurchaseLine[]
}
export interface RentalEvent {
  id: number
  eventDate: string
  type: 'CHARGE' | 'RETURN'
  quantity: number
  chargeFrom: string | null
  chargeTo: string | null
  chargedQty: number
  days: number
  amount: number
  note: string | null
}
export interface Rental extends Located {
  id: number
  partyId: number | null
  partyName: string | null
  materialId: number | null
  description: string | null
  uom: string | null
  quantity: number
  quantityOut: number
  ratePerDay: number
  startDate: string
  chargedUntil: string | null
  endDate: string | null
  status: 'OUT' | 'RETURNED'
  note: string | null
  charged: number
  accrued: number
  daysOut: number
  events: RentalEvent[]
}
export interface SubcontractBill { id: number; billDate: string; quantity: number | null; amount: number; note: string | null }
export interface Subcontract extends Located {
  id: number
  partyId: number
  partyName: string
  title: string
  uom: string | null
  quantity: number | null
  rate: number | null
  contractAmount: number
  startDate: string | null
  status: 'ACTIVE' | 'COMPLETED' | 'CANCELLED'
  note: string | null
  billedQty: number
  billedAmount: number
  remaining: number
  bills: SubcontractBill[] | null
}
export type ExpenseCategory = 'TRANSPORT' | 'FUEL' | 'FOOD' | 'UTILITY' | 'TOOLS' | 'REPAIR' | 'OFFICE' | 'OTHER'
export const EXPENSE_CATEGORIES: ExpenseCategory[] = ['TRANSPORT', 'FUEL', 'FOOD', 'UTILITY', 'TOOLS', 'REPAIR', 'OFFICE', 'OTHER']
export interface SiteExpense extends Located {
  id: number
  expenseDate: string
  category: ExpenseCategory
  partyId: number | null
  partyName: string | null
  amount: number
  description: string | null
}

// ---------------------------------------------------------------- reports

export interface CostSplit { labour: number; material: number; rental: number; subcontract: number; expense: number; total: number }
export interface SiteProfit {
  siteId: number | null
  siteName: string | null
  clientName: string | null
  contractValue: number
  billed: number
  received: number
  cost: CostSplit
  profit: number
  marginPercent: number | null
}
export interface ItemProfit { workItemId: number | null; nameBn: string | null; nameEn: string | null; contractValue: number; billed: number; cost: CostSplit; profit: number }
export interface ProfitReport { sites: SiteProfit[]; items: ItemProfit[]; total: SiteProfit }

export interface ApiError {
  code: string
  message: string
  params?: Record<string, unknown>
  fieldErrors?: { field: string; message: string }[]
}
