<script setup>
import { useI18n } from 'vue-i18n'
import { formatDate } from '../../utils/format.js'

const { t } = useI18n()

defineProps({
    model: { type: Object, required: true }
})
</script>

<template>
    <div class="border rounded-lg p-4 bg-white shadow-sm">
        <h3 class="font-semibold text-lg">{{ model.name }}</h3>
        <div class="text-sm text-gray-600 mt-2 space-y-1">
            <p>{{ t('ai.card.version') }}: {{ model.version }}</p>
            <p>{{ t('ai.card.status') }}:
                <span :class="model.status === 'active' ? 'text-green-600' : 'text-yellow-600'">
                    {{ model.status }}
                </span>
            </p>
        </div>
        <div v-if="model.metrics" class="mt-3 flex gap-3 text-sm">
            <span v-for="(value, key) in model.metrics" :key="key" class="bg-gray-100 px-2 py-1 rounded">
                {{ key }}: {{ value }}
            </span>
        </div>
        <p v-if="model.lastTrained" class="text-xs text-gray-400 mt-2">
            {{ t('ai.card.lastTrained') }} {{ formatDate(model.lastTrained) }}
        </p>
    </div>
</template>