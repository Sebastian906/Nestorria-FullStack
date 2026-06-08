<script setup>
import Sidebar from './components/Sidebar.vue'
</script>

<template>
  <div>
    <Sidebar />
    <main>
      <RouterView />
    </main>
  </div>
</template>
