<script setup>
import { ref, onMounted } from 'vue'
import { useAuth } from '@clerk/vue'
import { aiService } from '../services/aiService'
import AiStatus from '../components/ai/AiStatus.vue'
import ModelCard from '../components/ai/ModelCard.vue'
import TrainingTrigger from '../components/ai/TrainingTrigger.vue'
import KnowledgeBase from '../components/ai/KnowledgeBase.vue'
import ChatMetrics from '../components/ai/ChatMetrics.vue'

const { getToken } = useAuth()
const models = ref([])
const loading = ref(true)
const error = ref(null)

onMounted(async () => {
    try {
        const token = await getToken.value()
        const data = await aiService.getModels(token)
        models.value = data.models || []
    } catch (e) {
        error.value = e.message || 'Failed to load AI data'
    } finally {
        loading.value = false
    }
})
</script>

<template>
    <div class="p-6">
        <h1 class="text-2xl font-bold mb-6">AI Dashboard</h1>

        <AiStatus />

        <div v-if="loading" class="text-center py-8 text-gray-500">Loading...</div>
        <div v-else-if="error" class="text-center py-8 text-red-500">{{ error }}</div>
        <template v-else>
            <div class="grid grid-cols-1 md:grid-cols-2 gap-4 mb-6">
                <ModelCard v-for="model in models" :key="model.name" :model="model" />
            </div>

            <TrainingTrigger :models="models" />

            <div class="mt-6">
                <KnowledgeBase />
            </div>

            <div class="mt-6">
                <ChatMetrics />
            </div>
        </template>
    </div>
</template>