import { createApp } from 'vue'
import './style.css'
import Toast from 'vue-toastification'
import 'vue-toastification/dist/index.css'
import App from './App.vue'
import router from './router'
import { clerkPlugin } from '@clerk/vue'
import { enUS, esES } from '@clerk/localizations'
import { i18n, LANG_KEY } from './i18n/index.js'

const PUBLISHABLE_KEY = import.meta.env.VITE_CLERK_PUBLISHABLE_KEY

if (!PUBLISHABLE_KEY) {
    throw new Error('Add your clerk publishable key to the .env file as VITE_CLERK_PUBLISHABLE_KEY')
}

const storedLocale = localStorage.getItem(LANG_KEY) ?? 'en'

const app = createApp(App)

app.use(i18n)
app.use(clerkPlugin, {
    publishableKey: PUBLISHABLE_KEY,
    localization: storedLocale.startsWith('es') ? esES : enUS,
})

app.use(Toast)
app.use(router)

app.mount('#app')