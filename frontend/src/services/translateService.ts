import axios from "axios";
import { getLocale } from "../i18n";

const MAX_CACHE = 500;
const cache = new Map<string, string>();
const inflight = new Map<string, Promise<string>>();

function setBounded(key: string, value: string) {
    if (cache.has(key)) cache.delete(key); // refresh LRU order
    cache.set(key, value);
    if (cache.size > MAX_CACHE) {
        const oldest = cache.keys().next().value as string;
        cache.delete(oldest);
    }
}

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
            setBounded(key, out);
            return out;
        } catch {
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
    inflight.clear();
}
