import { useEffect, useState } from "react"
import axios from "axios"
import { assets } from "../assets/data"

interface AgencyData {
    id: string
    name: string
    address: string
    contact: string
    email: string
    city: string
    ownerId: string
    ownerImage: string
}

const Agencies = () => {
    const [agencies, setAgencies] = useState<AgencyData[]>([])
    const [loading, setLoading] = useState(true)
    const [error, setError] = useState<string | null>(null)

    const fetchAgencies = async () => {
        try {
            setLoading(true)
            setError(null)
            const { data } = await axios.get("/api/agencies")
            setAgencies(data)
        } catch (err: any) {
            setError(err.response?.data?.message || "Failed to load agencies")
        } finally {
            setLoading(false)
        }
    }

    useEffect(() => {
        fetchAgencies()
    }, [])

    return (
        <div className='bg-linear-to-r from-[#F0FDF4] to-white py-16 pt-28'>
            <div className='max-padd-container'>
                <h2 className='h2 mb-6'>Agencies</h2>
                {loading && <p className="text-gray-500 text-center py-10">Loading agencies...</p>}
                {!loading && error && (
                    <div className="text-center py-10">
                        <p className="text-red-500 mb-3">{error}</p>
                        <button onClick={fetchAgencies} className="px-4 py-2 rounded-lg border border-slate-900/10 bg-white hover:bg-secondary/10 transition text-sm">
                            Retry
                        </button>
                    </div>
                )}
                {!loading && !error && agencies.length === 0 && (
                    <p className="text-gray-500 text-center py-10">No agencies found.</p>
                )}
                {!loading && !error && agencies.length > 0 && (
                <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-5">
                    {agencies.map((agency) => (
                        <div key={agency.id} className='bg-white ring-1 ring-slate-900/5 p-4 rounded-xl'>
                            <div className='flex items-center gap-3 mb-3'>
                                <img src={agency.ownerImage || "https://images.unsplash.com/photo-1560250097-0b93528c311a"}
                                    alt={agency.name} className='h-12 w-12 rounded-full object-cover' />
                                <div>
                                    <h5 className='h5'>{agency.name}</h5>
                                    <p className='text-xs text-gray-500'>{agency.city}</p>
                                </div>
                            </div>
                            <p className='text-sm text-gray-600 mb-2'>{agency.address}</p>
                            <div className='flex items-center gap-2 text-sm text-gray-500 mb-1'>
                                <img src={assets.phone} alt="" width={14} className="opacity-60" />
                                <span>{agency.contact}</span>
                            </div>
                            <div className='flex items-center gap-2 text-sm text-gray-500'>
                                <img src={assets.mail} alt="" width={14} className="opacity-60" />
                                <span>{agency.email}</span>
                            </div>
                        </div>
                    ))}
                </div>
                )}
            </div>
        </div>
    )
}

export default Agencies