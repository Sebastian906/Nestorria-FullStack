import { dummyProperties, blogs } from './data'

describe('dummyProperties', () => {
    it('cada propiedad tiene los campos requeridos', () => {
        dummyProperties.forEach(p => {
            expect(p).toHaveProperty('_id')
            expect(p).toHaveProperty('title')
            expect(p.price).toHaveProperty('rent')
            expect(p.price).toHaveProperty('sale')
            expect(p.facilities.bedrooms).toBeGreaterThanOrEqual(1)
        })
    })

    it('no hay IDs duplicados', () => {
        const ids = dummyProperties.map(p => p._id)
        const unique = new Set(ids)
        expect(unique.size).toBe(ids.length)
    })
})

describe('blogs', () => {
    it('todos los blogs tienen imagen y categoría', () => {
        blogs.forEach(b => {
            expect(b.image).toBeTruthy()
            expect(b.category).toBeTruthy()
        })
    })
})