import './i18n'
import { createRoot } from 'react-dom/client'
import './index.css'
import App from './App.tsx'
import { BrowserRouter } from 'react-router-dom'
import { AppContextProvider } from './context/AppContext.tsx'
import { ClerkProvider } from '@clerk/react'
import { enUS, esES } from '@clerk/localizations'
import { useEffect, useState } from 'react'
import { useTranslation } from 'react-i18next'

const PUBLISHABLE_KEY = import.meta.env.VITE_CLERK_PUBLISHABLE_KEY

if (!PUBLISHABLE_KEY) {
  throw new Error('Add your clerk publishable key to the .env file as VITE_CLERK_PUBLISHABLE_KEY')
}

function LocalizedApp() {
  const { i18n } = useTranslation();
  // ponytail: localizations v4 vs react v6 types mismatch -> any, runtime shape is compatible
  const [locale, setLocale] = useState<any>(i18n.language?.startsWith('es') ? esES : enUS);

  useEffect(() => {
    const update = (lng: string) => setLocale(lng.startsWith('es') ? esES : enUS);
    update(i18n.language);
    i18n.on('languageChanged', update);
    return () => { i18n.off('languageChanged', update); };
  }, [i18n]);

  return (
    <ClerkProvider publishableKey={PUBLISHABLE_KEY} localization={locale}>
      <BrowserRouter>
        <AppContextProvider>
          <App />
        </AppContextProvider>
      </BrowserRouter>
    </ClerkProvider>
  );
}

createRoot(document.getElementById('root')!).render(<LocalizedApp />)
