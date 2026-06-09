import { createRouter, createWebHistory } from 'vue-router'
import Dashboard from '../pages/Dashboard.vue'
import AddProperty from '../pages/AddProperty.vue'
import ListProperty from '../pages/ListProperty.vue'

const routes = [
    { path: '/', component: Dashboard },
    { path: '/dashboard', component: Dashboard },
    { path: '/add-property', component: AddProperty },
    { path: '/list-property', component: ListProperty },
]

export default createRouter({
    history: createWebHistory(),
    routes,
})