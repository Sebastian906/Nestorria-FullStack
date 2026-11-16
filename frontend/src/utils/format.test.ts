import { formatCurrencyCompact } from './format'

describe('formatCurrencyCompact', () => {
    it('usa millones a partir de 1.000.000', () => {
        expect(formatCurrencyCompact(1850000000)).toMatch(/^\$1[.,]850M$/)
        expect(formatCurrencyCompact(4200000)).toMatch(/^\$4[.,]2M$/)
    })

    it('deja el número completo por debajo del millón', () => {
        expect(formatCurrencyCompact(780000)).toMatch(/^\$780[.,]000$/)
    })

    it('no revienta con precios ausentes', () => {
        expect(formatCurrencyCompact(null)).toBe('\u2014')
        expect(formatCurrencyCompact(undefined)).toBe('\u2014')
    })
})
