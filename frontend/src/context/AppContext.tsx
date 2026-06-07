import { createContext, useContext, useEffect, useState, type ReactNode } from "react"
import { useNavigate, type NavigateFunction } from "react-router-dom"
import { dummyProperties, type Property } from "../assets/data"
import { useUser } from "@clerk/react"

interface AppContextType {
    navigate: NavigateFunction;
    properties: Property[];
    currency: string;
    user: any;
    showAgencyReg: boolean;
    setShowAgencyReg: (show: boolean) => void;
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
    const [showAgencyReg, setShowAgencyReg] = useState<boolean>(false)

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
        user,
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