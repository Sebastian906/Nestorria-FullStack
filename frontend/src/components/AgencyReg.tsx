import { useState } from "react"
import { useAppContext } from "../context/AppContext"
import { useAuth } from "@clerk/react"
import { assets, cities } from "../assets/data"
import axios from "axios"
import toast from "react-hot-toast"
import { useTranslation } from "react-i18next"
import { serverMsg } from "../services/serverMsg"

const AgencyReg = () => {

    const { setShowAgencyReg, refreshProfile } = useAppContext()
    const { getToken } = useAuth()
    const { t } = useTranslation("user")
    const [name, setName] = useState("")
    const [email, setEmail] = useState("")
    const [contact, setContact] = useState("")
    const [address, setAddress] = useState("")
    const [city, setCity] = useState("")

    const onSubmitHandler = async (event: React.FormEvent) => {
        event.preventDefault()
        try {
            const token = await getToken()
            if (!token) {
                toast.error(t("user:agency.fail"))
                return
            }
            await axios.post('/api/agencies',
                { name, contact, email, address, city },
                { headers: { Authorization: `Bearer ${token}` } }
            )
            toast.success(t("user:agency.success"))
            await refreshProfile()
            setShowAgencyReg(false)
        } catch (error: any) {
            toast.error(serverMsg(error, "user:agency.fail"))
        }
    }

    return (
        <div
            onClick={() => setShowAgencyReg(false)}
            className='fixed top-0 left-0 right-0 bottom-0 z-50 flex items-center justify-center bg-black/80'
        >
            <form
                onClick={(e) => e.stopPropagation()}
                onSubmit={onSubmitHandler}
                className='flexCenter bg-white rounded-xl max-w-4xl max-md:mx-2 relative'
            >
                <img
                    src={assets.createPrp}
                    alt="createPrp img"
                    className='w-1/2 rounded-l-xl hidden md:block'
                />
                <div className='flex flex-col md:w-1/2 p-8 md:p-10'>
                    <img
                        onClick={() => setShowAgencyReg(false)}
                        src={assets.close}
                        alt="close img"
                        className='absolute top-4 right-4 h-6 w-6 p-1 cursor-pointer bg-secondary/50 rounded-full shadow-emerald-400'
                    />
                    <h3 className='h3 mb-6'>{t('user:agency.title')}</h3>
                    <div className='flex gap-2 xl:gap-3'>
                        <div>
                            <label
                                htmlFor="name"
                                className='medium-14'
                            >
                                {t('user:agency.name')}
                            </label>
                            <input
                                onChange={(e) => setName(e.target.value)}
                                value={name}
                                id='name'
                                type="text"
                                placeholder={t('common:search.placeholder')}
                                className='regular-14 border bg-secondary/10 border-slate-900/10 rounded-lg w-full px-3 py-1.5 mt-1 outline-none'
                                required
                            />
                        </div>
                        <div>
                            <label
                                htmlFor="contact"
                                className='medium-14'
                            >
                                {t('user:agency.contact')}
                            </label>
                            <input
                                onChange={(e) => setContact(e.target.value)}
                                value={contact}
                                id='contact'
                                type="text"
                                placeholder={t('common:search.placeholder')}
                                className='regular-14 border bg-secondary/10 border-slate-900/10 rounded-lg w-full px-3 py-1.5 mt-1 outline-none'
                                required
                            />
                        </div>
                    </div>
                    <div className='w-full mt-4'>
                        <label
                            htmlFor="email"
                            className='medium-14'
                        >
                            {t('user:agency.email')}
                        </label>
                        <input
                            onChange={(e) => setEmail(e.target.value)}
                            value={email}
                            id='email'
                            type="email"
                            placeholder={t('common:search.placeholder')}
                            className='regular-14 border bg-secondary/10 border-slate-900/10 rounded-lg w-full px-3 py-1.5 mt-1 outline-none'
                            required
                        />
                    </div>
                    <div className='w-full mt-4'>
                        <label
                            htmlFor="city"
                            className='medium-14'
                        >
                            {t('user:agency.address')}
                        </label>
                        <input
                            onChange={(e) => setAddress(e.target.value)}
                            value={address}
                            id='address'
                            type="text"
                            placeholder={t('common:search.placeholder')}
                            className='regular-14 border bg-secondary/10 border-slate-900/10 rounded-lg w-full px-3 py-1.5 mt-1 outline-none'
                            required
                        />
                    </div>
                    <div className='w-full mt-4 max-w-60 mr-auto'>
                        <label
                            htmlFor="city"
                            className='medium-14'
                        >
                            {t('user:agency.city')}
                        </label>
                        <select
                            onChange={(e) => setCity(e.target.value)}
                            value={city}
                            id='city'
                            className='regular-14 border bg-secondary/10 border-slate-900/10 rounded-lg w-full px-3 py-2.5 mt-1 outline-none'
                            required
                        >
                            <option value=''>{t('user:agency.selectCity')}</option>
                            {cities.map((city) => (
                                <option
                                    key={city}
                                    value={city}
                                >{city}</option>
                            ))}
                        </select>
                    </div>
                    <button className='btn-dark py-2 rounded-lg w-32 mt-6'>{t('user:agency.register')}</button>
                </div>
            </form>
        </div>
    )
}

export default AgencyReg