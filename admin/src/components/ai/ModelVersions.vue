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
const activeVersion = ref(null)
const loading = ref(true)
const actionLoading = ref(null) // version being acted on

const fetchVersions = async () => {
    loading.value = true
    try {
        const token = await getToken.value()
        const [versionsData, activeData] = await Promise.all([
            aiService.getModelVersions(props.modelName, token),
            aiService.getActiveVersion(props.modelName, token).catch(() => null)
        ])
        versions.value = versionsData.versions || []
        activeVersion.value = activeData?.version || null
    } catch (e) {
        toast.error('Failed to load model versions')
    } finally {
        loading.value = false
    }
}

const promote = async (version) => {
    actionLoading.value = version
    try {
        const token = await getToken.value()
        const result = await aiService.promoteModel(props.modelName, version, token)
        activeVersion.value = result.new_version
        toast.success(`Promoted to v${version}`)
    } catch (e) {
        toast.error(e.response?.data?.detail || 'Promote failed')
    } finally {
        actionLoading.value = null
    }
}

const rollback = async (version) => {
    actionLoading.value = version
    try {
        const token = await getToken.value()
        const result = await aiService.rollbackModel(props.modelName, version, token)
        activeVersion.value = result.new_version
        toast.success(`Rolled back to v${version}`)
    } catch (e) {
        toast.error(e.response?.data?.detail || 'Rollback failed')
    } finally {
        actionLoading.value = null
    }
}

onMounted(fetchVersions)
watch(() => props.modelName, fetchVersions)
</script>

<template>
    <div class="border rounded-lg p-4 bg-white shadow-sm">
        <div class="flex items-center justify-between mb-3">
            <h3 class="font-semibold">Versions — {{ modelName }}</h3>
            <button @click="fetchVersions" class="text-xs text-blue-600 hover:underline">Refresh</button>
        </div>

        <div v-if="loading" class="text-gray-500 text-sm">Loading versions...</div>

        <div v-else-if="!versions.length" class="text-gray-400 text-sm">No versions found.</div>

        <table v-else class="w-full text-sm">
            <thead>
                <tr class="text-left text-gray-500 border-b">
                    <th class="pb-2 font-medium">Version</th>
                    <th class="pb-2 font-medium">Date</th>
                    <th class="pb-2 font-medium">Features</th>
                    <th class="pb-2 font-medium text-right">Actions</th>
                </tr>
            </thead>
            <tbody>
                <tr v-for="v in versions" :key="v.version" class="border-b last:border-0">
                    <td class="py-2">
                        <span class="font-mono">{{ v.version }}</span>
                        <span v-if="v.version === activeVersion"
                            class="ml-2 text-xs bg-green-100 text-green-700 px-1.5 py-0.5 rounded">active</span>
                    </td>
                    <td class="py-2 text-gray-500">
                        {{ v.date ? new Date(v.date).toLocaleDateString() : '—' }}
                    </td>
                    <td class="py-2 text-gray-500">
                        {{ v.features?.length ? v.features.length + ' cols' : '—' }}
                    </td>
                    <td class="py-2 text-right space-x-2">
                        <button v-if="v.version !== activeVersion" @click="promote(v.version)"
                            :disabled="actionLoading"
                            class="text-xs bg-green-600 text-white px-2 py-1 rounded hover:bg-green-700 disabled:opacity-50">
                            {{ actionLoading === v.version ? '...' : 'Promote' }}
                        </button>
                        <button v-if="v.version !== activeVersion" @click="rollback(v.version)"
                            :disabled="actionLoading"
                            class="text-xs bg-yellow-500 text-white px-2 py-1 rounded hover:bg-yellow-600 disabled:opacity-50">
                            {{ actionLoading === v.version ? '...' : 'Rollback' }}
                        </button>
                    </td>
                </tr>
            </tbody>
        </table>
    </div>
</template>
