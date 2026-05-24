import { render, type RenderOptions } from '@testing-library/react'
import { BrowserRouter } from 'react-router-dom'
import type { ReactElement } from 'react'

const AllProviders = ({ children }: { children: React.ReactNode }) => {
    return <BrowserRouter>{children}</BrowserRouter>
}

const customRender = (ui: ReactElement, options?: RenderOptions) =>
    render(ui, { wrapper: AllProviders, ...options })

export * from '@testing-library/react'
export { customRender as render }