<script setup>
import { ref, onMounted, onBeforeUnmount, watch } from 'vue'

const props = defineProps({
    latitude: { type: Number, default: null },
    longitude: { type: Number, default: null },
})

const emit = defineEmits(['update:latitude', 'update:longitude', 'update:neighborhood', 'update:postalCode'])

const mapContainer = ref(null)
const loadingReverse = ref(false)
let map = null
let marker = null

// Coordenadas por defecto: Colombia
const DEFAULT_LAT = 4.5709
const DEFAULT_LNG = -74.2973

const initMap = async () => {
    if (!mapContainer.value || !window.L) return

    const L = window.L

    map = L.map(mapContainer.value, {
        center: [props.latitude || DEFAULT_LAT, props.longitude || DEFAULT_LNG],
        zoom: props.latitude ? 15 : 6,
    })

    L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
        attribution: '&copy; OpenStreetMap contributors',
    }).addTo(map)

    // Si ya hay coordenadas, colocar marcador
    if (props.latitude && props.longitude) {
        marker = L.marker([props.latitude, props.longitude]).addTo(map)
    }

    // Evento de clic para seleccionar ubicación
    map.on('click', async (e) => {
        const { lat, lng } = e.latlng

        // Actualizar o crear marcador
        if (marker) {
            marker.setLatLng([lat, lng])
        } else {
            marker = L.marker([lat, lng]).addTo(map)
        }

        // Emitir coordenadas
        emit('update:latitude', Math.round(lat * 1e6) / 1e6)
        emit('update:longitude', Math.round(lng * 1e6) / 1e6)

        // Reverse geocoding con Nominatim
        await reverseGeocode(lat, lng)
    })
}

const reverseGeocode = async (lat, lng) => {
    loadingReverse.value = true
    try {
        const response = await fetch(
            `https://nominatim.openstreetmap.org/reverse?format=jsonv2&lat=${lat}&lon=${lng}&addressdetails=1&accept-language=es`,
            {
                headers: {
                    'User-Agent': 'NestorriaAdmin/1.0',
                },
            }
        )
        const data = await response.json()

        if (data.address) {
            // Intentar obtener barrio de diferentes campos
            const neighborhood =
                data.address.neighbourhood ||
                data.address.suburb ||
                data.address.quarter ||
                data.address.city_district ||
                data.address.district ||
                data.address.village ||
                ''

            // Obtener código postal
            const postalCode = data.address.postcode || ''

            emit('update:neighborhood', neighborhood)
            emit('update:postalCode', postalCode)
        }
    } catch (error) {
        console.warn('Reverse geocoding failed:', error)
    } finally {
        loadingReverse.value = false
    }
}

// Cargar Leaflet dinámicamente
const loadLeaflet = () => {
    return new Promise((resolve) => {
        if (window.L) {
            resolve()
            return
        }
        const link = document.createElement('link')
        link.rel = 'stylesheet'
        link.href = 'https://cdnjs.cloudflare.com/ajax/libs/leaflet/1.7.1/leaflet.min.css'
        document.head.appendChild(link)

        const script = document.createElement('script')
        script.src = 'https://cdnjs.cloudflare.com/ajax/libs/leaflet/1.7.1/leaflet.min.js'
        script.onload = resolve
        document.head.appendChild(script)
    })
}

const clearLocation = () => {
    if (marker && map) {
        map.removeLayer(marker)
        marker = null
    }
    emit('update:latitude', null)
    emit('update:longitude', null)
    emit('update:neighborhood', '')
    emit('update:postalCode', '')
}

onMounted(async () => {
    await loadLeaflet()
    await initMap()
})

onBeforeUnmount(() => {
    if (map) {
        map.remove()
        map = null
    }
})

// Actualizar marcador si las coordenadas cambian externamente
watch(
    () => [props.latitude, props.longitude],
    ([lat, lng]) => {
        if (map && lat && lng && marker) {
            marker.setLatLng([lat, lng])
            map.setView([lat, lng], 15)
        }
    }
)
</script>

<template>
    <div>
        <div
            ref="mapContainer"
            class="w-full h-72 rounded-lg ring-1 ring-slate-900/10 z-0"
        ></div>
        <div class="flex items-center justify-between mt-2">
            <p v-if="loadingReverse" class="text-xs text-gray-400">
                Getting location data...
            </p>
            <p v-else-if="latitude && longitude" class="text-xs text-gray-500">
                {{ latitude?.toFixed(6) }}, {{ longitude?.toFixed(6) }}
            </p>
            <p v-else class="text-xs text-gray-400">
                Click on the map to get location
            </p>
            <button
                v-if="latitude && longitude"
                type="button"
                @click="clearLocation"
                class="text-xs text-red-400 hover:text-red-600 transition"
            >
                Clean
            </button>
        </div>
    </div>
</template>
