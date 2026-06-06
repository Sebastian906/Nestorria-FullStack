import { createContext, useContext, useEffect, useState, type ReactNode } from "react"
import { useNavigate, type NavigateFunction } from "react-router-dom"
import { dummyProperties, type Property } from "../assets/data"
import { useUser } from "@clerk/react"

interface AppContextType {
    navigate: NavigateFunction;
    properties: Property[];
    currency: string;
    user: any; // Replace 'any' with the actual user type if available
}

const AppContext = createContext<AppContextType | undefined>(undefined)

interface AppContextProviderProps {
    children: ReactNode;
}

export const AppContextProvider = ({ children }: AppContextProviderProps) => {
    const currency = import.meta.env.VITE_APP_CURRENCY
    const navigate = useNavigate()
    const { user } = useUser()
    const [properties, setProperties] = useState<Property[]>([])

    const getProperties = () => {
        setProperties(dummyProperties)
    }

    useEffect(() => {
        getProperties()
    }, [])

    const value: AppContextType = {
        navigate,
        properties,
        currency,
        user
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