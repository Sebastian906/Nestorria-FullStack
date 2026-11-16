import { getLocale } from '../i18n';
const empty = '—';
const locale = () => (getLocale() === 'es' ? 'es-ES' : 'en-US');
export const formatCurrency = (v: number | null | undefined, currency = import.meta.env.VITE_CURRENCY ?? '$') =>
    v == null ? empty : `${currency}${v.toLocaleString(locale())}`;
export const formatNumber = (v: number | null | undefined) =>
    v == null ? empty : v.toLocaleString(locale());
// Precios de cards: 1.850.000.000 -> $1.850M, 4.200.000 -> $4,2M, 780.000 -> $780.000
export const formatCurrencyCompact = (v: number | null | undefined, currency = import.meta.env.VITE_CURRENCY ?? '$') => {
    if (v == null) return empty;
    if (Math.abs(v) < 1_000_000) return formatCurrency(v, currency);
    const millones = v / 1_000_000;
    const decimals = Math.abs(millones) < 10 ? 1 : 0;
    return `${currency}${millones.toLocaleString(locale(), { maximumFractionDigits: decimals })}M`;
};
export const formatDate = (d: string | Date) =>
    new Intl.DateTimeFormat(getLocale() === 'es' ? 'es-ES' : 'en-US', { dateStyle: 'medium' }).format(new Date(d));