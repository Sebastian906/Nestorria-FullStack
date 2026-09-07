<script setup>
import { ref, onMounted } from 'vue'
import { useAuth } from '@clerk/vue'
import { useI18n } from 'vue-i18n'
import { serverMsg } from '../../utils/serverMsg.js'
import { aiService } from '../../services/aiService'

const { getToken } = useAuth()
const { t } = useI18n()
const metrics = ref(null)
const loading = ref(true)
const error = ref(null)

onMounted(async () => {
    try {
        const token = await getToken.value()
        metrics.value = await aiService.getChatMetrics(token)
    } catch (e) {
        console.error('Failed to load chat metrics', e)
        error.value = serverMsg(e, 'ai.metrics.loadFail')
    } finally {
        loading.value = false
    }
})
</script>

<template>
    <div class="border rounded-lg p-4 bg-white shadow-sm">
        <h3 class="font-semibold mb-3">{{ t('ai.metrics.title') }}</h3>

        <div v-if="loading" class="text-gray-500">{{ t('ai.metrics.loading') }}</div>
        <div v-else-if="error" class="text-red-500 text-sm">{{ error }}</div>
        <div v-else-if="metrics" class="grid grid-cols-2 md:grid-cols-4 gap-4">
            <div class="text-center">
                <p class="text-2xl font-bold">{{ metrics.totalMessages }}</p>
                <p class="text-xs text-gray-500">{{ t('ai.metrics.total') }}</p>
            </div>
            <div class="text-center">
                <p class="text-2xl font-bold">{{ metrics.messagesByUser ? Object.keys(metrics.messagesByUser).length : 0 }}</p>
                <p class="text-xs text-gray-500">{{ t('ai.metrics.users') }}</p>
            </div>
            <div class="text-center">
                <p class="text-2xl font-bold">{{ metrics.averageResponseTime.toFixed(1) }}s</p>
                <p class="text-xs text-gray-500">{{ t('ai.metrics.avg') }}</p>
            </div>
            <div class="text-center">
                <p class="text-2xl font-bold">{{ (metrics.errorRate * 100).toFixed(1) }}%</p>
                <p class="text-xs text-gray-500">{{ t('ai.metrics.errors') }}</p>
            </div>
        </div>
    </div>
</template>