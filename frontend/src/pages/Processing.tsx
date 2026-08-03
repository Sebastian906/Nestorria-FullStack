import { useParams } from "react-router-dom"
import { useAppContext } from "../context/AppContext"
import { useEffect } from "react"

const Processing = () => {

    const { navigate } = useAppContext()
    const { nextUrl } = useParams()

    useEffect(() => {
        if (!nextUrl) return
        const timerId = setTimeout(() => {
            navigate(`/${nextUrl}`)
        }, 8000)
        return () => clearTimeout(timerId)
    }, [nextUrl, navigate])

    return (
        <div className='flexCenter h-screen'>
            <div className='animate-spin rounded-full h-24 w-24 border-4 border-gray-300 border-t-secondary'/>
        </div>
    )
}

export default Processing