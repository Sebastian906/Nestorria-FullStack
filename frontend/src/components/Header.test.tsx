import { render, screen } from '../test/utils'
import { vi } from 'vitest'
import Header from './Header'

vi.mock('../context/AppContext', () => ({
    useAppContext: () => ({
        navigate: vi.fn(),
        user: null,
        isOwner: false,
        setShowAgencyReg: vi.fn(),
        searchQuery: '',
        setSearchQuery: vi.fn(),
    }),
}))

vi.mock('@clerk/react', () => ({
    useClerk: () => ({ openSignIn: vi.fn() }),
    UserButton: () => null,
}))

describe('Header', () => {
    it('renderiza el logo', () => {
        render(<Header />)
        expect(screen.getByAltText(/logo/i)).toBeInTheDocument()
    })

    it('renderiza el botón de login', () => {
        render(<Header />)
        expect(screen.getByRole('button', { name: /login/i })).toBeInTheDocument()
    })
})