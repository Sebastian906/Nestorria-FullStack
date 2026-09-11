<script setup>
import Sidebar from './components/Sidebar.vue'
import { SignIn, UserButton, useUser } from '@clerk/vue'
import { useAppContext } from './composables/useAppContext'
import { useI18n } from 'vue-i18n'

const { isAdmin, roleLoaded, user } = useAppContext()
const { isLoaded } = useUser()
const { t } = useI18n()
</script>

<template>
  <!-- Clerk aún cargando -->
  <div v-if="!isLoaded" class="min-h-screen flex items-center justify-center">
    <p class="text-sm text-gray-400">{{ t('common.auth.loading') }}</p>
  </div>

  <!-- Sin sesión: formulario prebuilt de Clerk (OAuth + todos los métodos activos en el dashboard) -->
  <div v-else-if="!user" class="min-h-screen flex flex-col items-center justify-center gap-4 bg-linear-to-r from-[#F0FDF4] to-white p-6">
    <h1 class="text-2xl font-bold">{{ t('common.auth.signInTitle') }}</h1>
    <p class="text-sm text-gray-500">{{ t('common.auth.signInSubtitle') }}</p>
    <SignIn routing="hash" />
  </div>

  <!-- Con sesión: el rol lo decide el backend (/api/users/me), no el email del login -->
  <div v-else-if="!roleLoaded" class="min-h-screen flex items-center justify-center">
    <p class="text-sm text-gray-400">{{ t('common.auth.loading') }}</p>
  </div>
  <div v-else-if="!isAdmin" class="min-h-screen flex flex-col items-center justify-center gap-4 p-6">
    <h1 class="text-2xl font-bold">{{ t('common.auth.deniedTitle') }}</h1>
    <p class="text-sm text-gray-500">{{ t('common.auth.deniedMsg') }}</p>
    <UserButton />
  </div>
  <div v-else class="bg-linear-to-r from-[#F0FDF4] to-white min-h-screen">
    <div class="mx-auto max-w-360 flex flex-col md:flex-row">
      <Sidebar />
      <main class="flex-1 p-4 md:p-6">
        <RouterView />
      </main>
    </div>
  </div>
</template>
