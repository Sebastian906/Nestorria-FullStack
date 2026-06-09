import { ref, computed } from 'vue'
import { useUser } from '@clerk/vue'

const showAgencyReg = ref(false)
const properties = ref([])
const currency = ref(import.meta.env.VITE_CURRENCY || '$')

export function useAppContext() {
    const { user } = useUser()

    const isOwner = computed(() => {
        return user.value?.publicMetadata?.role === 'agencyOwner' || false
    })

    const setProperties = (data) => {
        properties.value = data
    }

    return {
        user,
        isOwner,
        currency,
        properties,
        showAgencyReg,
        setProperties
    }
}