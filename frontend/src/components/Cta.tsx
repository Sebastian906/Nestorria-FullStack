import { assets } from "../assets/data"
import { useTranslation } from "react-i18next"

const Cta = () => {
    const { t } = useTranslation('home');
    return (
        <section className="bg-[#F0FDF4] pt-16 xl:pt-22">
            <div className="max-padd-container mx-2 md:mx-auto p-px">
                <div className="flex flex-col items-center justify-center text-center py-12 md:py-16 rounded-[15px]">
                    <div className="flexCenter justify-center bg-black/80 text-white px-3 py-1.5 ring-1 ring-slate-900/10 gap-1 rounded-full text-xs">
                        <img
                            src={assets.rocket}
                            alt="Rocket"
                            width={17}
                            className="invert"
                        />
                        <span>{t('cta.badge')}</span>
                    </div>
                    <h2 className="h2 mt-2">
                        {t('cta.title')}
                    </h2>
                    <p className="text-slate-500 mt-2 max-w-lg max-md:text-sm">{t('cta.subtitle')}</p>
                    <button
                        type="button"
                        className="btn-secondary mt-4"
                    >
                        {t('cta.action')}
                    </button>
                </div>
            </div>
        </section>
    )
}

export default Cta