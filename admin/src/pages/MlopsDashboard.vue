<script setup>
import { ref, onMounted } from 'vue'
import { useAuth } from '@clerk/vue'
import { useI18n } from 'vue-i18n'
import { serverMsg } from '../utils/serverMsg.js'
import { aiService } from '../services/aiService'
import ModelVersions from '../components/ai/ModelVersions.vue'
import VersionCompare from '../components/ai/VersionCompare.vue'

const { getToken } = useAuth()
const { t } = useI18n()
const models = ref([])
const loading = ref(true)
const error = ref(null)
const selectedModel = ref('')

onMounted(async () => {
    try {
        const token = await getToken.value()
        const data = await aiService.getModels(token)
        models.value = data.models || []
        if (models.value.length) selectedModel.value = models.value[0].name
    } catch (e) {
        error.value = serverMsg(e, 'ai.mlops.loadFail')
    } finally {
        loading.value = false
    }
})
</script>

<template>
    <div class="p-6">
        <h1 class="text-2xl font-bold mb-6">{{ t('ai.mlops.title') }}</h1>

        <div v-if="loading" class="text-center py-8 text-gray-500">{{ t('ai.mlops.loading') }}</div>
        <div v-else-if="error" class="text-center py-8 text-red-500">{{ error }}</div>
        <template v-else>
            <div class="mb-6">
                <h2 class="text-lg font-semibold mb-3">{{ t('ai.mlops.management') }}</h2>
                <div v-if="!models.length" class="text-gray-400 text-sm">{{ t('ai.mlops.empty') }}</div>
                <div v-for="model in models" :key="model.name" class="mb-4">
                    <ModelVersions :model-name="model.name" />
                </div>
            </div>

            <div v-if="models.length">
                <h2 class="text-lg font-semibold mb-3">{{ t('ai.mlops.comparison') }}</h2>
                <select v-model="selectedModel" class="border rounded px-2 py-1.5 text-sm mb-3">
                    <option v-for="m in models" :key="m.name" :value="m.name">{{ m.name }}</option>
                </select>
                <VersionCompare :model-name="selectedModel" />
            </div>
        </template>
    </div>
</template>
