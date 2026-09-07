import { Link } from "react-router-dom"
import { assets } from "../assets/data"
import { useTranslation } from "react-i18next"

const Footer = () => {
    const { t } = useTranslation('home');
    return (
        <footer className='pt-16 xl:pt-20 w-full text-gray-500 bg-[#F0FDF4]'>
            <div className='max-padd-container'>
                <div className='flex flex-wrap justify-between gap-12 md:gap-6'>
                    <div className='max-w-80'>
                        <div className='flex mb-4'>
                            <Link to={'/'}>
                                <img
                                    src={assets.logoImg}
                                    alt='logo'
                                    className={`h-11`}
                                />
                            </Link>
                        </div>
                        <p className='text-sm'>
                            {t('footer.tagline')}
                        </p>
                        <div className='flex items-center gap-3 mt-4'>
                            <img src={assets.facebook} alt="Facebook" />
                            <img src={assets.twitter} alt="Twitter" />
                            <img src={assets.instagram} alt="Instagram" />
                            <img src={assets.linkedin} alt="LinkedIn" />
                        </div>
                    </div>

                    <div>
                        <p className='h4 text-black/80'>{t('footer.company')}</p>
                        <ul className='mt-3 flex flex-col gap-2 text-sm'>
                            <li><a href="#">{t('footer.about')}</a></li>
                            <li><a href="#">{t('footer.careers')}</a></li>
                            <li><a href="#">{t('footer.press')}</a></li>
                            <li><a href="#">{t('footer.blog')}</a></li>
                            <li><a href="#">{t('footer.partners')}</a></li>
                        </ul>
                    </div>

                    <div>
                        <p className='h4 text-black/80'>{t('footer.support')}</p>
                        <ul className='mt-3 flex flex-col gap-2 text-sm'>
                            <li><a href="#">{t('footer.help')}</a></li>
                            <li><a href="#">{t('footer.safety')}</a></li>
                            <li><a href="#">{t('footer.cancellation')}</a></li>
                            <li><a href="#">{t('footer.contactUs')}</a></li>
                            <li><a href="#">{t('footer.accessibility')}</a></li>
                        </ul>
                    </div>

                    <div className='max-w-80'>
                        <p className='h4 text-black/80'>{t('footer.stayUpdated')}</p>
                        <p className='mt-3 text-sm'>
                            {t('footer.newsletter')}
                        </p>
                        <div className='flex items-center border pl-4 gap-2 bg-white border-gray-500/30 h-11.5 rounded-full overflow-hidden max-w-md w-full mt-6'>
                            <input
                                type="text"
                                className='w-full h-full outline-none text-sm text-gray-500'
                                placeholder={t('footer.emailPh')}
                            />
                            <button className='btn-dark font-medium px-3.5! py-2 mr-0.5'>
                                {t('footer.subscribe')}
                            </button>
                        </div>
                    </div>
                </div>
                <div className='flex flex-col md:flex-row gap-2 items-center justify-between py-5 mt-8'>
                    <p>© {new Date().getFullYear()} <a href='/'>Nestorria</a>. {t('footer.rights')}</p>
                    <ul className='flex items-center gap-4'>
                        <li><a href="#">{t('footer.privacy')}</a></li>
                        <li><a href="#">{t('footer.terms')}</a></li>
                        <li><a href="#">{t('footer.sitemap')}</a></li>
                    </ul>
                </div>
            </div>
        </footer>
    )
}

export default Footer