// Seeds a demo contractor with sample projects, labour, rates, two weeks of attendance,
// quotations / work orders / bills / receipts and purchases, rentals, sub-contracts, expenses.
// DEV ONLY. Usage: node scripts/seed-demo.mjs   (backend must be running; API=http://host:port to override)
// Re-running is safe: parts that already exist are skipped.
//
// Demo logins created / used by this script:
//   admin  / Admin@12345   (bootstrap admin from application.yml)
//   demo   / Demo@12345    (demo contractor)

const API = process.env.API ?? 'http://localhost:8080'
const ADMIN = { username: 'admin', password: 'Admin@12345' }
const DEMO = { username: 'demo', password: 'Demo@12345' }

async function call(method, path, token, body) {
  const res = await fetch(API + path, {
    method,
    headers: { 'Content-Type': 'application/json', ...(token ? { Authorization: `Bearer ${token}` } : {}) },
    body: body ? JSON.stringify(body) : undefined,
  })
  const text = await res.text()
  const data = text ? JSON.parse(text) : null
  if (!res.ok) throw new Error(`${method} ${path} -> ${res.status} ${text}`)
  return data
}
const login = async (u) => (await call('POST', '/api/auth/login', null, u)).token

const admin = await login(ADMIN)
const existing = (await call('GET', '/api/admin/contractors', admin)).find((c) => c.username === DEMO.username)
if (existing) {
  console.log('Demo contractor already exists - skipping projects, labour and attendance.')
} else {
  await seedBase()
}
await seedMoney(await login(DEMO))

async function seedBase() {
await call('POST', '/api/admin/contractors', admin, {
  name: 'মেসার্স আল-আমিন কনস্ট্রাকশন', ownerName: 'মোঃ আল-আমিন', phone: '01711223344', address: 'মিরপুর, ঢাকা', ...DEMO,
})
const T = await login(DEMO)
const post = (p, b) => call('POST', p, T, b)

const items = Object.fromEntries((await call('GET', '/api/app/work-items', T)).map((w) => [w.code, w.id]))

const c1 = await post('/api/app/clients', { name: 'গ্রিন হোমস লিমিটেড', phone: '01811000000' })
const c2 = await post('/api/app/clients', { name: 'রূপসা ডেভেলপার্স' })
const s1 = await post('/api/app/sites', { clientId: c1.id, name: 'সাইট আলফা - উত্তরা', address: 'সেক্টর ১০, উত্তরা' })
const s2 = await post('/api/app/sites', { clientId: c2.id, name: 'সাইট বিটা - বসুন্ধরা', address: 'ব্লক ডি, বসুন্ধরা' })

const floorsOf = {}
for (const [site, bNames] of [[s1, ['ভবন ১', 'ভবন ২']], [s2, ['টাওয়ার এ']]]) {
  for (const bn of bNames) {
    const b = await post('/api/app/buildings', { siteId: site.id, name: bn })
    floorsOf[b.id] = []
    for (let lvl = 1; lvl <= 4; lvl++) {
      const f = await post('/api/app/floors', { buildingId: b.id, name: `${['১ম', '২য়', '৩য়', '৪র্থ'][lvl - 1]} তলা`, levelNo: lvl })
      floorsOf[b.id].push(f)
      if (lvl === 2) {
        for (const u of ['এ', 'বি']) await post('/api/app/units', { floorId: f.id, name: `ফ্ল্যাট ${lvl}${u}` })
      }
    }
  }
}
const allFloors = Object.values(floorsOf).flat()

const rates = [['HELPER', 600, 75, 0], ['MASON', 900, 120, 50], ['JUNIOR', 750, 95, 0], ['SENIOR', 1100, 140, 100], ['EXPERT', 1400, 175, 150]]
for (const [tier, daily, ot, allow] of rates) {
  await post('/api/app/rate-cards', { skillTier: tier, dailyRate: daily, otHourlyRate: ot, skillAllowance: allow, effectiveFrom: '2026-01-01' })
}
// Uttara pays masons a little more.
await post('/api/app/rate-cards', { skillTier: 'MASON', siteId: s1.id, dailyRate: 950, otHourlyRate: 125, skillAllowance: 50, effectiveFrom: '2026-01-01' })

const names = {
  MASON: ['করিম মিস্ত্রি', 'রহিম মিস্ত্রি', 'জসিম উদ্দিন', 'হাবিব মিয়া', 'সালাম শেখ', 'নুরুল ইসলাম'],
  HELPER: ['জামাল', 'কামাল', 'রফিক', 'শফিক', 'বাবুল', 'মিজান', 'সোহেল', 'আলমগীর', 'ফারুক', 'মনির'],
  SENIOR: ['আব্দুল মজিদ'],
  EXPERT: ['ওস্তাদ হানিফ'],
  JUNIOR: ['রাকিব', 'সাকিব'],
}
const labour = {}
for (const [tier, list] of Object.entries(names)) {
  labour[tier] = []
  for (const name of list) labour[tier].push(await post('/api/app/labours', { name, skillTier: tier, joinedOn: '2026-06-01' }))
}

// 14 working days of attendance (Fridays off) following Workflow A in the SRS.
const ids = (tier, from, n) => labour[tier].slice(from, from + n).map((l) => l.id)
const today = new Date()
let posted = 0
for (let back = 14; back >= 0; back--) {
  const d = new Date(today)
  d.setDate(today.getDate() - back)
  if (d.getDay() === 5) continue
  const workDate = d.toISOString().slice(0, 10)
  const f = allFloors[back % 4]
  const f2 = allFloors[4 + (back % 4)]
  const f3 = allFloors[8 + (back % 4)]
  const plans = [
    [[...ids('MASON', 0, 2), ...ids('HELPER', 0, 4)], { floorId: f.id, workItemId: items.BRICKWORK, dayFraction: 1 }],
    [[...ids('MASON', 2, 2), ...ids('HELPER', 4, 2)], { floorId: f.id, workItemId: items.PLASTER_INT, dayFraction: 0.5 }],
    [[...ids('MASON', 2, 2), ...ids('HELPER', 4, 2)], { floorId: f2.id, workItemId: items.PLASTER_EXT, dayFraction: 0.5, otHours: back % 3 === 0 ? 2 : 0 }],
    [[...ids('EXPERT', 0, 1), ...ids('HELPER', 6, 3)], { floorId: f3.id, workItemId: items.COLUMN_CASTING, dayFraction: 1 }],
    [[...ids('SENIOR', 0, 1), ...ids('JUNIOR', 0, 2), ...ids('MASON', 4, 2)], { floorId: f3.id, workItemId: back % 2 ? items.SHUTTERING : items.SLAB_CASTING, dayFraction: 1, otHours: back % 2 ? 0 : 3 }],
  ]
  for (const [labourIds, slice] of plans) {
    await post('/api/app/attendance/assign', { workDate, labourIds, slice })
    posted += labourIds.length
  }
}

for (const l of [...labour.HELPER.slice(0, 5), ...labour.MASON.slice(0, 3)]) {
  await post('/api/app/labour-payments', { labourId: l.id, payDate: today.toISOString().slice(0, 10), amount: 3000, type: 'WAGE', note: 'সাপ্তাহিক মজুরি' })
}
await post('/api/app/labour-payments', { labourId: labour.MASON[0].id, payDate: today.toISOString().slice(0, 10), amount: 1000, type: 'ADVANCE' })

console.log(`Demo contractor seeded: ${posted} worker-slices of attendance.`)
}

// ---------------------------------------------------------------- income & expenses

async function seedMoney(T) {
  const get = (p) => call('GET', p, T)
  const post = (p, b) => call('POST', p, T, b)
  const put = (p, b) => call('PUT', p, T, b)
  if ((await get('/api/app/work-orders')).length) {
    console.log('Income / expense demo data already exists - nothing more to do.')
    return
  }
  const iso = (d) => d.toISOString().slice(0, 10)
  const daysAgo = (n) => { const d = new Date(); d.setDate(d.getDate() - n); return iso(d) }
  const items = Object.fromEntries((await get('/api/app/work-items')).map((w) => [w.code, w.id]))
  const mats = Object.fromEntries((await get('/api/app/materials')).map((m) => [m.code, m.id]))
  const sites = await get('/api/app/sites')
  const [alpha, beta] = [sites.find((s) => s.name.includes('আলফা')), sites.find((s) => s.name.includes('বিটা'))]
  const alphaB = (await get(`/api/app/buildings?siteId=${alpha.id}`))[0]
  const alphaF = await get(`/api/app/floors?buildingId=${alphaB.id}`)

  // Quotation -> work order -> monthly instalments, two bills, receipts.
  const q = await post('/api/app/quotations', {
    quoteDate: daysAgo(75), validUntil: daysAgo(45), clientId: alpha.clientId, siteId: alpha.id,
    title: 'ভবন ১ ও ২ — কাঠামো ও প্লাস্টার কাজ', discount: 50000,
    terms: '১. মালামাল ঠিকাদার সরবরাহ করবে।\n২. প্রতি মাসে চলতি বিল দাখিল করা হবে, ১৫ দিনের মধ্যে পরিশোধযোগ্য।\n৩. ৫% জামানত কাজ শেষে ফেরতযোগ্য।',
    lines: [
      { workItemId: items.COLUMN_CASTING, description: 'কলাম ঢালাই (১:১.৫:৩)', uom: 'CFT', quantity: 3000, rate: 120 },
      { workItemId: items.SLAB_CASTING, description: 'ছাদ ঢালাই', uom: 'CFT', quantity: 8000, rate: 110 },
      { workItemId: items.BRICKWORK, description: '১০ ইঞ্চি ইটের গাঁথুনি', uom: 'CFT', quantity: 12000, rate: 45 },
      { workItemId: items.PLASTER_INT, description: 'ভিতরের প্লাস্টার (১২ মিমি)', uom: 'SFT', quantity: 30000, rate: 22 },
      { workItemId: items.PLASTER_EXT, description: 'বাহিরের প্লাস্টার (১৮ মিমি)', uom: 'SFT', quantity: 15000, rate: 28 },
    ],
  })
  await post(`/api/app/quotations/${q.id}/status`, { status: 'SENT' })
  const wo = await post(`/api/app/quotations/${q.id}/work-order`, {
    woDate: daysAgo(60), startDate: daysAgo(60), retentionPercent: 5, clientRef: 'GHL/WO/2026/118',
  })
  const per = Math.round(Number(wo.contractValue) / 6)
  const plan = []
  for (let i = 0; i < 6; i++) {
    const d = new Date()
    d.setDate(d.getDate() - 45 + 30 * i)
    plan.push({ title: `মাসিক কিস্তি ${['১', '২', '৩', '৪', '৫', '৬'][i]}`, dueDate: iso(d), amount: i < 5 ? per : Number(wo.contractValue) - per * 5 })
  }
  const planned = await put(`/api/app/work-orders/${wo.id}/milestones`, plan)
  const b1 = await post('/api/app/bills', {
    billDate: daysAgo(44), dueDate: daysAgo(29), workOrderId: wo.id, milestoneId: planned.milestones[0].id,
    title: 'মাসিক কিস্তি ১', deductionAmount: 7000, deductionNote: 'এআইটি', lines: [], submit: true,
  })
  await post('/api/app/receipts', { receiptDate: daysAgo(30), workOrderId: wo.id, billId: b1.id, amount: b1.netAmount, method: 'BANK', reference: 'DBBL-CHQ-445120' })
  const b2 = await post('/api/app/bills', {
    billDate: daysAgo(14), dueDate: daysAgo(1), workOrderId: wo.id, milestoneId: planned.milestones[1].id,
    title: 'মাসিক কিস্তি ২', lines: [], submit: true,
  })
  await post('/api/app/receipts', { receiptDate: daysAgo(5), workOrderId: wo.id, billId: b2.id, amount: 150000, method: 'CHEQUE', reference: 'IBBL-778812' })
  await post('/api/app/receipts', { receiptDate: daysAgo(58), workOrderId: wo.id, amount: 200000, method: 'BANK', note: 'মবিলাইজেশন অগ্রিম' })
  const brickLine = planned.lines.find((l) => l.workItemId === items.BRICKWORK)
  await post('/api/app/bills', {
    billDate: daysAgo(0), workOrderId: wo.id, title: 'চলতি বিল — ইটের গাঁথুনি',
    lines: [{ workOrderLineId: brickLine.id, description: brickLine.description, uom: brickLine.uom, quantity: 3500, rate: brickLine.rate }],
    submit: false,
  })
  // A second quotation still waiting for the client.
  const q2 = await post('/api/app/quotations', {
    quoteDate: daysAgo(6), validUntil: daysAgo(-24), clientId: beta.clientId, siteId: beta.id, title: 'টাওয়ার এ — টাইলস ও রং',
    lines: [
      { workItemId: items.TILES, description: 'ফ্লোর টাইলস (২৪x২৪)', uom: 'SFT', quantity: 9000, rate: 38 },
      { workItemId: items.PAINTING, description: 'প্লাস্টিক পেইন্ট (২ কোট)', uom: 'SFT', quantity: 40000, rate: 14 },
    ],
  })
  await post(`/api/app/quotations/${q2.id}/status`, { status: 'SENT' })

  // Third parties and their costs.
  const party = (name, type, trade, phone) => post('/api/app/parties', { name, type, trade, phone })
  const steel = await party('মেসার্স রহমান স্টিল', 'SUPPLIER', 'রড ও সিমেন্ট', '01711000111')
  const sand = await party('বিসমিল্লাহ বালু ও ইট', 'SUPPLIER', 'বালু, ইট, খোয়া', '01811000222')
  const shutter = await party('হাজী শাটারিং হাউস', 'RENTAL', 'স্টিল সাটার ও জ্যাক', '01911000333')
  const elec = await party('জাহিদ ইলেকট্রিক', 'SUBCONTRACTOR', 'বৈদ্যুতিক কাজ', '01611000444')
  const tiles = await party('রফিক টাইলস ফিটিং', 'SUBCONTRACTOR', 'টাইলস', '01511000555')
  await post('/api/app/purchases', {
    purchaseDate: daysAgo(40), partyId: steel.id, invoiceNo: 'RS-2231', siteId: alpha.id, floorId: alphaF[0].id, workItemId: items.COLUMN_CASTING,
    paidAmount: 100000, payMethod: 'BANK',
    lines: [{ materialId: mats.ROD, quantity: 2500, rate: 92 }, { materialId: mats.CEMENT, quantity: 300, rate: 540 }],
  })
  await post('/api/app/purchases', {
    purchaseDate: daysAgo(12), partyId: steel.id, invoiceNo: 'RS-2290', siteId: alpha.id, floorId: alphaF[1].id, workItemId: items.SLAB_CASTING,
    lines: [{ materialId: mats.ROD, quantity: 3200, rate: 94 }, { materialId: mats.CEMENT, quantity: 450, rate: 545 }],
  })
  await post('/api/app/purchases', {
    purchaseDate: daysAgo(20), partyId: sand.id, invoiceNo: 'BB-117', siteId: alpha.id, workItemId: items.BRICKWORK,
    paidAmount: 50000, payMethod: 'CASH',
    lines: [{ materialId: mats.BRICK, quantity: 40000, rate: 12 }, { materialId: mats.SAND, quantity: 1200, rate: 45 }],
  })
  await post('/api/app/purchases', {
    purchaseDate: daysAgo(3), siteId: alpha.id, workItemId: items.SHUTTERING,
    lines: [{ materialId: mats.NAIL, quantity: 25, rate: 140 }, { materialId: mats.BINDING_WIRE, quantity: 60, rate: 120 }],
  })
  const sh = await post('/api/app/rentals', {
    partyId: shutter.id, materialId: mats.STEEL_SHUTTER, siteId: alpha.id, floorId: alphaF[1].id, workItemId: items.SHUTTERING,
    quantity: 300, ratePerDay: 3, startDate: daysAgo(35),
  })
  await post(`/api/app/rentals/${sh.id}/charge`, { date: daysAgo(6), note: 'মাসিক বিল' })
  await post(`/api/app/rentals/${sh.id}/return`, { date: daysAgo(2), quantity: 100 })
  await post('/api/app/rentals', {
    partyId: shutter.id, materialId: mats.PROP, siteId: alpha.id, floorId: alphaF[1].id, workItemId: items.SHUTTERING,
    quantity: 150, ratePerDay: 2, startDate: daysAgo(25),
  })
  await post('/api/app/rentals', {
    materialId: mats.BAMBOO, description: 'সাইট বিটা থেকে ধার', siteId: alpha.id, workItemId: items.SHUTTERING,
    quantity: 120, ratePerDay: 0, startDate: daysAgo(18),
  })
  const sc = await post('/api/app/subcontracts', {
    partyId: elec.id, title: 'ভবন ১ — সম্পূর্ণ বৈদ্যুতিক কাজ (কনসিল্ড ওয়্যারিং)', siteId: alpha.id, buildingId: alphaB.id,
    workItemId: items.GENERAL, uom: 'LS', contractAmount: 180000, startDate: daysAgo(30),
  })
  await post(`/api/app/subcontracts/${sc.id}/bills`, { billDate: daysAgo(10), amount: 60000, note: 'প্রথম চলতি বিল' })
  const tc = await post('/api/app/subcontracts', {
    partyId: tiles.id, title: 'টাওয়ার এ — ফ্লোর টাইলস ফিটিং', siteId: beta.id, workItemId: items.TILES,
    uom: 'SFT', quantity: 9000, rate: 12, startDate: daysAgo(8),
  })
  await post(`/api/app/subcontracts/${tc.id}/bills`, { billDate: daysAgo(1), quantity: 2500, note: '১ম ও ২য় তলা' })
  const expenses = [
    [28, 'TRANSPORT', 4500, 'রড পরিবহন (ট্রাক)', alpha], [21, 'FOOD', 3200, 'ঢালাইয়ের দিন নাস্তা', alpha],
    [15, 'FUEL', 2800, 'মিক্সার মেশিনের ডিজেল', alpha], [9, 'UTILITY', 6500, 'সাইটের বিদ্যুৎ বিল', alpha],
    [4, 'TOOLS', 3900, 'কোদাল, কড়াই, বালতি', beta], [2, 'TRANSPORT', 1800, 'টাইলস পরিবহন', beta],
  ]
  for (const [d, category, amount, description, site] of expenses) {
    await post('/api/app/expenses', { expenseDate: daysAgo(d), category, siteId: site.id, amount, description })
  }
  await post('/api/app/party-payments', { partyId: steel.id, payDate: daysAgo(8), amount: 250000, method: 'CHEQUE', reference: 'চেক ৬৬১২০৪' })
  await post('/api/app/party-payments', { partyId: elec.id, payDate: daysAgo(9), amount: 40000, method: 'CASH' })
  await post('/api/app/party-payments', { partyId: shutter.id, payDate: daysAgo(5), amount: 20000, method: 'MOBILE', reference: 'বিকাশ' })
  console.log('Income / expense demo data seeded: 2 quotations, 1 work order, 3 bills, 5 parties, purchases, rentals, sub-contracts, expenses.')
}
