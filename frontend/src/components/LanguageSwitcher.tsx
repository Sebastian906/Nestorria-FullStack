import { useTranslation } from 'react-i18next';
import { LANG_KEY } from '../i18n';

export default function LanguageSwitcher() {
    const { t, i18n } = useTranslation();
    const cur = i18n.language?.startsWith('es') ? 'es' : 'en';
    const set = (lng: 'en' | 'es') => { localStorage.setItem(LANG_KEY, lng); void i18n.changeLanguage(lng); };
    return (
        <div role="group" aria-label={t('common:a11y.language')} className="flex items-center gap-1 rounded-full ring-1 ring-slate-900/10 bg-white/80 px-1 py-1">
            {(['en', 'es'] as const).map((l) => (
                <button
                    key={l}
                    type="button"
                    onClick={() => set(l)}
                    aria-pressed={cur === l}
                    title={l === 'en' ? 'English' : 'Español'}
                    className={cur === l
                        ? 'px-2 py-0.5 rounded-full text-xs font-bold bg-gray-900 text-white'
                        : 'px-2 py-0.5 rounded-full text-xs font-semibold text-gray-600 hover:text-gray-900'}>
                    {l.toUpperCase()}
                </button>
            ))}
        </div>
    );
}