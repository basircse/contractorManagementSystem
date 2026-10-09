// Seeds a demo contractor with sample projects, labour, rates and two weeks of attendance.
// DEV ONLY. Usage: node scripts/seed-demo.mjs   (backend must be running on :8080)
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
  console.log('Demo contractor already exists - nothing to do.')
  process.exit(0)
}
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
