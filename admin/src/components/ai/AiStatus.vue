<script setup>
import { ref, onMounted } from 'vue'
import { useAuth } from '@clerk/vue'
import { aiService } from '../../services/aiService'

const { getToken } = useAuth()
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
            <h3 class="font-semibold">AI Service Status</h3>
        </div>
        <div v-if="loading" class="text-gray-500 text-sm mt-2">Checking...</div>
        <div v-else-if="status" class="text-sm mt-2 space-y-1">
            <p>Status: <span class="font-medium">{{ status.status }}</span></p>
            <p>Models: {{ status.modelsLoaded?.join(', ') || 'None' }}</p>
            <p>RAG: {{ status.ragEnabled ? 'Enabled' : 'Disabled' }}</p>
            <p>LLM: {{ status.llmEnabled ? 'Enabled' : 'Disabled' }}</p>
        </div>
    </div>
</template>