<script setup>
import { ref, watch, onMounted } from 'vue'
import { useAppContext } from '../composables/useAppContext'
import { useToast } from 'vue-toastification'
import axios from 'axios'

const { currency, roleLoaded, auth } = useAppContext()
const toast = useToast()

const properties = ref([])
const loading = ref(false)
const togglingId = ref(null)

const getToken = () => auth.getToken.value()

const fetchProperties = async () => {
    loading.value = true
    try {
        const token = await getToken()
        const { data } = await axios.get('/api/properties/owner', {
            headers: { Authorization: `Bearer ${token}` }
        })
        properties.value = data
    } catch (error) {
        toast.error('No se pudieron cargar las propiedades')
        console.error(error)
    } finally {
        loading.value = false
    }
}

const toggleAvailability = async (propertyId) => {
    if (togglingId.value === propertyId) return
    togglingId.value = propertyId
    try {
        const token = await getToken()
        await axios.patch(`/api/properties/${propertyId}/availability`, null, {
            headers: { Authorization: `Bearer ${token}` }
        })
        await fetchProperties()
    } catch (error) {
        toast.error('No se pudo cambiar la disponibilidad')
        console.error(error)
    } finally {
        togglingId.value = null
    }
}

onMounted(() => {
    if (roleLoaded.value) {
        fetchProperties()
        return
    }
    const stop = watch(roleLoaded, (loaded) => {
        if (loaded) {
            stop()
            fetchProperties()
        }
    })
})
</script>

<template>
    <div class="md:px-8 py-6 xl:py-8 m-1 sm:m-3 h-[97vh] overflow-y-scroll lg:w-11/12 bg-white shadow rounded-xl">

        <div v-if="loading" class="flex justify-center items-center h-40 text-gray-400 text-sm">
            Loading properties…
        </div>

        <div v-else-if="properties.length === 0" class="flex justify-center items-center h-40 text-gray-400 text-sm">
            No properties found. Add your first property.
        </div>

        <div v-else>
            <div
                class="flex justify-between flex-wrap gap-2 sm:grid grid-cols-[2fr_2fr_1fr_1fr] lg:grid-cols-[0.5fr_2fr_2fr_1fr_1fr] px-6 py-3 bg-secondary border-b border-slate-900/15 rounded-t-xl">
                <h5 class="h5 hidden lg:block">Index</h5>
                <h5 class="h5">Name</h5>
                <h5 class="h5">Address</h5>
                <h5 class="h5">Price</h5>
                <h5 class="h5">Available</h5>
            </div>

            <div v-for="(property, index) in properties" :key="property.id"
                class="flex justify-between items-center flex-wrap gap-2 sm:grid grid-cols-[2fr_2fr_1fr_1fr] lg:grid-cols-[0.5fr_2fr_2fr_1fr_1fr] px-6 py-3 bg-secondary/5 text-gray-50 medium-14 font-semibold border-b border-slate-900/15">
                <div class="hidden lg:block">{{ index + 1 }}</div>

                <div class="flexStart gap-x-2 max-w-64">
                    <div class="overflow-hidden rounded-lg">
                        <img :src="property.images?.[0]" :alt="property.title" class="w-16 rounded-lg object-cover" />
                    </div>
                    <div class="line-clamp-2">{{ property.title }}</div>
                </div>

                <div class="line-clamp-2">{{ property.address }}</div>

                <div>{{ currency }}{{ property.price?.sale ?? '—' }}</div>

                <div>
                    <label class="relative inline-flex items-center cursor-pointer text-gray-900 gap-3">
                        <input type="checkbox" class="sr-only peer"
                            :checked="property.available ?? property.isAvailable" :disabled="togglingId === property.id"
                            @click.prevent="toggleAvailability(property.id)" />
                        <div class="w-10 h-6 rounded-full peer transition-colors duration-200" :class="togglingId === property.id
                            ? 'bg-slate-200'
                            : 'bg-slate-300 peer-checked:bg-secondary'" />
                        <span
                            class="absolute left-1 top-1 w-4 h-4 bg-white rounded-full transition-transform duration-200 ease-in-out peer-checked:translate-x-4" />
                    </label>
                </div>
            </div>
        </div>
    </div>
</template>