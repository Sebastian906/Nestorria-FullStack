import { Link, useLocation } from "react-router-dom"
import { assets } from "../assets/data"
import Navbar from "./Navbar"
import { useEffect, useState } from "react"
import { useClerk, UserButton } from "@clerk/react"
import { useAppContext } from "../context/AppContext"

const Header = () => {

    const [active, setActive] = useState<boolean>(false)
    const [menuOpened, setMenuOpened] = useState<boolean>(false)
    const [showSearch, setShowSearch] = useState<boolean>(false)
    const location = useLocation()
    const { navigate, user, isOwner, setShowAgencyReg, searchQuery, setSearchQuery } = useAppContext()
    const { openSignIn } = useClerk();

    const BookingIcon = () => (
        <svg
            xmlns="http://www.w3.org/2000/svg"
            width="24"
            height="24"
            viewBox="0 0 36 36"
            fill="none"
            stroke="currentColor"
            strokeWidth="2"
            strokeLinecap="round"
            strokeLinejoin="round"
            className="lucide lucide-scroll-text-icon lucide-scroll-text"
        >
            <path d="M15 12h-5" />
            <path d="M15 8h-5" />
            <path d="M19 17V5a2 2 0 0 0-2-2H4" />
            <path d="M8 21h12a2 2 0 0 0 2-2v-1a1 1 0 0 0-1-1H11a1 1 0 0 0-1 1v1a2 2 0 1 1-4 0V5a2 2 0 1 0-4 0v2a1 1 0 0 0 1 1h3" />
        </svg>
    )

    const toggleMenu = () => setMenuOpened((prev) => !prev)

    const handleSearchChange = (e: any) => {
        setSearchQuery(e.target.value);

        // Redirect to listing page if not already there
        if (e.target.value && location.pathname !== '/listing') {
            navigate('/listing');
        }
    }

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
                        <div>
                            {user && (
                                <button
                                    onClick={() => isOwner
                                        ? window.location.assign(import.meta.env.VITE_ADMIN_URL)
                                        : setShowAgencyReg(true)}
                                    className={`btn-outline px-2 py-1 text-xs font-semibold ${!active && 'text-primary ring-primary bg-transparent hover:text-black'} bg-secondary/10 hover:bg-white`}>
                                    {isOwner ? "Dashboard" : "Register Agency"}
                                </button>
                            )}
                        </div>
                        {/* SEARCH BAR */}
                        <div className='relative hidden xl:flex items-center'>
                            {/* SEARCH INPUT */}
                            <div className={`${active ? 'bg-secondary/10' : 'bg-white'} transition-all duration-300 ease-in-out ring-1 ring-slate-900/10 rounded-full overflow-hidden ${showSearch
                                ? 'w-66.5 opacity-100 px-4 py-2'
                                : 'w-11 opacity-0 px-0 py-0'
                                }`}>
                                <input
                                    onChange={handleSearchChange}
                                    value={searchQuery}
                                    type="text"
                                    placeholder='Type here...'
                                    className='w-full text-sm outline-none pr-10 placeholder:text-slate-400'
                                />
                            </div>
                            {/* SEARCH TOGGLE BUTTON */}
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
                        <div className='group relative top-1'>
                            {/* USER */}
                            <div>
                                {user ? (
                                    <UserButton
                                        appearance={{
                                            elements: {
                                                userButtonAvatarBox: {
                                                    width: '42px',
                                                    height: '42px'
                                                }
                                            }
                                        }}
                                    >
                                        <UserButton.MenuItems>
                                            <UserButton.Action
                                                label='My Bookings'
                                                labelIcon={<BookingIcon />}
                                                onClick={() => navigate('/my-bookings')}
                                            />
                                        </UserButton.MenuItems>
                                    </UserButton>
                                ) : (
                                    <button
                                        onClick={() => openSignIn()}
                                        className='btn-secondary flexCenter gap-2 rounded-full'
                                    >
                                        Login
                                        <img src={assets.user} alt='user' />
                                    </button>
                                )}
                            </div>
                        </div>
                    </div>
                </div>
            </div>
        </header>
    )
}

export default Header