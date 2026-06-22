import { ref, computed, watch } from 'vue'
import axios from 'axios'
import { useAuth, useUser } from '@clerk/vue'

axios.defaults.baseURL = import.meta.env.VITE_BACKEND_URL

const showAgencyReg = ref(false)
const properties = ref([])
const currency = ref(import.meta.env.VITE_CURRENCY || '$')

const userRole = ref(null)
const roleLoaded = ref(false)
let profileWatcherStarted = false

export function useAppContext() {
    const { user } = useUser()
    const auth = useAuth()

    const isOwner = computed(() => userRole.value === 'AGENCY_OWNER')

    if (!profileWatcherStarted) {
        profileWatcherStarted = true
        watch(user, async (newUser) => {
            if (!newUser) {
                userRole.value = null
                roleLoaded.value = false
                return
            }
            try {
                const token = await auth.getToken.value()
                if (!token) {
                    return
                }
                const { data } = await axios.get('/api/users/me', {
                    headers: { Authorization: `Bearer ${token}` }
                })
                userRole.value = data.role
            } catch (error) {
                console.error('No se pudo cargar el perfil del usuario', error)
            } finally {
                roleLoaded.value = true
            }
        }, { immediate: true })
    }

    const setProperties = (data) => {
        properties.value = data
    }

    return {
        user,
        isOwner,
        roleLoaded,
        currency,
        properties,
        showAgencyReg,
        setProperties
    }
}