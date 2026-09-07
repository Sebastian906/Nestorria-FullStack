import { assets } from "../assets/data"
import { useTranslation } from "react-i18next"

const Contact = () => {
    const { t } = useTranslation('user');
    return (
        <div className='bg-linear-to-r from-[#F0FDF4] to-white py-16 pt-28'>
            <form className="flex flex-col items-center text-sm text-slate-800">
                <p className="text-xs bg-black/80 text-white font-medium px-3 py-1 rounded-full">{t('user:contact.badge')}</p>
                <h1 className="text-4xl font-bold py-4 text-center">{t('user:contact.title')}</h1>
                <p className="max-md:text-sm text-gray-500 pb-10 text-center">
                    {t('user:contact.subtitle')}
                    <a
                        href="#"
                        className="text-secondary hover:underline"
                    > contact@nestorria.com
                    </a>
                </p>
                <div className="max-w-96 w-full px-4">
                    <label
                        htmlFor="name"
                        className="font-medium"
                    >
                        {t('user:contact.name')}
                    </label>
                    <div className="flex items-center mt-2 mb-4 h-10 pl-3 border border-slate-300 bg-secondary/10 rounded-full focus-within:ring-2 focus-within:ring-black/80 transition-all overflow-hidden">
                        <img
                            src={assets.user}
                            alt="userIcon"
                            width={19}
                            className='invert-50'
                        />
                        <input
                            type="text"
                            className="h-full px-2 w-full outline-none bg-transparent"
                            placeholder={t('user:contact.namePh')}
                            required
                        />
                    </div>
                    <label
                        htmlFor="email-address"
                        className="font-medium mt-4"
                    >
                        {t('user:contact.email')}
                    </label>
                    <div className="flex items-center mt-2 mb-4 h-10 pl-3 border border-slate-300 bg-secondary/10 rounded-full focus-within:ring-2 focus-within:ring-black/80 transition-all overflow-hidden">
                        <img
                            src={assets.mail}
                            alt="mailIcon"
                            width={19}
                            className='invert-50'
                        />
                        <input
                            type="email"
                            className="h-full px-2 w-full outline-none bg-transparent"
                            placeholder={t('user:contact.emailPh')}
                            required
                        />
                    </div>
                    <label
                        htmlFor="message"
                        className="font-medium mt-4"
                    >
                        {t('user:contact.message')}
                    </label>
                    <textarea
                        rows={4}
                        className="w-full mt-2 p-2 border border-slate-300 bg-secondary/10 rounded-lg resize-none outline-none focus:ring-2 focus-within:ring-black/80 transition-all"
                        placeholder={t('user:contact.messagePh')}
                        required
                    ></textarea>
                    <button
                        type="submit"
                        className="flexCenter gap-1 mt-5 btn-secondary w-full font-bold!"
                    >
                        {t('user:contact.submit')}
                        <img
                            src={assets.right}
                            alt="rightIcon"
                        />
                    </button>
                </div>
            </form>
        </div>
    )
}

export default Contact