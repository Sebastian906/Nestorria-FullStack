import { getLocale } from '../i18n';
export const formatCurrency = (v: number, currency = import.meta.env.VITE_CURRENCY ?? '$') =>
    `${currency}${v.toLocaleString(getLocale() === 'es' ? 'es-ES' : 'en-US')}`;
export const formatNumber = (v: number) => v.toLocaleString(getLocale() === 'es' ? 'es-ES' : 'en-US');
export const formatDate = (d: string | Date) =>
    new Intl.DateTimeFormat(getLocale() === 'es' ? 'es-ES' : 'en-US', { dateStyle: 'medium' }).format(new Date(d));