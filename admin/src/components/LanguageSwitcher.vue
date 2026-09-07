<script setup>
import { useI18n } from 'vue-i18n';
import { setLocale } from '../i18n/index.js';

const { t, locale } = useI18n();
const set = (l) => { setLocale(l); };
const isActive = (l) => String(locale.value).startsWith(l);
</script>
<template>
    <div role="group" :aria-label="t('common.a11y.language')" class="flex items-center gap-1 rounded-full ring-1 ring-slate-900/10 bg-white px-1 py-1">
        <button
            v-for="l in ['en', 'es']"
            :key="l"
            type="button"
            @click="set(l)"
            :aria-pressed="isActive(l)"
            :title="l === 'en' ? 'English' : 'Español'"
            :class="isActive(l)
                ? 'px-2 py-0.5 rounded-full text-xs font-bold bg-gray-900 text-white'
                : 'px-2 py-0.5 rounded-full text-xs font-semibold text-gray-600 hover:text-gray-900'">
            {{ l.toUpperCase() }}
        </button>
    </div>
</template>