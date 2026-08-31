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

    async uploadDocument(file, token) {
        const formData = new FormData()
        formData.append('file', file)
        const { data } = await api.post('/api/ai/admin/rag/documents', formData, {
            headers: {
                Authorization: `Bearer ${token}`,
                'Content-Type': 'multipart/form-data'
            }
        })
        return data
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
    },

    async getModelVersions(modelName, token) {
        const { data } = await api.get(`/api/ai/admin/models/${modelName}/versions`, {
            headers: { Authorization: `Bearer ${token}` }
        })
        return data
    },

    async getActiveVersion(modelName, token) {
        const { data } = await api.get(`/api/ai/admin/models/${modelName}/active`, {
            headers: { Authorization: `Bearer ${token}` }
        })
        return data
    },

    async promoteModel(modelName, version, token) {
        const { data } = await api.post(`/api/ai/admin/models/${modelName}/promote/${version}`, null, {
            headers: { Authorization: `Bearer ${token}` }
        })
        return data
    },

    async rollbackModel(modelName, version, token) {
        const { data } = await api.post(`/api/ai/admin/models/${modelName}/rollback/${version}`, null, {
            headers: { Authorization: `Bearer ${token}` }
        })
        return data
    },

    async compareVersions(modelName, v1, v2, token) {
        const { data } = await api.get(`/api/ai/admin/models/${modelName}/compare`, {
            params: { v1, v2 },
            headers: { Authorization: `Bearer ${token}` }
        })
        return data
    }
}