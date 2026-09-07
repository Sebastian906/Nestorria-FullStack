import { i18n } from "../i18n/index.js";

const CODE_MAP = {
    "agency.already-exists": "categories.errors.agencyExists",
    "report.unsupported-format": "reports.errors.badFormat",
};

export function serverMsg(error, fallbackKey) {
    const data = error?.response?.data;
    const t = i18n.global.t;
    if (data?.code && CODE_MAP[data.code]) {
        return t(CODE_MAP[data.code]);
    }
    if (data?.message) {
        return data.message;
    }
    return t(fallbackKey);
}