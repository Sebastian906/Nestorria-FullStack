import i18n from 'i18next';
import { initReactI18next } from 'react-i18next';
import LanguageDetector from 'i18next-browser-languagedetector';
import axios from 'axios';
import enCommon from './locales/en/common.json';
import esCommon from './locales/es/common.json';
import enNav from './locales/en/nav.json';
import esNav from './locales/es/nav.json';
import enBooking from './locales/en/booking.json';
import esBooking from './locales/es/booking.json';
import enProperty from './locales/en/property.json';
import esProperty from './locales/es/property.json';
import enUser from './locales/en/user.json';
import esUser from './locales/es/user.json';
import enChat from './locales/en/chat.json';
import esChat from './locales/es/chat.json';
import enHome from './locales/en/home.json';
import esHome from './locales/es/home.json';
import enListing from './locales/en/listing.json';
import esListing from './locales/es/listing.json';
import enGuides from './locales/en/guides.json';
import esGuides from './locales/es/guides.json';

export const LANG_KEY = 'nestorria-lang';
export const getLocale = () => (i18n.language?.startsWith('es') ? 'es' : 'en') as 'en' | 'es';

const stored = localStorage.getItem(LANG_KEY);
if (stored) document.documentElement.lang = stored;

await i18n
    .use(LanguageDetector)
    .use(initReactI18next)
    .init({
        lng: stored ?? 'en',
        fallbackLng: ['es', 'en'],
        defaultNS: 'common',
        resources: {
            en: { common: enCommon, nav: enNav, booking: enBooking, property: enProperty, user: enUser, chat: enChat, home: enHome, listing: enListing, guides: enGuides },
            es: { common: esCommon, nav: esNav, booking: esBooking, property: esProperty, user: esUser, chat: esChat, home: esHome, listing: esListing, guides: esGuides },
        },
        detection: { order: ['localStorage'], caches: ['localStorage'], lookupLocalStorage: LANG_KEY },
        interpolation: { escapeValue: true },
    });

i18n.on('languageChanged', (lng) => {
    document.documentElement.lang = lng;
    axios.defaults.headers.common['Accept-Language'] = lng.startsWith('es') ? 'es' : 'en';
});
axios.defaults.headers.common['Accept-Language'] = (stored ?? 'en').startsWith('es') ? 'es' : 'en';
export default i18n;