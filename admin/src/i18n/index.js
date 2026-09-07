import { createI18n } from 'vue-i18n';
import axios from 'axios';
import enNav from './locales/en/nav.json';
import esNav from './locales/es/nav.json';
import enCommon from './locales/en/common.json';
import esCommon from './locales/es/common.json';
import enDashboard from './locales/en/dashboard.json';
import esDashboard from './locales/es/dashboard.json';
import enProperty from './locales/en/property.json';
import esProperty from './locales/es/property.json';
import enCategories from './locales/en/categories.json';
import esCategories from './locales/es/categories.json';
import enReports from './locales/en/reports.json';
import esReports from './locales/es/reports.json';
import enAi from './locales/en/ai.json';
import esAi from './locales/es/ai.json';
import enNotifications from './locales/en/notifications.json';
import esNotifications from './locales/es/notifications.json';

export const LANG_KEY = 'nestorria-lang';
const stored = localStorage.getItem(LANG_KEY) ?? 'en';
document.documentElement.lang = stored;

export const i18n = createI18n({
    legacy: false,
    locale: stored,
    fallbackLocale: 'en',
    messages: {
        en: { nav: enNav, common: enCommon, dashboard: enDashboard, property: enProperty, categories: enCategories, reports: enReports, ai: enAi, notifications: enNotifications },
        es: { nav: esNav, common: esCommon, dashboard: esDashboard, property: esProperty, categories: esCategories, reports: esReports, ai: esAi, notifications: esNotifications },
    },
});
i18n.global.locale.value; // reactive
axios.defaults.headers.common['Accept-Language'] = stored.startsWith('es') ? 'es' : 'en';
export function setLocale(l) {
    localStorage.setItem(LANG_KEY, l);
    i18n.global.locale.value = l;
    document.documentElement.lang = l;
    axios.defaults.headers.common['Accept-Language'] = l.startsWith('es') ? 'es' : 'en';
}