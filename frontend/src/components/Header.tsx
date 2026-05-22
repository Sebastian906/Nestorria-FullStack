import { Link, useLocation } from "react-router-dom"
import { assets } from "../assets/data"
import Navbar from "./Navbar"
import { useEffect, useState } from "react"

const Header = () => {

    const [active, setActive] = useState<boolean>(false)
    const [menuOpened, setMenuOpened] = useState<boolean>(false)
    const [showSearch, setShowSearch] = useState<boolean>(false)
    const location = useLocation()

    const toggleMenu = () => setMenuOpened(prev => !prev)

    useEffect(() => {
        const handleScroll = () => {
            if (location.pathname === '/') {
                setActive(window.scrollY > 10)
            } else {
                setActive(true)
            } if (window.scrollY > 10) {
                setMenuOpened(false)
            }
        }
        window.addEventListener('scroll', handleScroll)
        handleScroll()
        return () => {
            window.removeEventListener('scroll', handleScroll)
        }
    }, [location.pathname])

    return (
        <header className={`${active ? 'bg-white py-5 shadow-rd' : 'py-4'} fixed top-0 w-full left-0 right-0 z-50 transition-all duration-200`}>
            <div className='max-padd-container'>
                {/* CONTAINER */}
                <div className='flexBetween'>
                    {/* LOGO */}
                    <div className='flex flex-1'>
                        <Link to={'/'}>
                            <img
                                src={assets.logoImg}
                                alt='logo'
                                className={`${!active && 'invert'} h-11`}
                            />
                        </Link>
                    </div>
                    {/* NAV BAR */}
                    <Navbar
                        setMenuOpened={setMenuOpened}
                        containerStyles={`${menuOpened ? 'flex items-start flex-col gap-y-8 fixed top-16 right-6 p-5 bg-white shadow-md w-52 ring-1 ring-slate-900/5 rounded-xl z-50' : 'hidden lg:flex gap-x-5 xl:gap-x-1 medium-15 p-1'
                            } ${!menuOpened && !active ? 'text-white' : ''} `}
                    />
                    {/* BUTTONS, SEARCH BAR & PROFILE */}
                    <div className='flex sm:flex-1 items-center sm:justify-end gap-x-4 sm:gap-x-8'>
                        {/* SEARCH BAR */}
                        <div className='relative hidden xl:flex items-center'>
                            <div className={`${active ? 'bg-secondary/10' : 'bg-white'} transition-all duration-300 ease-in-out ring-1 ring-slate-900/10 rounded-full overflow-hidden ${
                                showSearch
                                ? 'w-66.5 opacity-100 px-4 py-2'
                                : 'w-11 opacity-0 px-0 py-0'
                            }`}>
                                <input
                                    type="text"
                                    placeholder='Type here...'
                                    className='w-full text-sm outline-none pr-10 placeholder:text-slate-400'
                                />
                            </div>
                            <div
                                onClick={() => setShowSearch(prev => !prev)}
                                className={`${active ? 'bg-secondary/10' : 'bg-primary'} absolute right-0 ring-1 ring-slate-900/10 p-2 rounded-full cursor-pointer z-10`}
                            >
                                <img
                                    src={assets.search}
                                    alt="searchIcon"
                                />
                            </div>
                        </div>
                        {/* MENU TOGGLE */}
                        <>
                            {menuOpened ? (
                                <img
                                    src={assets.close}
                                    alt="closeMenuIcon"
                                    onClick={toggleMenu}
                                    className={`${!active && 'invert'} lg:hidden cursor-pointer text-xl`}
                                />
                            ) : (
                                <img
                                    src={assets.menu}
                                    alt="openMenuIcon"
                                    onClick={toggleMenu}
                                    className={`${!active && 'invert'} lg:hidden cursor-pointer text-xl`}
                                />
                            )}
                        </>
                        { /* USER PROFILE */}
                        <div>
                            {/* USER */}
                            <div>
                                <div>
                                    <button className='btn-secondary flexCenter gap-2 rounded-full'>
                                        Login
                                        <img src={assets.user} alt='user' />
                                    </button>
                                </div>
                            </div>
                        </div>
                    </div>
                </div>
            </div>
        </header>
    )
}

export default Header