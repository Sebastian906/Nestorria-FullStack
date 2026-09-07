<script setup>
import { ref, watch, onMounted } from 'vue'
import { useAppContext } from '../composables/useAppContext'
import { useToast } from 'vue-toastification'
import { useI18n } from 'vue-i18n'
import { serverMsg } from '../utils/serverMsg.js'
import { formatCurrency, formatDate } from '../utils/format.js'
import { assets } from '../assets/assets'
import axios from 'axios'

const { auth, roleLoaded, currency } = useAppContext()
const toast = useToast()
const { t } = useI18n()

const dashboardData = ref({
    bookings: [],
    totalBookings: 0,
    totalRevenue: 0,
})

const getDashboardData = async () => {
    try {
        const token = await auth.getToken.value()
        const { data } = await axios.get('/api/bookings/agency', {
            headers: { Authorization: `Bearer ${token}` }
        })
        dashboardData.value = data
    } catch (error) {
        toast.error(serverMsg(error, 'dashboard.errors.loadFail'))
        console.error(error)
    }
}

onMounted(() => {
    if (roleLoaded.value) {
        getDashboardData()
        return
    }
    const stop = watch(roleLoaded, (loaded) => {
        if (loaded) {
            stop()
            getDashboardData()
        }
    })
})
</script>

<template>
    <div
        class="md:px-8 pt-2 md:pt-6 pb-6 xl:py-8 m-1 sm:m-3 h-[97vh] overflow-y-scroll lg:w-11/12 bg-white shadow rounded-xl">
        <div class="grid grid-cols-2 gap-4">
            <div class="flexStart gap-7 p-5 bg-[#F0FDF4] lg:min-w-56 rounded-xl">
                <img :src="assets.house" alt="" class="hidden sm:flex w-8" />
                <div>
                    <h4 class="h4">{{ String(dashboardData.totalBookings).padStart(2, '0') }}</h4>
                    <h5 class="h5 text-secondary">{{ t('dashboard.totalSales') }}</h5>
                </div>
            </div>
            <div class="flexStart gap-7 p-5 bg-[#d1e8ff] lg:min-w-56 rounded-xl">
                <img :src="assets.dollar" alt="" class="hidden sm:flex w-8" />
                <div>
                    <h4 class="h4">{{ formatCurrency(dashboardData.totalRevenue, currency) }}</h4>
                    <h5 class="h5 text-secondary">{{ t('dashboard.totalEarnings') }}</h5>
                </div>
            </div>
        </div>

        <div class="mt-4">
            <div
                class="flex justify-between flex-wrap gap-2 sm:grid grid-cols-[2fr_2fr_1fr_1fr] lg:grid-cols-[0.5fr_2fr_2fr_1fr_1fr] px-6 py-3 bg-secondary border-b border-slate-900/15 rounded-t-xl">
                <h5 class="h5 hidden lg:block">{{ t('dashboard.table.index') }}</h5>
                <h5 class="h5">{{ t('dashboard.table.property') }}</h5>
                <h5 class="h5">{{ t('dashboard.table.dates') }}</h5>
                <h5 class="h5">{{ t('dashboard.table.amount') }}</h5>
                <h5 class="h5">{{ t('dashboard.table.status') }}</h5>
            </div>

            <div v-if="dashboardData.bookings.length === 0"
                class="flex justify-center items-center h-24 text-gray-400 text-sm">
                {{ t('dashboard.table.empty') }}
            </div>

            <div v-for="(booking, index) in dashboardData.bookings" :key="booking.id"
                class="flex justify-between items-center flex-wrap gap-2 sm:grid grid-cols-[2fr_2fr_1fr_1fr] lg:grid-cols-[0.5fr_2fr_2fr_1fr_1fr] px-6 py-3 bg-secondary/5 text-gray-50 medium-14 border-b border-slate-900/15">
                <div class="hidden lg:block">{{ index + 1 }}</div>

                <div class="flexStart gap-x-2 max-w-64">
                    <div class="overflow-hidden rounded-lg">
                        <img :src="booking.property.images?.[0]" :alt="booking.property.title"
                            class="w-16 rounded-lg" />
                    </div>
                    <div class="line-clamp-2">{{ booking.property.title }}</div>
                </div>

                <div>
                    {{ formatDate(booking.checkInDate) }}
                    →
                    {{ formatDate(booking.checkOutDate) }}
                </div>

                <div>{{ formatCurrency(booking.totalPrice, currency) }}</div>

                <button :class="(booking.paid ?? booking.isPaid)
                    ? 'bg-green-400/80 text-white border-green-500/30'
                    : 'bg-amber-100 text-red-500 border-amber-500/30'"
                    class="w-22 py-0.5 rounded-full text-xs border">
                    {{ (booking.paid ?? booking.isPaid) ? t('dashboard.table.completed') : t('dashboard.table.pending') }}
                </button>
            </div>
        </div>
    </div>
</template>