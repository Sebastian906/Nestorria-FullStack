<script setup>
import { reactive, ref, computed } from 'vue'
import axios from 'axios'
import { useAuth } from '@clerk/vue'
import { useToast } from 'vue-toastification'

const props = defineProps({ parentId: { type: Number, default: null } })
const emit = defineEmits(['close', 'created'])

const toast = useToast()
const auth = useAuth()

// Mismo patrón que el autómata del backend: ^[a-z0-9]+(?:-[a-z0-9]+)*$
// (UX en el form; la garantía real la da el FiniteAutomaton en CategoryService)
const SLUG_RE = /^[a-z0-9]+(?:-[a-z0-9]+)*$/

const form = reactive({ name: '', slug: '', description: '' })
const loading = ref(false)
const serverError = ref('')

const slugValid = computed(() => form.slug === '' || SLUG_RE.test(form.slug))

const generateSlug = () => {
    form.slug = form.name
        .toLowerCase()
        .trim()
        .replace(/[^a-z0-9]+/g, '-')
        .replace(/^-+|-+$/g, '')
}

const handleSubmit = async () => {
    serverError.value = ''
    if (!form.name.trim()) {
        toast.error('El nombre es obligatorio')
        return
    }
    if (!SLUG_RE.test(form.slug)) {
        toast.error('Slug inválido: solo minúsculas, dígitos y guiones simples')
        return
    }

    loading.value = true
    try {
        const token = await auth.getToken.value()
        if (!token) {
            toast.error('Error de autenticación. Inicia sesión nuevamente.')
            return
        }
        await axios.post(
            '/api/categories',
            {
                name: form.name.trim(),
                slug: form.slug,
                description: form.description.trim() || null,
                parentId: props.parentId,
            },
            { headers: { Authorization: `Bearer ${token}` } }
        )
        toast.success('Categoría creada')
        emit('created')
    } catch (error) {
        // El backend rechaza con 400 si el slug no lo acepta el autómata
        serverError.value = error?.response?.data?.message || 'No se pudo crear la categoría'
        toast.error(serverError.value)
    } finally {
        loading.value = false
    }
}
</script>

<template>
    <div class="fixed inset-0 bg-black/50 flex items-center justify-center z-50" @click.self="emit('close')">
        <form class="bg-white rounded-lg shadow-lg p-6 w-full max-w-md space-y-4" @submit.prevent="handleSubmit">
            <div class="flex justify-between items-center">
                <h2 class="text-lg font-bold">
                    {{ parentId ? 'Nueva subcategoría' : 'Nueva categoría' }}
                </h2>
                <button type="button" class="text-gray-400 hover:text-gray-600" @click="emit('close')">
                    ✕
                </button>
            </div>

            <div>
                <label class="block text-sm font-medium mb-1">Nombre *</label>
                <input v-model="form.name" class="w-full border border-gray-300 rounded px-3 py-2"
                    placeholder="Ej: Apartamento" />
            </div>

            <div>
                <div class="flex items-center justify-between">
                    <label class="block text-sm font-medium mb-1">Slug *</label>
                    <button type="button" class="text-xs text-secondary hover:underline" @click="generateSlug">
                        Generar del nombre
                    </button>
                </div>
                <input v-model="form.slug" class="w-full border border-gray-300 rounded px-3 py-2"
                    :class="form.slug && !slugValid ? 'border-red-500' : ''" placeholder="Ej: apartamento" />
                <p v-if="form.slug && !slugValid" class="text-xs text-red-500 mt-1">
                    Solo minúsculas, dígitos y guiones simples entre segmentos
                </p>
            </div>

            <div>
                <label class="block text-sm font-medium mb-1">Descripción</label>
                <textarea v-model="form.description" class="w-full border border-gray-300 rounded px-3 py-2"
                    rows="2"></textarea>
            </div>

            <p v-if="serverError" class="text-sm text-red-500">{{ serverError }}</p>

            <div class="flex justify-end gap-2">
                <button type="button" class="px-4 py-2 border border-gray-300 rounded" @click="emit('close')">
                    Cancelar
                </button>
                <button type="submit"
                    class="px-4 py-2 bg-secondary text-white rounded hover:opacity-90 disabled:opacity-50"
                    :disabled="loading">
                    {{ loading ? 'Guardando...' : 'Guardar' }}
                </button>
            </div>
        </form>
    </div>
</template>