import type bn from './bn'

const en: typeof bn = {
  app: { name: 'Contractor Management', short: 'CCMS' },
  common: {
    save: 'Save', cancel: 'Cancel', edit: 'Edit', add: 'Add', delete: 'Delete', search: 'Search',
    archive: 'Archive', restore: 'Restore', actions: 'Actions', name: 'Name', code: 'Code', phone: 'Phone',
    email: 'Email', address: 'Address', description: 'Description', status: 'Status', active: 'Active',
    inactive: 'Inactive', archived: 'Archived', showArchived: 'Show archived', yes: 'Yes', no: 'No',
    total: 'Total', date: 'Date', from: 'From', to: 'To', apply: 'Apply', none: 'No data',
    all: 'All', note: 'Note', confirm: 'Confirm', saved: 'Saved', back: 'Back',
    loading: 'Loading…', optional: 'optional', print: 'Print', taka: 'Taka', days: 'days', hours: 'hours',
    readOnly: 'Read only',
  },
  auth: {
    login: 'Log in', logout: 'Log out', username: 'Username', password: 'Password', welcome: 'Welcome',
    subtitle: 'Sign in to your account', changePassword: 'Change password',
    currentPassword: 'Current password', newPassword: 'New password', passwordChanged: 'Password changed',
  },
  nav: {
    dashboard: 'Dashboard', contractors: 'Contractors', projects: 'Project structure', workItems: 'Work items',
    labours: 'Labour', rateCards: 'Wage rates', attendance: 'Daily attendance', payments: 'Wage payments',
    itemReport: 'Item-wise cost', labourReport: 'Labour history', setup: 'Setup', reports: 'Reports',
    work: 'Daily work', admin: 'Admin',
  },
  admin: {
    title: 'Contractor management', newContractor: 'New contractor', company: 'Company name', owner: 'Owner name',
    loginUser: 'Login user', lastLogin: 'Last login', block: 'Block', unblock: 'Unblock', blocked: 'Blocked',
    blockReason: 'Reason for blocking', blockConfirm: 'Block {name}? They will no longer be able to log in.',
    resetPassword: 'Reset password', viewBusiness: 'View business', viewing: 'You are viewing: {name}',
    exitView: 'Stop viewing', totalContractors: 'Total contractors', activeContractors: 'Active',
    blockedContractors: 'Blocked',
  },
  hierarchy: {
    client: 'Client', clients: 'Clients', site: 'Site', sites: 'Sites', building: 'Building', buildings: 'Buildings',
    floor: 'Floor', floors: 'Floors', unit: 'Unit', units: 'Units', levelNo: 'Level no.', startDate: 'Start date',
    selectClient: 'Select a client', selectSite: 'Select site', allLevels: 'All',
    addChild: 'Add {level}',
  },
  workItem: { title: 'Work items', nameBn: 'Name (Bangla)', nameEn: 'Name (English)', uom: 'Unit', sortOrder: 'Order' },
  labour: {
    title: 'Labour list', newLabour: 'New worker', skillTier: 'Skill tier', nid: 'NID', joinedOn: 'Joined on',
    tiers: { HELPER: 'Helper', MASON: 'Mason', JUNIOR: 'Junior', SENIOR: 'Senior', EXPERT: 'Expert' },
    history: 'History',
  },
  rate: {
    title: 'Wage rate cards', dailyRate: 'Daily rate', otRate: 'Overtime (per hour)', allowance: 'Skill allowance (daily)',
    effectiveFrom: 'Effective from', allSites: 'All sites', hint: 'A site-specific rate overrides the general one. Changes apply only to attendance saved afterwards.',
  },
  attendance: {
    title: 'Daily attendance & task tagging', step1: '1. Location', step2: '2. Work item', step3: '3. Workers',
    fullDay: 'Full day', halfDay: 'Half day', quarterDay: 'Quarter day', dayFraction: 'Day fraction', otHours: 'OT hours',
    assign: 'Save attendance', assigned: 'Attendance saved for {n}', todays: 'Attendance for this day', worked: 'Work',
    cost: 'Cost', selectWorkers: 'Select workers', selected: '{n} selected', editDay: 'Edit day',
    addSlice: 'Add work', remaining: 'left', alreadyFull: 'full', deleteConfirm: 'Delete this day\'s attendance?',
    workers: 'Workers',
  },
  payment: {
    title: 'Wage payments & dues', newPayment: 'Add payment', amount: 'Amount', type: 'Type',
    types: { WAGE: 'Wage', ADVANCE: 'Advance' }, earned: 'Earned', paid: 'Paid', due: 'Due',
    balances: 'Dues by worker', recent: 'Recent payments', onlyDue: 'Only with dues',
  },
  report: {
    itemTitle: 'Item-wise labour cost', labourTitle: 'Labour deployment history', workers: 'Workers',
    share: 'Share', bySite: 'By site', selectLabour: 'Select worker', byItem: 'By work item',
    periodTotal: 'Period total', location: 'Location',
  },
  dashboard: {
    periodCost: 'Cost, last 30 days', todayCost: 'Cost today', workersToday: 'Working today', activeSites: 'Active sites',
    totalDue: 'Total wages due', costByItem: 'Cost by work item', costBySite: 'Cost by site', trend: 'Daily cost',
    activeLabours: 'Active workers',
  },
  errors: {
    BAD_CREDENTIALS: 'Wrong username or password',
    ACCOUNT_BLOCKED: 'Your account is blocked. Please contact the admin.',
    UNAUTHENTICATED: 'Session expired, please log in again',
    FORBIDDEN: 'You are not allowed to do this',
    NOT_FOUND: 'Not found',
    VALIDATION_FAILED: 'Please check the information entered',
    DUPLICATE: 'This already exists',
    CONTRACTOR_REQUIRED: 'Select a contractor',
    RATE_NOT_FOUND: 'No wage rate for {name} ({tier}) on {date}. Add a wage rate first.',
    DAY_FRACTION_EXCEEDED: 'More than one full day: {names}',
    INVALID_LOCATION: 'Invalid location',
    WRONG_PASSWORD: 'Current password is wrong',
    NETWORK: 'Cannot reach the server',
    INTERNAL: 'Something went wrong',
    required: 'Required',
  },
}

export default en
