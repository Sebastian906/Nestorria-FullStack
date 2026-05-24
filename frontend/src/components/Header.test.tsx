import { render, screen } from '../test/utils'
import Header from './Header'

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