<script setup>
import { RouterLink } from 'vue-router'

const links = [
    { to: '/dashboard', label: 'Dashboard' },
    { to: '/add-property', label: 'Add Property' },
    { to: '/list-property', label: 'List Property' },
]
</script>

<template>
    <div>
        Sidebar
    </div>
</template>