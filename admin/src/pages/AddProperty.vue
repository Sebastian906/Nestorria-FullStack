<script setup>
import { reactive, ref, onBeforeUnmount } from 'vue'

const imageKeys = [1, 2, 3, 4]

const images = reactive({ 1: null, 2: null, 3: null, 4: null })
const previews = reactive({ 1: null, 2: null, 3: null, 4: null })

const inputs = reactive({
    title: '',
    description: '',
    city: '',
    country: '',
    address: '',
    area: '',
    propertyType: '',
    price: {
        rent: '',
        sale: '',
    },
    bedrooms: '',
    bathrooms: '',
    garages: '',
    amenities: {
        Parking: false,
        Wifi: false,
        Backyard: false,
        Terrace: false,
    },
})

const loading = ref(false)

const propertyTypes = [
    'House',
    'Apartment',
    'Villa',
    'Penthouse',
    'Townhouse',
    'Commercial',
    'Land Plot',
]

const handleImageChange = (key, event) => {
    const file = event.target.files[0]
    if (!file) return

    if (previews[key]) {
        URL.revokeObjectURL(previews[key])
    }

    images[key] = file
    previews[key] = URL.createObjectURL(file)
}

onBeforeUnmount(() => {
    imageKeys.forEach((key) => {
        if (previews[key]) URL.revokeObjectURL(previews[key])
    })
})

const handleSubmit = async () => {
    loading.value = true
    try {
        // TODO: reemplazar por la llamada real a la API cuando exista el backend
        console.log('Property payload:', JSON.parse(JSON.stringify(inputs)), images)
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
                <input
                    v-model="inputs.title"
                    type="text"
                    placeholder="Type here..."
                    class="px-3 py-1.5 ring-1 ring-slate-900/10 rounded-lg bg-secondary/5 mt-1 w-full"
                />
            </div>

            <div class="w-full">
                <h5 class="h5">Property Description</h5>
                <textarea
                    v-model="inputs.description"
                    rows="5"
                    placeholder="Type here..."
                    class="px-3 py-1.5 ring-1 ring-slate-900/10 rounded-lg bg-secondary/5 mt-1 w-full"
                />
            </div>

            <div class="flex gap-4">
                <div class="w-full">
                    <h5 class="h5">City</h5>
                    <input
                        v-model="inputs.city"
                        type="text"
                        placeholder="Type here..."
                        class="px-3 py-1.5 ring-1 ring-slate-900/10 rounded-lg bg-secondary/5 mt-1 w-full"
                    />
                </div>
                <div class="w-full">
                    <h5 class="h5">Country</h5>
                    <input
                        v-model="inputs.country"
                        type="text"
                        placeholder="Type here..."
                        class="px-3 py-1.5 ring-1 ring-slate-900/10 rounded-lg bg-secondary/5 mt-1 w-full"
                    />
                </div>
                <div>
                    <h5 class="h5">Property Type</h5>
                    <select
                        v-model="inputs.propertyType"
                        class="w-36 px-3 py-2 ring-1 ring-slate-900/10 rounded-lg bg-secondary/5 mt-1"
                    >
                        <option value="">Select Type</option>
                        <option v-for="type in propertyTypes" :key="type" :value="type">
                            {{ type }}
                        </option>
                    </select>
                </div>
            </div>

            <div class="flex gap-4 flex-wrap w-full">
                <div class="flex-1">
                    <h5 class="h5">Address</h5>
                    <input
                        v-model="inputs.address"
                        type="text"
                        placeholder="Type here..."
                        class="px-3 py-1.5 ring-1 ring-slate-900/10 rounded-lg bg-secondary/5 mt-1 w-full"
                    />
                </div>
                <div class="w-32">
                    <h5 class="h5">Area</h5>
                    <input
                        v-model.number="inputs.area"
                        type="number"
                        placeholder="Area (sq ft)"
                        class="px-3 py-1.5 ring-1 ring-slate-900/10 rounded-lg bg-secondary/5 mt-1 w-full"
                    />
                </div>
            </div>

            <div class="flex gap-4 flex-wrap">
                <div>
                    <h5 class="h5">Rent Price <span class="text-xs">/night</span></h5>
                    <input
                        v-model.number="inputs.price.rent"
                        type="number"
                        placeholder="100"
                        min="99"
                        class="px-3 py-1.5 ring-1 ring-slate-900/10 rounded-lg bg-secondary/5 mt-1 w-28"
                    />
                </div>
                <div>
                    <h5 class="h5">Sale Price</h5>
                    <input
                        v-model.number="inputs.price.sale"
                        type="number"
                        placeholder="9999"
                        min="9999"
                        class="px-3 py-1.5 ring-1 ring-slate-900/10 rounded-lg bg-secondary/5 mt-1 w-28"
                    />
                </div>
                <div>
                    <h5 class="h5">Bedrooms</h5>
                    <input
                        v-model.number="inputs.bedrooms"
                        type="number"
                        placeholder="1"
                        min="1"
                        class="px-3 py-1.5 ring-1 ring-slate-900/10 rounded-lg bg-secondary/5 mt-1 w-20"
                    />
                </div>
                <div>
                    <h5 class="h5">Bathrooms</h5>
                    <input
                        v-model.number="inputs.bathrooms"
                        type="number"
                        placeholder="1"
                        min="1"
                        class="px-3 py-1.5 ring-1 ring-slate-900/10 rounded-lg bg-secondary/5 mt-1 w-20"
                    />
                </div>
                <div>
                    <h5 class="h5">Garages</h5>
                    <input
                        v-model.number="inputs.garages"
                        type="number"
                        placeholder="1"
                        min="1"
                        class="px-3 py-1.5 ring-1 ring-slate-900/10 rounded-lg bg-secondary/5 mt-1 w-20"
                    />
                </div>
            </div>

            <!-- AMENITIES -->
            <div>
                <h5 class="h5">Amenities</h5>
                <div class="flex gap-3 flex-wrap mt-1">
                    <div
                        v-for="(_, amenity) in inputs.amenities"
                        :key="amenity"
                        class="flex gap-1"
                    >
                        <input
                            :id="`amenity-${amenity}`"
                            v-model="inputs.amenities[amenity]"
                            type="checkbox"
                        />
                        <label :for="`amenity-${amenity}`">{{ amenity }}</label>
                    </div>
                </div>
            </div>

            <!-- IMAGES -->
            <div class="flex gap-2 mt-2">
                <label
                    v-for="imgKey in imageKeys"
                    :key="imgKey"
                    :for="`propertyImage${imgKey}`"
                    class="ring-1 ring-slate-900/10 overflow-hidden rounded-lg"
                >
                    <input
                        :id="`propertyImage${imgKey}`"
                        type="file"
                        accept="image/*"
                        hidden
                        @change="(e) => handleImageChange(imgKey, e)"
                    />
                    <div class="h-12 w-24 bg-secondary/5 flexCenter">
                        <img
                            v-if="previews[imgKey]"
                            :src="previews[imgKey]"
                            alt="Property preview"
                            class="h-full w-full object-contain"
                        />
                        <svg
                            v-else
                            viewBox="0 0 24 24"
                            fill="none"
                            stroke="currentColor"
                            stroke-width="1.5"
                            class="w-5 h-5 text-slate-400"
                        >
                            <path
                                stroke-linecap="round"
                                stroke-linejoin="round"
                                d="M12 16V4m0 0L8 8m4-4l4 4M4 16v2a2 2 0 002 2h12a2 2 0 002-2v-2"
                            />
                        </svg>
                    </div>
                </label>
            </div>

            <button
                type="submit"
                :disabled="loading"
                class="btn-secondary text-black font-semibold mt-3 p-2 max-w-36 sm:w-full rounded-xl"
            >
                {{ loading ? 'Adding' : 'Add Property' }}
            </button>
        </form>
    </div>
</template>