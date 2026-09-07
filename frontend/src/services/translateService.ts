import axios from "axios";
import { getLocale } from "../i18n";

const cache = new Map<string, string>();

const inflight = new Map<string, Promise<string>>();

export async function displayText(original: string | null | undefined): Promise<string> {
    if (!original) return "";
    const target = getLocale();
    const key = `${target}::${original}`;
    const hit = cache.get(key);
    if (hit !== undefined) return hit;

    const pending = inflight.get(key);
    if (pending) return pending;

    const job = (async () => {
        try {
            const { data } = await axios.post(
                "/api/ai/translate",
                { text: original, source: "auto", target },
                { timeout: 8000 }
            );
            const out = typeof data?.translated === "string" && data.translated.trim()
                ? data.translated
                : original;
            cache.set(key, out);
            return out;
        } catch {
            cache.set(key, original);
            return original;
        } finally {
            inflight.delete(key);
        }
    })();

    inflight.set(key, job);
    return job;
}

export function clearDisplayCache() {
    cache.clear();
}
