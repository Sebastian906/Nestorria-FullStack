<script setup>
import Sidebar from './components/Sidebar.vue'
</script>

<template>
  <div class="bg-linear-to-r from-[#F0FDF4] to-white min-h-screen">
    <div class="mx-auto max-w-360 flex flex-col md:flex-row">
      <Sidebar />
      <main class="flex-1 p-4 md:p-6">
        <RouterView />
      </main>
    </div>
  </div>
</template>
