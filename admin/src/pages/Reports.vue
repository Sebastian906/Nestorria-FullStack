<script setup>
import { ref, onMounted, computed } from 'vue'
import { useAppContext } from '../composables/useAppContext'
import { useToast } from 'vue-toastification'
import { useI18n } from 'vue-i18n'
import { serverMsg } from '../utils/serverMsg.js'
import axios from 'axios'

const { auth, roleLoaded } = useAppContext()
const toast = useToast()
const { t } = useI18n()

// Estado
const loading = ref(false)
const selectedPeriod = ref('current-month')
const customStartDate = ref('')
const customEndDate = ref('')
const reportType = ref('bookings')

// Períodos predefinidos (traducidos, reactivos al idioma)
const periods = computed(() => [
    { value: 'current-month', label: t('reports.pCurrentMonth') },
    { value: 'previous-month', label: t('reports.pPreviousMonth') },
    { value: 'current-year', label: t('reports.pCurrentYear') },
    { value: 'previous-year', label: t('reports.pPreviousYear') },
    { value: 'last-3-months', label: t('reports.pLast3') },
    { value: 'last-6-months', label: t('reports.pLast6') },
    { value: 'custom', label: t('reports.pCustom') },
])

// Formatea Date a YYYY-MM-DD usando timezone local (no UTC)
const toLocalDateStr = (d) => {
    const y = d.getFullYear()
    const m = String(d.getMonth() + 1).padStart(2, '0')
    const day = String(d.getDate()).padStart(2, '0')
    return `${y}-${m}-${day}`
}

// Calcular fechas según período seleccionado
const getDateRange = () => {
    const now = new Date()
    const currentMonth = now.getMonth()
    const currentYear = now.getFullYear()
    
    switch (selectedPeriod.value) {
        case 'current-month': {
            return {
                startDate: `${currentYear}-${String(currentMonth + 1).padStart(2, '0')}-01`,
                endDate: `${currentYear}-${String(currentMonth + 1).padStart(2, '0')}-${new Date(currentYear, currentMonth + 1, 0).getDate()}`
            }
        }
        case 'previous-month': {
            const prevMonth = currentMonth === 0 ? 11 : currentMonth - 1
            const prevYear = currentMonth === 0 ? currentYear - 1 : currentYear
            return {
                startDate: `${prevYear}-${String(prevMonth + 1).padStart(2, '0')}-01`,
                endDate: `${prevYear}-${String(prevMonth + 1).padStart(2, '0')}-${new Date(prevYear, prevMonth + 1, 0).getDate()}`
            }
        }
        case 'current-year': {
            return {
                startDate: `${currentYear}-01-01`,
                endDate: `${currentYear}-12-31`
            }
        }
        case 'previous-year': {
            return {
                startDate: `${currentYear - 1}-01-01`,
                endDate: `${currentYear - 1}-12-31`
            }
        }
        case 'last-3-months': {
            const threeMonthsAgo = new Date(currentYear, currentMonth - 2, 1)
            return {
                startDate: toLocalDateStr(threeMonthsAgo),
                endDate: toLocalDateStr(now)
            }
        }
        case 'last-6-months': {
            const sixMonthsAgo = new Date(currentYear, currentMonth - 5, 1)
            return {
                startDate: toLocalDateStr(sixMonthsAgo),
                endDate: toLocalDateStr(now)
            }
        }
        case 'custom': {
            return {
                startDate: customStartDate.value,
                endDate: customEndDate.value
            }
        }
        default:
            return { startDate: null, endDate: null }
    }
}

// Descargar reporte
const downloadReport = async (format) => {
    loading.value = true
    
    try {
        const token = await auth.getToken.value()
        const { startDate, endDate } = getDateRange()
        
        let url = `/api/reports/${reportType.value}/${format}`
        const params = new URLSearchParams()
        
        if (reportType.value === 'bookings') {
            if (startDate) params.append('startDate', startDate)
            if (endDate) params.append('endDate', endDate)
        }
        
        if (params.toString()) {
            url += `?${params.toString()}`
        }
        
        const response = await axios.get(url, {
            headers: { Authorization: `Bearer ${token}` },
            responseType: 'blob'
        })
        
        // Crear enlace de descarga
        const blob = new Blob([response.data])
        const downloadUrl = window.URL.createObjectURL(blob)
        const link = document.createElement('a')
        link.href = downloadUrl
        
        // Nombre del archivo
        const fileExtension = format === 'xlsx' ? 'xlsx' : 'pdf'
        const reportName = reportType.value === 'bookings' ? 'bookings' : 'properties'
        const dateStr = toLocalDateStr(new Date())
        link.download = `${reportName}-report_${dateStr}.${fileExtension}`
        
        document.body.appendChild(link)
        link.click()
        document.body.removeChild(link)
        window.URL.revokeObjectURL(downloadUrl)
        
        toast.success(t('reports.downloaded', { format: format.toUpperCase() }))

    } catch (error) {
        console.error('Error downloading report:', error)
        toast.error(serverMsg(error, 'reports.errors.generic'))
    } finally {
        loading.value = false
    }
}

// Validar fechas personalizadas
const isCustomDateValid = () => {
    if (selectedPeriod.value !== 'custom') return true
    return customStartDate.value && customEndDate.value && 
           customStartDate.value <= customEndDate.value
}
</script>

<template>
    <div class="px-4 md:px-8 py-6 xl:py-8 m-1 sm:m-3 h-[97vh] overflow-y-scroll lg:w-11/12 bg-white shadow rounded-xl">
        <!-- Header -->
        <div class="mb-6">
            <h1 class="text-2xl font-bold text-gray-800 mb-2">{{ t('reports.title') }}</h1>
            <p class="text-gray-600">{{ t('reports.subtitle') }}</p>
        </div>

        <!-- Tipo de Reporte -->
        <div class="mb-6">
            <label class="block text-sm font-medium text-gray-700 mb-2">{{ t('reports.type') }}</label>
            <div class="flex gap-4">
                <button
                    @click="reportType = 'bookings'"
                    :class="reportType === 'bookings'
                        ? 'bg-blue-600 text-white'
                        : 'bg-gray-100 text-gray-700 hover:bg-gray-200'"
                    class="px-4 py-2 rounded-lg font-medium transition-colors"
                >
                    {{ t('reports.bookings') }}
                </button>
                <button
                    @click="reportType = 'properties'"
                    :class="reportType === 'properties'
                        ? 'bg-blue-600 text-white'
                        : 'bg-gray-100 text-gray-700 hover:bg-gray-200'"
                    class="px-4 py-2 rounded-lg font-medium transition-colors"
                >
                    {{ t('reports.properties') }}
                </button>
            </div>
        </div>

        <!-- Período -->
        <div class="mb-6">
            <label class="block text-sm font-medium text-gray-700 mb-2">{{ t('reports.period') }}</label>
            <select 
                v-model="selectedPeriod"
                class="w-full md:w-64 px-4 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-blue-500 focus:border-blue-500"
            >
                <option v-for="period in periods" :key="period.value" :value="period.value">
                    {{ period.label }}
                </option>
            </select>
        </div>

        <!-- Fechas Personalizadas -->
        <div v-if="selectedPeriod === 'custom'" class="mb-6 flex flex-wrap gap-4">
            <div>
                <label class="block text-sm font-medium text-gray-700 mb-2">{{ t('reports.from') }}</label>
                <input
                    v-model="customStartDate"
                    type="date"
                    class="px-4 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-blue-500 focus:border-blue-500"
                />
            </div>
            <div>
                <label class="block text-sm font-medium text-gray-700 mb-2">{{ t('reports.to') }}</label>
                <input
                    v-model="customEndDate"
                    type="date"
                    class="px-4 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-blue-500 focus:border-blue-500"
                />
            </div>
        </div>

        <!-- Botones de Descarga -->
        <div class="flex flex-wrap gap-4 mt-8">
            <!-- Botón XLSX (Verde) -->
            <button
                @click="downloadReport('xlsx')"
                :disabled="loading || !isCustomDateValid()"
                class="flex items-center gap-2 px-6 py-3 bg-green-600 text-white font-semibold rounded-lg hover:bg-green-700 disabled:opacity-50 disabled:cursor-not-allowed transition-colors shadow-md"
            >
                <svg xmlns="http://www.w3.org/2000/svg" class="h-5 w-5" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                    <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M9 17v-2m3 2v-4m3 4v-6m2 10H7a2 2 0 01-2-2V5a2 2 0 012-2h5.586a1 1 0 01.707.293l5.414 5.414a1 1 0 01.293.707V19a2 2 0 01-2 2z" />
                </svg>
                <span v-if="!loading">{{ t('reports.downloadXlsx') }}</span>
                <span v-else>{{ t('reports.downloading') }}</span>
            </button>

            <!-- Botón PDF (Rojo) -->
            <button
                @click="downloadReport('pdf')"
                :disabled="loading || !isCustomDateValid()"
                class="flex items-center gap-2 px-6 py-3 bg-red-600 text-white font-semibold rounded-lg hover:bg-red-700 disabled:opacity-50 disabled:cursor-not-allowed transition-colors shadow-md"
            >
                <svg xmlns="http://www.w3.org/2000/svg" class="h-5 w-5" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                    <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M7 21h10a2 2 0 002-2V9.414a1 1 0 00-.293-.707l-5.414-5.414A1 1 0 0012.586 3H7a2 2 0 00-2 2v14a2 2 0 002 2z" />
                </svg>
                <span v-if="!loading">{{ t('reports.downloadPdf') }}</span>
                <span v-else>{{ t('reports.downloading') }}</span>
            </button>
        </div>

        <!-- Info adicional -->
        <div class="mt-8 p-4 bg-green-100 rounded-lg">
            <h3 class="font-medium text-gray-800 mb-2">{{ t('reports.info') }}</h3>
            <ul class="text-sm text-gray-600 space-y-1">
                <li v-if="reportType === 'bookings'">
                    • {{ t('reports.includesBookings') }}
                </li>
                <li v-if="reportType === 'properties'">
                    • {{ t('reports.includesProperties') }}
                </li>
                <li>
                    • {{ t('reports.selectedPeriod') }} <span class="font-medium">{{ periods.find(p => p.value === selectedPeriod)?.label }}</span>
                </li>
                <li v-if="selectedPeriod === 'custom' && customStartDate && customEndDate">
                    • {{ t('reports.range', { from: customStartDate, to: customEndDate }) }}
                </li>
            </ul>
        </div>
    </div>
</template>
