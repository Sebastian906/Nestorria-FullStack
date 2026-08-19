<script setup>
import { ref, onMounted } from 'vue'
import axios from 'axios'
import { useAuth } from '@clerk/vue'
import { useToast } from 'vue-toastification'
import CategoryTree from '../components/CategoryTree.vue'
import CategoryForm from '../components/CategoryForm.vue'

const toast = useToast()
const auth = useAuth()

const tree = ref([])
const loading = ref(false)
const showForm = ref(false)
const formParentId = ref(null)

const loadTree = async () => {
    loading.value = true
    try {
        const token = await auth.getToken.value()
        const { data } = await axios.get('/api/categories', {
            headers: { Authorization: `Bearer ${token}` },
        })
        tree.value = data
    } catch (error) {
        toast.error('No se pudieron cargar las categorías')
    } finally {
        loading.value = false
    }
}

const openCreate = (parentId) => {
    formParentId.value = parentId
    showForm.value = true
}

const onCreated = () => {
    showForm.value = false
    loadTree()
}

onMounted(loadTree)
</script>

<template>
    <div class="px-4 md:px-8 py-6 xl:py-8 m-1 sm:m-3 h-[97vh] overflow-y-scroll lg:w-11/12 bg-white shadow rounded-xl">
        <div class="flex items-center justify-between mb-6">
            <h1 class="text-2xl font-bold">Categorías</h1>
            <button class="px-4 py-2 bg-secondary text-white rounded hover:opacity-90" @click="openCreate(null)">
                Nueva categoría
            </button>
        </div>

        <p v-if="loading" class="text-gray-500">Cargando...</p>

        <CategoryTree v-else :nodes="tree" @create-sub="openCreate" />

        <CategoryForm v-if="showForm" :parent-id="formParentId" @close="showForm = false" @created="onCreated" />
    </div>
</template>