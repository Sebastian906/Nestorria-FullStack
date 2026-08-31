import axios from 'axios'

const api = axios.create({
    baseURL: import.meta.env.VITE_BACKEND_URL || 'http://127.0.0.1:4000'
})

export const aiService = {
    async getModels(token) {
        const { data } = await api.get('/api/ai/admin/models', {
            headers: { Authorization: `Bearer ${token}` }
        })
        return data
    },

    async triggerTraining(modelName, token) {
        const { data } = await api.post(`/api/ai/admin/models/${modelName}/train`, null, {
            headers: { Authorization: `Bearer ${token}` }
        })
        return data
    },

    async getDocuments(token) {
        const { data } = await api.get('/api/ai/admin/rag/documents', {
            headers: { Authorization: `Bearer ${token}` }
        })
        return data
    },

    async deleteDocument(documentId, token) {
        await api.delete(`/api/ai/admin/rag/documents/${documentId}`, {
            headers: { Authorization: `Bearer ${token}` }
        })
    },

    async getChatMetrics(token) {
        const { data } = await api.get('/api/ai/admin/chat/metrics', {
            headers: { Authorization: `Bearer ${token}` }
        })
        return data
    },

    async getStatus(token) {
        const { data } = await api.get('/api/ai/admin/status', {
            headers: { Authorization: `Bearer ${token}` }
        })
        return data
    }
}