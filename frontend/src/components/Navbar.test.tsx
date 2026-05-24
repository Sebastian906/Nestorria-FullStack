import userEvent from '@testing-library/user-event'
import { render, screen } from '../test/utils'
import Navbar from './Navbar'

const mockSetMenuOpened = vi.fn()

describe('Navbar', () => {
    it('renderiza todos los links de navegación', () => {
        render(<Navbar setMenuOpened={mockSetMenuOpened} containerStyles="" />)

        expect(screen.getByRole('link', { name: /home/i })).toBeInTheDocument()
        expect(screen.getByRole('link', { name: /listing/i })).toBeInTheDocument()
        expect(screen.getByRole('link', { name: /blog/i })).toBeInTheDocument()
        expect(screen.getByRole('link', { name: /contact/i })).toBeInTheDocument()
    })

    it('llama setMenuOpened(false) al hacer click en un link', async () => {
        const user = userEvent.setup()
        render(
            <Navbar setMenuOpened={mockSetMenuOpened} containerStyles="" />
        )
        await user.click(screen.getByRole('link', { name: /home/i }))
        expect(mockSetMenuOpened).toHaveBeenCalledWith(false)
    })
})