<script setup>
import { ref, onMounted } from 'vue'
import { useAuth } from '@clerk/vue'
import { aiService } from '../services/aiService'
import ModelVersions from '../components/ai/ModelVersions.vue'
import VersionCompare from '../components/ai/VersionCompare.vue'

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
        error.value = e.message || 'Failed to load models'
    } finally {
        loading.value = false
    }
})
</script>

<template>
    <div class="p-6">
        <h1 class="text-2xl font-bold mb-6">MLOps Dashboard</h1>

        <div v-if="loading" class="text-center py-8 text-gray-500">Loading...</div>
        <div v-else-if="error" class="text-center py-8 text-red-500">{{ error }}</div>
        <template v-else>
            <div class="mb-6">
                <h2 class="text-lg font-semibold mb-3">Version Management</h2>
                <div v-if="!models.length" class="text-gray-400 text-sm">No models registered.</div>
                <div v-for="model in models" :key="model.name" class="mb-4">
                    <ModelVersions :model-name="model.name" />
                </div>
            </div>

            <div v-if="models.length">
                <h2 class="text-lg font-semibold mb-3">Version Comparison</h2>
                <VersionCompare :model-name="models[0].name" />
            </div>
        </template>
    </div>
</template>
