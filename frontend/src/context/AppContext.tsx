import { createContext, useContext, useEffect, useState, type ReactNode, type Dispatch, type SetStateAction } from "react"
import { useNavigate, type NavigateFunction } from "react-router-dom"
import { type Property } from "../assets/data"
import { useAuth, useUser } from "@clerk/react"
import axios from "axios"
import toast from "react-hot-toast"

const backendUrl = (import.meta.env.VITE_BACKEND_URL || 'http://127.0.0.1:4000').replace(/\/$/, '')
axios.defaults.baseURL = backendUrl

export interface SearchedCity {
    city: string
    searchedAt: string
}

interface AppContextType {
    navigate: NavigateFunction;
    properties: Property[];
    currency: string;
    user: any;
    isOwner: boolean;
    searchedCities: SearchedCity[];
    setSearchedCities: Dispatch<SetStateAction<SearchedCity[]>>;
    showAgencyReg: boolean;
    setShowAgencyReg: (show: boolean) => void;
    refreshProfile: () => Promise<void>;
    searchQuery: string;
    setSearchQuery: (query: string) => void;
    favoriteIds: Set<string>;
    toggleFavorite: (propertyId: string) => Promise<boolean>;
}

const AppContext = createContext<AppContextType | undefined>(undefined)

export const AppContextProvider = ({ children }: { children: ReactNode }) => {
    const currency: string = import.meta.env.VITE_CURRENCY ?? '$'
    const navigate = useNavigate()
    const { user } = useUser()
    const { getToken } = useAuth()
    const [properties, setProperties] = useState<Property[]>([])
    const [showAgencyReg, setShowAgencyReg] = useState<boolean>(false)
    const [isOwner, setIsOwner] = useState<boolean>(false)
    const [searchedCities, setSearchedCities] = useState<SearchedCity[]>([])
    const [searchQuery, setSearchQuery] = useState<string>('')
    const [favoriteIds, setFavoriteIds] = useState<Set<string>>(new Set())

    const getProperties = async () => {
        try {
            const { data } = await axios.get('/api/properties/me');

            const mappedProperties = data.map((prop: any) => ({
                ...prop,
                _id: prop.id,
                agency: prop.agency ? {
                    ...prop.agency,
                    owner: {
                        image: prop.agency.ownerImage || "https://images.unsplash.com/photo-1560250097-0b93528c311a"
                    }
                } : null,
                averageRating: prop.averageRating ?? null,
                reviewCount: prop.reviewCount ?? 0
            }));

            setProperties(mappedProperties);
        } catch (error: any) {
            // Silenciar errores de red en carga inicial (puede ser offline)
            if (error.code === 'ERR_NETWORK') {
                console.warn('Network error loading properties');
            } else {
                toast.error('No se pudieron cargar las propiedades');
            }
        }
    };

    const getUserProfile = async () => {
        try {
            const token = await getToken()
            if (!token) {
                return
            }
            const { data } = await axios.get('/api/users/me', {
                headers: { Authorization: `Bearer ${token}` }
            })
            setIsOwner(data.role === 'AGENCY_OWNER')
            setSearchedCities(
                (data.recentSearchedCities ?? []).map((city: string) => ({
                    city,
                    searchedAt: new Date().toISOString()
                }))
            )
        } catch (error: any) {
            toast.error(error?.response?.data?.message ?? 'No se pudo cargar tu perfil')
        }
    }

    const loadFavorites = async () => {
        try {
            const token = await getToken()
            if (!token) {
                setFavoriteIds(new Set())
                return
            }
            const { data } = await axios.get('/api/favorites/me', {
                headers: { Authorization: `Bearer ${token}` }
            })
            const ids = new Set<string>(data.map((fav: any) => fav.propertyId))
            setFavoriteIds(ids)
        } catch (error: any) {
            // Silenciar errores de red
            if (error.code !== 'ERR_NETWORK') {
                console.warn('Error loading favorites')
            }
        }
    }

    const toggleFavorite = async (propertyId: string): Promise<boolean> => {
        try {
            const token = await getToken()
            if (!token) {
                toast.error('Inicia sesión para agregar favoritos')
                return false
            }
            const { data } = await axios.post(`/api/properties/${propertyId}/favorite`, {}, {
                headers: { Authorization: `Bearer ${token}` }
            })
            const favorited: boolean = data.favorited

            setFavoriteIds(prev => {
                const next = new Set(prev)
                if (favorited) {
                    next.add(propertyId)
                } else {
                    next.delete(propertyId)
                }
                return next
            })

            toast.success(favorited ? 'Agregado a favoritos' : 'Eliminado de favoritos')
            return favorited
        } catch (error: any) {
            toast.error(error.response?.data?.message || 'No se pudo actualizar favorito')
            return false
        }
    }

    useEffect(() => {
        getProperties()
    }, [])

    useEffect(() => {
        if (user) {
            getUserProfile()
            loadFavorites()
        } else {
            setIsOwner(false)
            setSearchedCities([])
            setFavoriteIds(new Set())
        }
    }, [user])

    const value: AppContextType = {
        navigate,
        properties,
        currency,
        user,
        isOwner,
        searchedCities,
        setSearchedCities,
        showAgencyReg,
        setShowAgencyReg,
        refreshProfile: getUserProfile,
        searchQuery,
        setSearchQuery,
        favoriteIds,
        toggleFavorite,
    }

    return (
        <AppContext.Provider value={value}>
            {children}
        </AppContext.Provider>
    )
}

export const useAppContext = () => {
    const context = useContext(AppContext)
    if (!context) {
        throw new Error("useAppContext debe ser utilizado dentro de un AppContextProvider")
    }
    return context
}