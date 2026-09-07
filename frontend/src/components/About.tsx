import { assets } from "../assets/data"
import Title from "./Title"
import { useTranslation } from "react-i18next"

const About = () => {
    const { t } = useTranslation('home');
    return (
        <section className='max-padd-container py-16 xl:py-28 pt-36!'>
            {/* CONTAINER */}
            <div className='flex items-center flex-col lg:flex-row gap-12'>
                {/* INFO - LEFT SIDE */}
                <div className='flex-1'>
                    <Title
                        title1={t('about.title1')}
                        title2={t('about.title2')}
                        para={t('about.para')}
                        titleStyles={'mb-10'}
                        title2Styles={''}
                        paraStyles={''}
                    />
                    <div className='flex flex-col gap-6 mt-5'>
                        <div className='flex gap-3'>
                            <img
                                src={assets.calendarSecondary}
                                alt='Calendar'
                                width={20}
                            />
                            <p>{t('about.f1')}</p>
                        </div>
                        <div className='flex gap-3'>
                            <img
                                src={assets.graph}
                                alt='Graph'
                                width={20}
                            />
                            <p>{t('about.f2')}</p>
                        </div>
                        <div className='flex gap-3'>
                            <img
                                src={assets.map}
                                alt='Map'
                                width={20}
                            />
                            <p>{t('about.f3')}</p>
                        </div>
                        <div className='flex gap-3'>
                            <img
                                src={assets.pound}
                                alt='Pound'
                                width={20}
                            />
                            <p>{t('about.f4')}</p>
                        </div>
                    </div>
                    {/* RATING */}
                    <div className='flex items-center divide-x divide-gray-300 mt-11'>
                        <div className='flex -space-x-3 pr-3'>
                            <img
                                src={assets.client1}
                                alt='Star'
                                className='w-12 h-12 rounded-full border-2 border-white hover:-translate-y-1 transition z-1'
                            />
                            <img
                                src={assets.client2}
                                alt='Star'
                                className='w-12 h-12 rounded-full border-2 border-white hover:-translate-y-1 transition z-2'
                            />
                            <img
                                src={assets.client3}
                                alt='Star'
                                className='w-12 h-12 rounded-full border-2 border-white hover:-translate-y-1 transition z-2'
                            />
                            <img
                                src={assets.client4}
                                alt='Star'
                                className='w-12 h-12 rounded-full border-2 border-white hover:-translate-y-1 transition z-2'
                            />
                        </div>
                        <div className="pl-3">
                            <div className="flex items-center">
                                <img
                                    src={assets.star}
                                    alt='starIcon'
                                    width={17}
                                />
                                <img
                                    src={assets.star}
                                    alt='starIcon'
                                    width={17}
                                />
                                <img
                                    src={assets.star}
                                    alt='starIcon'
                                    width={17}
                                />
                                <img
                                    src={assets.star}
                                    alt='starIcon'
                                    width={17}
                                />
                                <img
                                    src={assets.star}
                                    alt='starIcon'
                                    width={17}
                                />
                                <p className='text-gray-600 medium-16 ml-2'>5.0</p>
                            </div>
                            <p className='text-sm text-gray-500'>
                                {t('about.trustedBy')} {' '}
                                <span className='font-medium text-gray-800'>100.000+</span>{' '}
                                {t('about.users')}
                            </p>
                        </div>
                    </div>
                </div>
                {/* IMAGE - RIGHT SIDE */}
                <div className='flex-1'>
                    <div className='relative flex justify-end'>
                        <img 
                            src={assets.about}
                            alt='aboutImg'
                            className='rounded-3xl'
                        />
                    </div>
                </div>
            </div>
        </section>
    )
}

export default About