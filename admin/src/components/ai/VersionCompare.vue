<script setup>
import { ref, onMounted, watch } from 'vue'
import { useAuth } from '@clerk/vue'
import { useToast } from 'vue-toastification'
import { aiService } from '../../services/aiService'

const props = defineProps({
    modelName: { type: String, required: true }
})

const { getToken } = useAuth()
const toast = useToast()

const versions = ref([])
const v1 = ref('')
const v2 = ref('')
const result = ref(null)
const loading = ref(false)
const loadingVersions = ref(true)

const fetchVersions = async () => {
    loadingVersions.value = true
    try {
        const token = await getToken.value()
        const data = await aiService.getModelVersions(props.modelName, token)
        versions.value = data.versions || []
    } catch (e) {
        toast.error('Failed to load versions')
    } finally {
        loadingVersions.value = false
    }
}

const compare = async () => {
    if (!v1.value || !v2.value) {
        toast.warning('Select two versions to compare')
        return
    }
    loading.value = true
    result.value = null
    try {
        const token = await getToken.value()
        result.value = await aiService.compareVersions(props.modelName, v1.value, v2.value, token)
    } catch (e) {
        toast.error(e.response?.data?.detail || 'Comparison failed')
    } finally {
        loading.value = false
    }
}

onMounted(fetchVersions)
watch(() => props.modelName, () => {
    v1.value = ''
    v2.value = ''
    result.value = null
    fetchVersions()
})
</script>

<template>
    <div class="border rounded-lg p-4 bg-white shadow-sm">
        <h3 class="font-semibold mb-3">Compare Versions — {{ modelName }}</h3>

        <div v-if="loadingVersions" class="text-gray-500 text-sm">Loading versions...</div>

        <template v-else>
            <div class="flex flex-wrap items-end gap-3 mb-4">
                <div class="flex-1 min-w-[120px]">
                    <label class="block text-xs text-gray-500 mb-1">Version A</label>
                    <select v-model="v1" class="w-full border rounded px-2 py-1.5 text-sm">
                        <option value="" disabled>Select</option>
                        <option v-for="v in versions" :key="'a-' + v.version" :value="v.version">{{ v.version }}</option>
                    </select>
                </div>
                <div class="flex-1 min-w-[120px]">
                    <label class="block text-xs text-gray-500 mb-1">Version B</label>
                    <select v-model="v2" class="w-full border rounded px-2 py-1.5 text-sm">
                        <option value="" disabled>Select</option>
                        <option v-for="v in versions" :key="'b-' + v.version" :value="v.version">{{ v.version }}</option>
                    </select>
                </div>
                <button @click="compare" :disabled="loading || !v1 || !v2"
                    class="bg-blue-600 text-white text-sm px-4 py-1.5 rounded hover:bg-blue-700 disabled:opacity-50">
                    {{ loading ? 'Comparing...' : 'Compare' }}
                </button>
            </div>

            <div v-if="result" class="overflow-x-auto">
                <table class="w-full text-sm">
                    <thead>
                        <tr class="text-left text-gray-500 border-b">
                            <th class="pb-2 font-medium">Metric</th>
                            <th class="pb-2 font-medium text-right">v{{ result.version_1 }}</th>
                            <th class="pb-2 font-medium text-right">v{{ result.version_2 }}</th>
                            <th class="pb-2 font-medium text-right">Delta</th>
                        </tr>
                    </thead>
                    <tbody>
                        <tr v-for="(entry, metric) in result.metrics_comparison" :key="metric" class="border-b last:border-0">
                            <td class="py-2 font-mono text-xs">{{ metric }}</td>
                            <td class="py-2 text-right">{{ entry.v1 != null ? entry.v1.toFixed(4) : '—' }}</td>
                            <td class="py-2 text-right">{{ entry.v2 != null ? entry.v2.toFixed(4) : '—' }}</td>
                            <td class="py-2 text-right">
                                <span :class="entry.improved ? 'text-green-600' : entry.delta === 0 ? 'text-gray-400' : 'text-red-500'">
                                    {{ entry.delta > 0 ? '+' : '' }}{{ entry.delta?.toFixed(4) ?? '—' }}
                                </span>
                            </td>
                        </tr>
                    </tbody>
                </table>
            </div>
        </template>
    </div>
</template>
