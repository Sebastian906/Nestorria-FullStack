import { createRouter, createWebHistory } from 'vue-router'
import Dashboard from '../pages/Dashboard.vue'
import AddProperty from '../pages/AddProperty.vue'
import ListProperty from '../pages/ListProperty.vue'
import Reports from '../pages/Reports.vue'
import Categories from '../pages/Categories.vue'

const routes = [
    { path: '/', component: Dashboard },
    { path: '/dashboard', component: Dashboard },
    { path: '/add-property', component: AddProperty },
    { path: '/list-property', component: ListProperty },
    { path: '/reports', component: Reports },
    { path: '/categories', component: Categories },
]

export default createRouter({
    history: createWebHistory(),
    routes,
})