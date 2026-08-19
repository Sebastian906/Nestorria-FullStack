<script setup>
defineProps({ nodes: { type: Array, default: () => [] } })
defineEmits(['create-sub'])
</script>

<template>
    <ul v-if="nodes.length" class="space-y-2">
        <li v-for="node in nodes" :key="node.id" class="border border-gray-200 rounded p-3">
            <div class="flex items-center justify-between">
                <div>
                    <span class="font-medium">{{ node.name }}</span>
                    <span class="ml-2 text-xs text-gray-400">{{ node.slug }}</span>
                </div>
                <button class="text-sm text-secondary hover:underline" @click="$emit('create-sub', node.id)">
                    + Subcategoría
                </button>
            </div>
            <CategoryTree v-if="node.children && node.children.length" :nodes="node.children"
                class="mt-2 pl-4 border-l border-gray-200" @create-sub="(id) => $emit('create-sub', id)" />
        </li>
    </ul>
    <p v-else class="text-sm text-gray-400">Sin categorías todavía.</p>
</template>