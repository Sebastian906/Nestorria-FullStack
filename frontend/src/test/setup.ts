import '@testing-library/jest-dom'
import i18n from 'i18next'
import { initReactI18next } from 'react-i18next'

// jsdom has no scrollTo (Navbar calls scrollTo(0,0) on click) — stub it.
window.scrollTo = window.scrollTo ?? (() => {}) as typeof window.scrollTo;

import enChat from '../i18n/locales/en/chat.json'
import enCommon from '../i18n/locales/en/common.json'
import enNav from '../i18n/locales/en/nav.json'
import enHome from '../i18n/locales/en/home.json'
import enListing from '../i18n/locales/en/listing.json'
import enProperty from '../i18n/locales/en/property.json'
import enBooking from '../i18n/locales/en/booking.json'
import enUser from '../i18n/locales/en/user.json'
import enGuides from '../i18n/locales/en/guides.json'

// Test-only i18n init: real EN strings so assertions on English copy keep passing.
// Component namespaces used in tests: chat (ChatWidget), nav (Navbar), common/user (Header).
await i18n.use(initReactI18next).init({
    lng: 'en',
    fallbackLng: 'en',
    defaultNS: 'common',
    interpolation: { escapeValue: false },
    resources: {
        en: {
            chat: enChat,
            common: enCommon,
            nav: enNav,
            home: enHome,
            listing: enListing,
            property: enProperty,
            booking: enBooking,
            user: enUser,
            guides: enGuides,
        },
    },
});