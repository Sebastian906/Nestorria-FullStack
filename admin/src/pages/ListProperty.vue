<script setup>
import { ref, watch } from 'vue'
import { useAppContext } from '../composables/useAppContext'
import { dummyProperties } from '../assets/assets'

const { user, currency } = useAppContext()

const properties = ref([])

const getProperties = () => {
    properties.value = dummyProperties
}

watch(user, (newUser) => {
    if (newUser) getProperties()
}, { immediate: true })

const toggleAvailability = (property) => {
    property.isAvailable = !property.isAvailable
}
</script>

<template>
    <div class="md:px-8 py-6 xl:py-8 m-1 sm:m-3 h-[97vh] overflow-y-scroll lg:w-11/12 bg-white shadow rounded-xl">
        <div>
            <div class="flex justify-between flex-wrap gap-2 sm:grid grid-cols-[2fr_2fr_1fr_1fr] lg:grid-cols-[0.5fr_2fr_2fr_1fr_1fr] px-6 py-3 bg-secondary border-b border-slate-900/15 rounded-t-xl">
                <h5 class="h5 hidden lg:block">Index</h5>
                <h5 class="h5">Name</h5>
                <h5 class="h5">Address</h5>
                <h5 class="h5">Price</h5>
                <h5 class="h5">Available</h5>
            </div>
            <div>
                <div
                    v-for="(property, index) in properties"
                    :key="property._id"
                    class="flex justify-between items-center flex-wrap gap-2 sm:grid grid-cols-[2fr_2fr_1fr_1fr] lg:grid-cols-[0.5fr_2fr_2fr_1fr_1fr] px-6 py-3 bg-secondary/5 text-gray-50 medium-14 font-semibold border-b border-slate-900/15"
                >
                    <div class="hidden lg:block">{{ index + 1 }}</div>
                    <div class="flexStart gap-x-2 max-w-64">
                        <div class="overflow-hidden rounded-lg">
                            <img :src="property.images[0]" :alt="property.title" class="w-16 rounded-lg" />
                        </div>
                        <div class="line-clamp-2">{{ property.title }}</div>
                    </div>
                    <div class="line-clamp-2">{{ property.address }}</div>
                    <div>{{ currency }}{{ property.price.sale }}</div>
                    <div>
                        <label class="relative inline-flex items-center cursor-pointer text-gray-900 gap-3">
                            <input
                                type="checkbox"
                                class="sr-only peer"
                                :checked="property.isAvailable"
                                @change="toggleAvailability(property)"
                            />
                            <div class="w-10 h-6 bg-slate-300 rounded-full peer peer-checked:bg-secondary transition-colors duration-200" />
                            <span class="absolute left-1 top-1 w-4 h-4 bg-white rounded-full transition-transform duration-200 ease-in-out peer-checked:translate-x-4" />
                        </label>
                    </div>
                </div>
            </div>
        </div>
    </div>
</template>