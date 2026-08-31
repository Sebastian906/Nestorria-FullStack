<script setup>
import { ref } from 'vue'
import { useAuth } from '@clerk/vue'
import { aiService } from '../../services/aiService'

const props = defineProps({
    models: { type: Array, required: true }
})

const { getToken } = useAuth()
const selectedModel = ref('')
const isTraining = ref(false)
const trainingResult = ref(null)

async function startTraining() {
    if (!selectedModel.value) return
    isTraining.value = true
    trainingResult.value = null
    try {
        const token = await getToken.value()
        const result = await aiService.triggerTraining(selectedModel.value, token)
        trainingResult.value = result
    } catch (e) {
        trainingResult.value = { status: 'error', error: e.message }
    } finally {
        isTraining.value = false
    }
}
</script>

<template>
    <div class="border rounded-lg p-4 bg-white shadow-sm">
        <h3 class="font-semibold mb-3">Training</h3>
        <div class="flex gap-3 items-center">
            <select v-model="selectedModel" class="border rounded px-3 py-2">
                <option value="" disabled>Select model</option>
                <option v-for="m in models" :key="m.name" :value="m.name">
                    {{ m.name }}
                </option>
            </select>
            <button @click="startTraining" :disabled="isTraining || !selectedModel"
                class="bg-blue-600 text-white px-4 py-2 rounded disabled:opacity-50">
                {{ isTraining ? 'Starting...' : 'Start Training' }}
            </button>
        </div>
        <div v-if="trainingResult" class="mt-3 text-sm">
            <span v-if="trainingResult.status === 'started'" class="text-green-600">
                Job {{ trainingResult.jobId }} started
            </span>
            <span v-else class="text-red-600">{{ trainingResult.error }}</span>
        </div>
    </div>
</template>