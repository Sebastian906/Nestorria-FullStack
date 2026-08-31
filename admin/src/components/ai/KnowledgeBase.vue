<script setup>
import { ref, onMounted } from 'vue'
import { useAuth } from '@clerk/vue'
import { aiService } from '../../services/aiService'

const { getToken } = useAuth()
const documents = ref([])
const loading = ref(true)
const uploading = ref(false)
const error = ref(null)

onMounted(async () => {
    await loadDocuments()
})

async function loadDocuments() {
    loading.value = true
    error.value = null
    try {
        const token = await getToken.value()
        const data = await aiService.getDocuments(token)
        documents.value = data.documents || []
    } catch (e) {
        console.error('Failed to load documents', e)
        error.value = 'Failed to load documents'
    } finally {
        loading.value = false
    }
}

async function uploadDocument(event) {
    const file = event.target.files[0]
    if (!file) return
    uploading.value = true
    error.value = null
    try {
        const token = await getToken.value()
        await aiService.uploadDocument(file, token)
        await loadDocuments()
    } catch (e) {
        console.error('Upload failed', e)
        error.value = 'Upload failed'
    } finally {
        uploading.value = false
    }
}

async function deleteDoc(docId) {
    if (!confirm('Delete this document?')) return
    try {
        const token = await getToken.value()
        await aiService.deleteDocument(docId, token)
        documents.value = documents.value.filter(d => d.id !== docId)
    } catch (e) {
        console.error('Delete failed', e)
    }
}
</script>

<template>
    <div class="border rounded-lg p-4 bg-white shadow-sm">
        <h3 class="font-semibold mb-3">Knowledge Base</h3>

        <div v-if="loading" class="text-gray-500">Loading documents...</div>

        <div v-else-if="error" class="text-red-500 text-sm">{{ error }}</div>

        <div v-else>
            <div v-if="documents.length === 0" class="text-gray-400 text-sm">
                No documents ingested yet.
            </div>
            <div v-for="doc in documents" :key="doc.id" class="flex justify-between items-center py-2 border-b last:border-0">
                <div>
                    <span class="font-medium">{{ doc.name }}</span>
                    <span class="text-sm text-gray-500 ml-2">{{ doc.chunks }} chunks</span>
                </div>
                <button @click="deleteDoc(doc.id)" class="text-red-500 text-sm hover:underline">
                    Delete
                </button>
            </div>

            <div class="mt-4">
                <label class="block text-sm text-gray-600 mb-1">Upload document</label>
                <input type="file" @change="uploadDocument" class="text-sm" :disabled="uploading" />
            </div>
        </div>
    </div>
</template>