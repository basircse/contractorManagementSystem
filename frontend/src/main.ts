import { createApp } from 'vue'
import { createPinia } from 'pinia'
import PrimeVue, { type PrimeVueLocaleOptions } from 'primevue/config'
import { definePreset } from '@primevue/themes'
import Aura from '@primevue/themes/aura'
import ToastService from 'primevue/toastservice'
import ConfirmationService from 'primevue/confirmationservice'
import Tooltip from 'primevue/tooltip'

import Button from 'primevue/button'
import InputText from 'primevue/inputtext'
import InputNumber from 'primevue/inputnumber'
import Password from 'primevue/password'
import Select from 'primevue/select'
import MultiSelect from 'primevue/multiselect'
import SelectButton from 'primevue/selectbutton'
import DatePicker from 'primevue/datepicker'
import Textarea from 'primevue/textarea'
import Checkbox from 'primevue/checkbox'
import ToggleSwitch from 'primevue/toggleswitch'
import DataTable from 'primevue/datatable'
import Column from 'primevue/column'
import ColumnGroup from 'primevue/columngroup'
import Row from 'primevue/row'
import Dialog from 'primevue/dialog'
import Toast from 'primevue/toast'
import ConfirmDialog from 'primevue/confirmdialog'
import Tag from 'primevue/tag'
import Message from 'primevue/message'
import IconField from 'primevue/iconfield'
import InputIcon from 'primevue/inputicon'

import 'primeicons/primeicons.css'
import './styles.css'

import App from './App.vue'
import router from './router'
import { i18n, primeLocales, persistLang, type Lang } from './i18n'

const Theme = definePreset(Aura, {
  semantic: {
    primary: {
      50: '{teal.50}', 100: '{teal.100}', 200: '{teal.200}', 300: '{teal.300}', 400: '{teal.400}',
      500: '{teal.500}', 600: '{teal.600}', 700: '{teal.700}', 800: '{teal.800}', 900: '{teal.900}', 950: '{teal.950}',
    },
  },
})

const lang = i18n.global.locale.value as Lang
persistLang(lang)

const app = createApp(App)
app.use(createPinia())
app.use(router)
app.use(i18n)
app.use(PrimeVue, {
  theme: { preset: Theme, options: { darkModeSelector: '.app-dark' } },
  locale: primeLocales[lang] as PrimeVueLocaleOptions,
})
app.use(ToastService)
app.use(ConfirmationService)
app.directive('tooltip', Tooltip)

const components = {
  Button, InputText, InputNumber, Password, Select, MultiSelect, SelectButton, DatePicker, Textarea, Checkbox,
  ToggleSwitch, DataTable, Column, ColumnGroup, Row, Dialog, Toast, ConfirmDialog, Tag, Message, IconField, InputIcon,
}
for (const [name, c] of Object.entries(components)) app.component(name, c)

app.mount('#app')
