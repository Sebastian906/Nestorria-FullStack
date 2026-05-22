import type { Dispatch, SetStateAction } from "react";
import { NavLink } from "react-router-dom";

interface NavbarProps {
    setMenuOpened: Dispatch<SetStateAction<boolean>>;
    containerStyles: string;
}

const Navbar: React.FC<NavbarProps> = ({ setMenuOpened, containerStyles }) => {
    const navLinks = [
        { path: '/', title: 'Home' },
        { path: '/listing', title: 'Listing' },
        { path: '/blog', title: 'Blog' },
        { path: '/contact', title: 'Contact' }
    ];

    return <nav className={`${containerStyles}`}>
        {navLinks.map((link) => (
            <NavLink
                onClick={() => {
                    setMenuOpened(false);
                    scrollTo(0, 0);
                }}
                key={link.title}
                to={link.path}
                className={({ isActive }) => `${isActive ? 'active-link' : ''} px-3 py-2 rounded-full uppercase text-sm font-bold `}
            >
                {link.title}
            </NavLink>
        ))}
    </nav>
}

export default Navbar