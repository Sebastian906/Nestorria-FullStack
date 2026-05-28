import { assets } from "../assets/data"
import Title from "./Title"

const About = () => {
    return (
        <section className='max-padd-container py-16 xl:py-28 pt-36!'>
            {/* CONTAINER */}
            <div className='flex items-center flex-col lg:flex-row gap-12'>
                {/* INFO - LEFT SIDE */}
                <div className='flex-1'>
                    <Title
                        title1={'Your Trusted Real Estate Partner'}
                        title2={'Helping You Every Step of the Way'}
                        para={'Trust, clarity, and simplicity are at the core of everything we do to make your propety journey easy.'}
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
                            <p>In-app scheduling for property viewings</p>
                        </div>
                        <div className='flex gap-3'>
                            <img
                                src={assets.graph}
                                alt='Graph'
                                width={20}
                            />
                            <p>Real-time market price update</p>
                        </div>
                        <div className='flex gap-3'>
                            <img
                                src={assets.map}
                                alt='Map'
                                width={20}
                            />
                            <p>User-friendly interface for smooth navigation</p>
                        </div>
                        <div className='flex gap-3'>
                            <img
                                src={assets.pound}
                                alt='Pound'
                                width={20}
                            />
                            <p>Access to off-market properties</p>
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
                                Trusted By {' '}
                                <span className='font-medium text-gray-800'>100.000+</span>{' '}
                                users
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