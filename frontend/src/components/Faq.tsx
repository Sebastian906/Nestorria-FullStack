import { useState } from "react"
import { assets } from "../assets/data"
import Title from "./Title"
import { useTranslation } from "react-i18next"

const Faq = () => {
    const { t } = useTranslation('home');
    const [openIndex, setOpenIndex] = useState<number | null>(null)
    const faqsData = [
        { question: t('faq.q1'), answer: t('faq.a1') },
        { question: t('faq.q2'), answer: t('faq.a2') },
        { question: t('faq.q3'), answer: t('faq.a3') },
        { question: t('faq.q4'), answer: t('faq.a4') },
        { question: t('faq.q5'), answer: t('faq.a5') },
    ]

    return (
        <section className='max-padd-container py-16 xl:py-22'>
            {/* CONTAINER */}
            <div className='flex flex-col gap-y-12 xl:flex-row'>
                {/* IMAGE - LEFT SIDE */}
                <div className='flex-1'>
                    <div className='relative rounded-3xl overflow-hidden inline-block'>
                        <img
                            src={assets.faq}
                            alt='faqImg'
                            className='block w-full'
                        />
                        <div className='absolute top-5 left-5 right-5 bg-white p-3 rounded-2xl flex items-center gap-4 z-10'>
                            <img
                                src={assets.signature}
                                alt='signImg'
                                width={55}
                            />
                            <div>
                                <h5 className='bold-16'>{t('faq.experts')}</h5>
                                <p>{t('faq.expertsBody')}</p>
                            </div>
                        </div>
                    </div>
                </div>
                {/* FAQs - RIGHT SIDE */}
                <div className='flex-1 flex flex-col justify-center'>
                    <Title
                        title1={t('faq.title1')}
                        title2={t('faq.title2')}
                        para={t('faq.para')}
                        titleStyles={'mb-10'}
                    />
                    <div className='max-w-xl w-full flex flex-col gap-4 items-start text-left'>
                        {faqsData.map((faq, index) => (
                            <div
                                key={index}
                                className='flex flex-col items-start w-full'
                            >
                                <div
                                    className='flex items-center justify-between w-full cursor-pointer bg-secondary/10 border-slate-900/10 p-2 px-4 rounded-lg'
                                    onClick={() => setOpenIndex(openIndex === index ? null : index)}
                                >
                                    <h2 className='text-sm'>{faq.question}</h2>
                                    <img
                                        src={assets.down}
                                        alt=""
                                    />
                                </div>
                                <p className={`text-sm text-slate-500 px-4 transition-all duration-500 ease-in-out ${openIndex === index ? 'opacity-100 max-h-75 translate-y-0 pt-4' : 'opacity-0 max-h-0 -translate-y-2'}`}>
                                    {faq.answer}
                                </p>
                            </div>
                        ))}
                    </div>
                </div>
            </div>
        </section>
    )
}

export default Faq