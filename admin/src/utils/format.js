import { i18n } from "../i18n/index.js";

const localeOf = () => {
    const l = i18n.global.locale?.value ?? "en";
    return String(l).startsWith("es") ? "es-ES" : "en-US";
};

export const formatCurrency = (v, currency = import.meta.env.VITE_CURRENCY || "$") =>
    `${currency}${Number(v ?? 0).toLocaleString(localeOf())}`;

export const formatNumber = (v) => Number(v ?? 0).toLocaleString(localeOf());

export const formatDate = (d) =>
    new Intl.DateTimeFormat(localeOf(), { dateStyle: "medium" }).format(new Date(d));
