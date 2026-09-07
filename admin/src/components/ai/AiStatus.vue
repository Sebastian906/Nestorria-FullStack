<script setup>
import { ref, onMounted } from 'vue'
import { useAuth } from '@clerk/vue'
import { useI18n } from 'vue-i18n'
import { aiService } from '../../services/aiService'

const { getToken } = useAuth()
const { t } = useI18n()
const status = ref(null)
const loading = ref(true)

onMounted(async () => {
    try {
        const token = await getToken.value()
        status.value = await aiService.getStatus(token)
    } catch (e) {
        status.value = { status: 'error' }
    } finally {
        loading.value = false
    }
})
</script>

<template>
    <div class="border rounded-lg p-4 bg-white shadow-sm mb-6">
        <div class="flex items-center gap-3">
            <div class="w-3 h-3 rounded-full" :class="status?.status === 'ok' ? 'bg-green-500' : 'bg-red-500'" />
            <h3 class="font-semibold">{{ t('ai.status.title') }}</h3>
        </div>
        <div v-if="loading" class="text-gray-500 text-sm mt-2">{{ t('ai.status.checking') }}</div>
        <div v-else-if="status" class="text-sm mt-2 space-y-1">
            <p>{{ t('ai.status.status') }}: <span class="font-medium">{{ status.status }}</span></p>
            <p>{{ t('ai.status.models') }}: {{ status.modelsLoaded?.join(', ') || t('ai.status.none') }}</p>
            <p>{{ t('ai.status.rag') }}: {{ status.ragEnabled ? t('ai.status.enabled') : t('ai.status.disabled') }}</p>
            <p>{{ t('ai.status.llm') }}: {{ status.llmEnabled ? t('ai.status.enabled') : t('ai.status.disabled') }}</p>
        </div>
    </div>
</template>