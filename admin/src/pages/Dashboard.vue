<script setup>
import { ref, watch } from 'vue'
import { useAppContext } from '../composables/useAppContext'
import { assets } from '../assets/assets'
import { dummyDashboardData } from '../../../frontend/src/assets/data';

const { user, currency } = useAppContext()

const dashboardData = ref({
    bookings: [],
    totalBookings: 0,
    totalRevenue: 0,
})

const getDashboardData = () => {
    dashboardData.value = dummyDashboardData
}

watch(user, (newUser) => {
    if (newUser) getDashboardData()
}, { immediate: true })
</script>

<template>
    <div class="md:px-8 py-6 xl:py-8 m-1 sm:m-3 h-[97vh] overflow-y-scroll lg:w-11/12 bg-white shadow rounded-xl">
        <!-- STATS -->
        <div class="grid grid-cols-2 gap-4">
            <div class="flexStart gap-7 p-5 bg-[#F0FDF4] lg:min-w-56 rounded-xl">
                <img :src="assets.house" alt="" class="hidden sm:flex w-8" />
                <div>
                    <h4 class="h4">{{ String(dashboardData.totalBookings).padStart(2, '0') }}</h4>
                    <h5 class="h5 text-secondary">Total Sales</h5>
                </div>
            </div>
            <div class="flexStart gap-7 p-5 bg-[#d1e8ff] lg:min-w-56 rounded-xl">
                <img :src="assets.dollar" alt="" class="hidden sm:flex w-8" />
                <div>
                    <h4 class="h4">{{ currency }}{{ dashboardData.totalRevenue }}</h4>
                    <h5 class="h5 text-secondary">Total Earnings</h5>
                </div>
            </div>
        </div>

        <!-- LATEST BOOKINGS -->
        <div class="mt-4">
            <!-- TABLE HEADER -->
            <div
                class="flex justify-between flex-wrap gap-2 sm:grid grid-cols-[2fr_2fr_1fr_1fr] lg:grid-cols-[0.5fr_2fr_2fr_1fr_1fr] px-6 py-3 bg-secondary border-b border-slate-900/15 rounded-t-xl">
                <h5 class="h5 hidden lg:block">Index</h5>
                <h5 class="h5">Property</h5>
                <h5 class="h5">Booking dates</h5>
                <h5 class="h5">Amount</h5>
                <h5 class="h5">Status</h5>
            </div>

            <!-- TABLE ROWS -->
            <div>
                <div v-for="(booking, index) in dashboardData.bookings" :key="booking._id"
                    class="flex justify-between items-center flex-wrap gap-2 sm:grid grid-cols-[2fr_2fr_1fr_1fr] lg:grid-cols-[0.5fr_2fr_2fr_1fr_1fr] px-6 py-3 bg-secondary/5 text-gray-50 medium-14 border-b border-slate-900/15">
                    <div class="hidden lg:block">{{ index + 1 }}</div>

                    <div class="flexStart gap-x-2 max-w-64">
                        <div class="overflow-hidden rounded-lg">
                            <img :src="booking.property.images[0]" :alt="booking.property.title"
                                class="w-16 rounded-lg" />
                        </div>
                        <div class="line-clamp-2">{{ booking.property.title }}</div>
                    </div>

                    <div>
                        {{ new Date(booking.checkInDate).toLocaleDateString() }}
                        to
                        {{ new Date(booking.checkOutDate).toLocaleDateString() }}
                    </div>

                    <div>{{ currency }}{{ booking.totalPrice }}</div>

                    <button :class="booking.isPaid
                        ? 'bg-green-400/80 text-white border-green-500/30'
                        : 'bg-amber-100 text-red-500 border-amber-500/30'"
                        class="w-22 py-0.5 rounded-full text-xs border">
                        {{ booking.isPaid ? 'Completed' : 'Pending' }}
                    </button>
                </div>
            </div>
        </div>
    </div>
</template>