import { useEffect, useState } from "react";
import { useTranslation } from "react-i18next";
import { displayText, clearDisplayCache } from "../services/translateService";

/** Traduce un texto del backend al idioma UI. Sin idioma -> original (sin parpadeo). */
export function useDisplayText(original: string | null | undefined): string {
    const { i18n } = useTranslation();
    const [value, setValue] = useState(original ?? "");
    const locale = i18n.language?.startsWith("es") ? "es" : "en";

    useEffect(() => {
        let alive = true;
        setValue(original ?? "");
        if (!original) return;
        displayText(original).then((out) => {
            if (alive) setValue(out);
        });
        return () => {
            alive = false;
        };
    }, [original, locale]);

    useEffect(() => {
        const onLang = () => clearDisplayCache();
        i18n.on("languageChanged", onLang);
        return () => {
            i18n.off("languageChanged", onLang);
        };
    }, [i18n]);

    return value;
}
