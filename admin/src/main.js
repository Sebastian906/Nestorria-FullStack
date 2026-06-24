import { createApp } from 'vue'
import './style.css'
import Toast from 'vue-toastification'
import 'vue-toastification/dist/index.css'
import App from './App.vue'
import router from './router'
import { clerkPlugin } from '@clerk/vue'

const PUBLISHABLE_KEY = import.meta.env.VITE_CLERK_PUBLISHABLE_KEY

if (!PUBLISHABLE_KEY) {
    throw new Error('Add your clerk publishable key to the .env file as VITE_CLERK_PUBLISHABLE_KEY')
}

const app = createApp(App)

app.use(clerkPlugin, {
    publishableKey: PUBLISHABLE_KEY
})

app.use(Toast)
app.use(router)

app.mount('#app')