import { createContext, useContext, useEffect, useState, type ReactNode } from "react"
import { useNavigate, type NavigateFunction } from "react-router-dom"
import { dummyProperties, type Property } from "../assets/data"

interface AppContextType {
    navigate: NavigateFunction;
    properties: Property[];
}

const AppContext = createContext<AppContextType | undefined>(undefined)

interface AppContextProviderProps {
    children: ReactNode;
}

export const AppContextProvider = ({ children }: AppContextProviderProps) => {
    const navigate = useNavigate()

    const [properties, setProperties] = useState<Property[]>([])

    const getProperties = () => {
        setProperties(dummyProperties)
    }

    useEffect(() => {
        getProperties()
    }, [])

    const value: AppContextType = {
        navigate,
        properties
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