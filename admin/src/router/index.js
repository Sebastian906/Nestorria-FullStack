import { createRouter, createWebHistory } from 'vue-router'
import Dashboard from '../pages/Dashboard.vue'
import AddProperty from '../pages/AddProperty.vue'
import ListProperty from '../pages/ListProperty.vue'
import Reports from '../pages/Reports.vue'
import Categories from '../pages/Categories.vue'
import AiDashboard from '../pages/AiDashboard.vue'
import MlopsDashboard from '../pages/MlopsDashboard.vue'
import { useAppContext } from '../composables/useAppContext'

const routes = [
    { path: '/', component: Dashboard },
    { path: '/dashboard', component: Dashboard },
    { path: '/add-property', component: AddProperty },
    { path: '/list-property', component: ListProperty },
    { path: '/reports', component: Reports },
    { path: '/categories', component: Categories },
    { path: '/ai', component: AiDashboard, meta: { requiresAdmin: true } },
    { path: '/mlops', component: MlopsDashboard, meta: { requiresAdmin: true } }
]

const router = createRouter({
    history: createWebHistory(),
    routes,
})

router.beforeEach((to, from, next) => {
    if (to.meta.requiresAdmin) {
        const { isAdmin, roleLoaded } = useAppContext()
        if (!roleLoaded.value) {
            next({ path: '/' })
        } else if (!isAdmin.value) {
            next({ path: '/' })
        } else {
            next()
        }
    } else {
        next()
    }
})

export default router