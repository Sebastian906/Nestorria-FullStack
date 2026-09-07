<script setup>
import { ref, onMounted, watch } from 'vue'
import { useAuth } from '@clerk/vue'
import { useToast } from 'vue-toastification'
import { useI18n } from 'vue-i18n'
import { serverMsg } from '../../utils/serverMsg.js'
import { formatDate } from '../../utils/format.js'
import { aiService } from '../../services/aiService'

const props = defineProps({
    modelName: { type: String, required: true }
})

const { getToken } = useAuth()
const toast = useToast()
const { t } = useI18n()

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
        toast.error(serverMsg(e, 'ai.errors.loadFail'))
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
        toast.success(t('ai.success.promoted', { version }))
    } catch (e) {
        toast.error(serverMsg(e, 'ai.errors.promoteFail'))
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
        toast.success(t('ai.success.rolledBack', { version }))
    } catch (e) {
        toast.error(serverMsg(e, 'ai.errors.rollbackFail'))
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
            <h3 class="font-semibold">{{ t('ai.versions.title') }} — {{ modelName }}</h3>
            <button @click="fetchVersions" class="text-xs text-blue-600 hover:underline">{{ t('ai.versions.refresh') }}</button>
        </div>

        <div v-if="loading" class="text-gray-500 text-sm">{{ t('ai.versions.loading') }}</div>

        <div v-else-if="!versions.length" class="text-gray-400 text-sm">{{ t('ai.versions.empty') }}</div>

        <table v-else class="w-full text-sm">
            <thead>
                <tr class="text-left text-gray-500 border-b">
                    <th class="pb-2 font-medium">{{ t('ai.versions.version') }}</th>
                    <th class="pb-2 font-medium">{{ t('ai.versions.date') }}</th>
                    <th class="pb-2 font-medium">{{ t('ai.versions.features') }}</th>
                    <th class="pb-2 font-medium text-right">{{ t('ai.versions.actions') }}</th>
                </tr>
            </thead>
            <tbody>
                <tr v-for="v in versions" :key="v.version" class="border-b last:border-0">
                    <td class="py-2">
                        <span class="font-mono">{{ v.version }}</span>
                        <span v-if="v.version === activeVersion"
                            class="ml-2 text-xs bg-green-100 text-green-700 px-1.5 py-0.5 rounded">{{ t('ai.versions.active') }}</span>
                    </td>
                    <td class="py-2 text-gray-500">
                        {{ v.date ? formatDate(v.date) : '—' }}
                    </td>
                    <td class="py-2 text-gray-500">
                        {{ v.features?.length ? v.features.length + ' ' + t('ai.versions.cols') : '—' }}
                    </td>
                    <td class="py-2 text-right space-x-2">
                        <button v-if="v.version !== activeVersion" @click="promote(v.version)"
                            :disabled="actionLoading"
                            class="text-xs bg-green-600 text-white px-2 py-1 rounded hover:bg-green-700 disabled:opacity-50">
                            {{ actionLoading === v.version ? '...' : t('ai.versions.promote') }}
                        </button>
                        <button v-if="v.version !== activeVersion" @click="rollback(v.version)"
                            :disabled="actionLoading"
                            class="text-xs bg-yellow-500 text-white px-2 py-1 rounded hover:bg-yellow-600 disabled:opacity-50">
                            {{ actionLoading === v.version ? '...' : t('ai.versions.rollback') }}
                        </button>
                    </td>
                </tr>
            </tbody>
        </table>
    </div>
</template>
