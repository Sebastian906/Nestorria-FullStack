<script setup>
import { reactive, ref, onBeforeUnmount } from 'vue'
import { useAuth } from '@clerk/vue'
import axios from 'axios'
import { useToast } from 'vue-toastification'

const toast = useToast()
const { getToken } = useAuth()

const imageKeys = [1, 2, 3, 4]

const images = reactive({ 1: null, 2: null, 3: null, 4: null })
const previews = reactive({ 1: null, 2: null, 3: null, 4: null })

// Enum values must match Java's PropertyType exactly
const propertyTypes = [
    { label: 'House', value: 'HOUSE' },
    { label: 'Apartment', value: 'APARTMENT' },
    { label: 'Villa', value: 'VILLA' },
    { label: 'Penthouse', value: 'PENTHOUSE' },
    { label: 'Townhouse', value: 'TOWNHOUSE' },
    { label: 'Commercial', value: 'COMMERCIAL' },
    { label: 'Land Plot', value: 'LAND_PLOT' },
]

const defaultAmenities = () => ({
    Backyard: false,
    Balcony: false,
    'Fitness Center': false,
    Fireplace: false,
    Garden: false,
    Garage: false,
    'High-Speed Internet': false,
    Parking: false,
    'Private Beach': false,
    'Swimming Pool': false,
    Terrace: false,
    'Wi-Fi': false,
})

const inputs = reactive({
    title: '',
    description: '',
    city: '',
    country: '',
    address: '',
    area: '',
    propertyType: '',
    priceRent: '',
    priceSale: '',
    bedrooms: '',
    bathrooms: '',
    garages: '',
    amenities: defaultAmenities(),
})

const loading = ref(false)

const resetForm = () => {
    Object.assign(inputs, {
        title: '', description: '', city: '', country: '',
        address: '', area: '', propertyType: '',
        priceRent: '', priceSale: '',
        bedrooms: '', bathrooms: '', garages: '',
        amenities: defaultAmenities(),
    })
    imageKeys.forEach((key) => {
        if (previews[key]) URL.revokeObjectURL(previews[key])
        images[key] = null
        previews[key] = null
    })
}

const handleImageChange = (key, event) => {
    const file = event.target.files[0]
    if (!file) return
    if (previews[key]) URL.revokeObjectURL(previews[key])
    images[key] = file
    previews[key] = URL.createObjectURL(file)
    event.target.value = ''
}

onBeforeUnmount(() => {
    imageKeys.forEach((key) => {
        if (previews[key]) URL.revokeObjectURL(previews[key])
    })
})

const handleSubmit = async () => {

    const isBlank = (value) => value === '' || value === null || value === undefined

    // Validation
    if (
        !inputs.country || !inputs.address || isBlank(inputs.area) ||
        !inputs.propertyType || (isBlank(inputs.priceRent) && isBlank(inputs.priceSale)) ||
        isBlank(inputs.bedrooms) || isBlank(inputs.bathrooms)
    ) {
        toast.error('Please fill all required fields')
        return
    }

    const hasImage = imageKeys.some((key) => images[key] !== null)
    if (!hasImage) {
        toast.error('Please upload at least one image')
        return
    }

    loading.value = true
    try {
        const token = await getToken.value()
        if (!token) {
            toast.error('Authentication error. Please log in again.')
            return
        }

        // Backend expects @RequestPart("data") as a JSON string blob,
        // NOT flat FormData fields — serialize payload separately.
        const enabledAmenities = Object.keys(inputs.amenities).filter(
            (key) => inputs.amenities[key]
        )

        const payload = {
            title: inputs.title,
            description: inputs.description,
            city: inputs.city,
            country: inputs.country,
            address: inputs.address,
            area: Number(inputs.area),
            propertyType: inputs.propertyType,        // already UPPER_SNAKE_CASE
            priceRent: isBlank(inputs.priceRent) ? null : Number(inputs.priceRent),
            priceSale: isBlank(inputs.priceSale) ? null : Number(inputs.priceSale),
            bedrooms: Number(inputs.bedrooms),
            bathrooms: Number(inputs.bathrooms),
            garages: Number(inputs.garages) || 0,
            amenities: enabledAmenities,
        }

        const formData = new FormData()
        // Spring's @RequestPart("data") with consumes=MULTIPART_FORM_DATA
        // deserializes this part as JSON when Content-Type is application/json
        formData.append(
            'data',
            new Blob([JSON.stringify(payload)], { type: 'application/json' })
        )

        imageKeys.forEach((key) => {
            if (images[key]) formData.append('images', images[key])
        })

        // axios instance already has baseURL from useAppContext module-level setup
        await axios.post('/api/properties', formData, {
            headers: { Authorization: `Bearer ${token}` },
            // Do NOT set Content-Type manually — let the browser set multipart boundary
        })

        toast.success('Property added successfully')
        resetForm()
    } catch (error) {
        const message =
            error.response?.data?.message || error.message || 'Something went wrong'
        toast.error(message)
    } finally {
        loading.value = false
    }
}
</script>

<template>
    <div class="md:px-8 py-6 xl:py-8 m-1.5 sm:m-3 h-[97vh] overflow-y-scroll lg:w-11/12 bg-white shadow rounded-xl">
        <form @submit.prevent="handleSubmit" class="flex flex-col gap-y-3.5 px-2 text-sm xl:max-w-3xl">

            <div class="w-full">
                <h5 class="h5">Property Name</h5>
                <input v-model="inputs.title" type="text" placeholder="Type here..."
                    class="px-3 py-1.5 ring-1 ring-slate-900/10 rounded-lg bg-secondary/5 mt-1 w-full" />
            </div>

            <div class="w-full">
                <h5 class="h5">Property Description</h5>
                <textarea v-model="inputs.description" rows="5" placeholder="Type here..."
                    class="px-3 py-1.5 ring-1 ring-slate-900/10 rounded-lg bg-secondary/5 mt-1 w-full" />
            </div>

            <div class="flex gap-4">
                <div class="w-full">
                    <h5 class="h5">City</h5>
                    <input v-model="inputs.city" type="text" placeholder="Type here..."
                        class="px-3 py-1.5 ring-1 ring-slate-900/10 rounded-lg bg-secondary/5 mt-1 w-full" />
                </div>
                <div class="w-full">
                    <h5 class="h5">Country</h5>
                    <input v-model="inputs.country" type="text" placeholder="Type here..."
                        class="px-3 py-1.5 ring-1 ring-slate-900/10 rounded-lg bg-secondary/5 mt-1 w-full" />
                </div>
                <div>
                    <h5 class="h5">Property Type</h5>
                    <select v-model="inputs.propertyType"
                        class="w-36 px-3 py-2 ring-1 ring-slate-900/10 rounded-lg bg-secondary/5 mt-1">
                        <option value="">Select Type</option>
                        <option v-for="type in propertyTypes" :key="type.value" :value="type.value">
                            {{ type.label }}
                        </option>
                    </select>
                </div>
            </div>

            <div class="flex gap-4 flex-wrap w-full">
                <div class="flex-1">
                    <h5 class="h5">Address</h5>
                    <input v-model="inputs.address" type="text" placeholder="Type here..."
                        class="px-3 py-1.5 ring-1 ring-slate-900/10 rounded-lg bg-secondary/5 mt-1 w-full" />
                </div>
                <div class="w-32">
                    <h5 class="h5">Area</h5>
                    <input v-model.number="inputs.area" type="number" placeholder="Area (sq ft)" min="1"
                        class="px-3 py-1.5 ring-1 ring-slate-900/10 rounded-lg bg-secondary/5 mt-1 w-full" />
                </div>
            </div>

            <div class="flex gap-4 flex-wrap">
                <div>
                    <h5 class="h5">Rent Price <span class="text-xs">/night</span></h5>
                    <input v-model.number="inputs.priceRent" type="number" placeholder="100" min="0"
                        class="px-3 py-1.5 ring-1 ring-slate-900/10 rounded-lg bg-secondary/5 mt-1 w-28" />
                </div>
                <div>
                    <h5 class="h5">Sale Price</h5>
                    <input v-model.number="inputs.priceSale" type="number" placeholder="9999" min="0"
                        class="px-3 py-1.5 ring-1 ring-slate-900/10 rounded-lg bg-secondary/5 mt-1 w-28" />
                </div>
                <div>
                    <h5 class="h5">Bedrooms</h5>
                    <input v-model.number="inputs.bedrooms" type="number" placeholder="1" min="0"
                        class="px-3 py-1.5 ring-1 ring-slate-900/10 rounded-lg bg-secondary/5 mt-1 w-20" />
                </div>
                <div>
                    <h5 class="h5">Bathrooms</h5>
                    <input v-model.number="inputs.bathrooms" type="number" placeholder="1" min="0"
                        class="px-3 py-1.5 ring-1 ring-slate-900/10 rounded-lg bg-secondary/5 mt-1 w-20" />
                </div>
                <div>
                    <h5 class="h5">Garages</h5>
                    <input v-model.number="inputs.garages" type="number" placeholder="0" min="0"
                        class="px-3 py-1.5 ring-1 ring-slate-900/10 rounded-lg bg-secondary/5 mt-1 w-20" />
                </div>
            </div>

            <!-- AMENITIES -->
            <div>
                <h5 class="h5">Amenities</h5>
                <div class="flex gap-3 flex-wrap mt-1">
                    <div v-for="(_, amenity) in inputs.amenities" :key="amenity" class="flex gap-1">
                        <input :id="`amenity-${amenity}`" v-model="inputs.amenities[amenity]" type="checkbox" />
                        <label :for="`amenity-${amenity}`">{{ amenity }}</label>
                    </div>
                </div>
            </div>

            <!-- IMAGES -->
            <div class="flex gap-2 mt-2">
                <label v-for="imgKey in imageKeys" :key="imgKey" :for="`propertyImage${imgKey}`"
                    class="ring-1 ring-slate-900/10 overflow-hidden rounded-lg cursor-pointer">
                    <input :id="`propertyImage${imgKey}`" type="file" accept="image/*" hidden
                        @change="(e) => handleImageChange(imgKey, e)" />
                    <div class="h-12 w-24 bg-secondary/5 flexCenter">
                        <img v-if="previews[imgKey]" :src="previews[imgKey]" alt="Property preview"
                            class="h-full w-full object-contain" />
                        <svg v-else viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5"
                            class="w-5 h-5 text-slate-400">
                            <path stroke-linecap="round" stroke-linejoin="round"
                                d="M12 16V4m0 0L8 8m4-4l4 4M4 16v2a2 2 0 002 2h12a2 2 0 002-2v-2" />
                        </svg>
                    </div>
                </label>
            </div>

            <button type="submit" :disabled="loading"
                class="btn-secondary text-black font-semibold mt-3 p-2 max-w-36 sm:w-full rounded-xl disabled:opacity-50">
                {{ loading ? 'Adding...' : 'Add Property' }}
            </button>
        </form>
    </div>
</template>