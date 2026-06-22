import { createContext, useContext, useEffect, useState, type ReactNode } from "react"
import { useNavigate, type NavigateFunction } from "react-router-dom"
import { dummyProperties, type Property } from "../assets/data"
import { useAuth, useUser } from "@clerk/react"
import axios from "axios"
import toast from "react-hot-toast"

axios.defaults.baseURL = import.meta.env.VITE_BACKEND_URL
interface AppContextType {
    navigate: NavigateFunction;
    properties: Property[];
    currency: string;
    user: any;
    isOwner: boolean;
    searchedCities: string[];
    showAgencyReg: boolean;
    setShowAgencyReg: (show: boolean) => void;
}

const AppContext = createContext<AppContextType | undefined>(undefined)

// interface AppContextProviderProps {
//     children: ReactNode;
// }

export const AppContextProvider = ({ children }: { children: ReactNode }) => {
    const currency: string = import.meta.env.VITE_CURRENCY ?? '$'
    const navigate = useNavigate()
    const { user } = useUser()
    const { getToken } = useAuth()
    const [properties, setProperties] = useState<Property[]>([])
    const [showAgencyReg, setShowAgencyReg] = useState<boolean>(false)
    const [isOwner, setIsOwner] = useState<boolean>(false)
    const [searchedCities, setSearchedCities] = useState<string[]>([])

    const getProperties = () => setProperties(dummyProperties)

    const getUserProfile = async () => {
        try {
            const token = await getToken()
            const { data } = await axios.get('/api/users/me', {
                headers: { Authorization: `Bearer ${token}` }
            })
            setIsOwner(data.role === 'AGENCY_OWNER')
            setSearchedCities(data.recentSearchedCities ?? [])
        } catch (error: any) {
            toast.error(error?.response?.data?.message ?? 'No se pudo cargar tu perfil')
        }
    }

    useEffect(() => {
        getProperties()
    }, [])

    useEffect(() => {
        if (user) {
            getUserProfile()
        } else {
            setIsOwner(false)
            setSearchedCities([])
        }
    }, [user])

    const value: AppContextType = {
        navigate,
        properties,
        currency,
        user,
        isOwner,
        searchedCities,
        showAgencyReg,
        setShowAgencyReg
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