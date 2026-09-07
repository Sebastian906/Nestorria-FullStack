<script setup>
import { watch, onMounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { UserButton } from '@clerk/vue'
import { useAppContext } from '../composables/useAppContext'
import { assets } from '../assets/assets'
import NotificationBell from './NotificationBell.vue'
import { useI18n } from 'vue-i18n'
import LanguageSwitcher from './LanguageSwitcher.vue'

const router = useRouter()
const route = useRoute()
const { isOwner, isAdmin, roleLoaded, user } = useAppContext()
const { t } = useI18n()

const navItems = [
    {
        path: '/dashboard',
        key: 'dashboard',
        icon: assets.dashboard
    },
    {
        path: '/add-property',
        key: 'addProperty',
        icon: assets.housePlus
    },
    {
        path: '/list-property',
        key: 'listProperty',
        icon: assets.list
    },
    {
        path: '/reports',
        key: 'reports',
        icon: assets.clipboard
    },
    {
        path: '/categories',
        key: 'categories',
        icon: assets.network
    },
    {
        path: '/ai',
        key: 'ai',
        icon: assets.brain,
        adminOnly: true
    },
    {
        path: '/mlops',
        key: 'mlops',
        icon: assets.brainCog,
        adminOnly: true
    },
]

const checkAuth = () => {
    if (!roleLoaded.value) return
    if (!isAdmin.value) {
        if (route.path !== '/') {
            router.replace({ path: '/' })
        }
    }
}

onMounted(() => {
    checkAuth()
})

watch([isOwner, roleLoaded], checkAuth)

const userButtonAppearance = {
    elements: {
        userButtonAvatarBox: {
            width: '45px',
            height: '45px'
        }
    }
}
</script>

<template>
    <div
        class="max-md:flexCenter flex flex-col justify-between bg-white sm:m-3 md:min-w-[20%] md:min-h-[97vh] rounded-xl shadow">
        <div class="flex flex-col gap-y-6 max-md:items-center md:flex-col md:pt-5">

            <div class="w-full flex justify-between md:flex-col">
                <div class="flex flex-1 p-3 lg:pl-8">
                    <RouterLink to="/dashboard">
                        <img :src="assets.logoImg" alt="Logo" class="h-11" />
                    </RouterLink>
                </div>

                <div class="md:hidden flex items-center gap-3 md:bg-primary rounded-b-xl p-2 pl-5 lg:pl-10 md:mt-10">
                    <UserButton :appearance="userButtonAppearance" />
                    <div class="text-sm font-semibold text-gray-800 capitalize flex-1">
                        {{ user?.firstName }} {{ user?.lastName }}
                    </div>
                    <NotificationBell />
                    <LanguageSwitcher />
                </div>
            </div>

            <div class="flex md:flex-col md:gap-x-5 gap-y-8 md:mt-4">
                <RouterLink v-for="link in navItems.filter(item => !item.adminOnly || isAdmin)" :key="link.key" :to="link.path" custom
                    v-slot="{ isActive, navigate }">
                    <div @click="navigate" :class="isActive
                        ? 'flexStart gap-x-2 p-5 lg:pl-12 bold-13 sm:text-sm! cursor-pointer h-10 bg-secondary/10 max-md:border-b-4 md:border-r-4 border-secondary'
                        : 'flexStart gap-x-2 lg:pl-12 p-5 bold-13 sm:text-sm! cursor-pointer h-10 rounded-xl'">
                        <img :src="link.icon" :alt="t(`nav.${link.key}`)" class="hidden md:block" width="18" />
                        <div>{{ t(`nav.${link.key}`) }}</div>
                    </div>
                </RouterLink>
            </div>
        </div>

        <div
            class="hidden md:flex items-center gap-3 md:bg-primary border-t border-slate-900/15 rounded-b-xl p-2 pl-5 lg:pl-10 md:mt-10">
            <UserButton :appearance="userButtonAppearance" />
            <div class="text-sm font-semibold text-gray-800 capitalize flex-1">
                {{ user?.firstName }} {{ user?.lastName }}
            </div>
            <NotificationBell />
            <LanguageSwitcher />
        </div>
    </div>
</template>