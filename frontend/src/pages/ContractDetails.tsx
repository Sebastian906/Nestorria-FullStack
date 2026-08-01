import { useEffect, useState, useCallback } from "react"
import { useParams, useNavigate } from "react-router-dom"
import { useAuth, useUser } from "@clerk/react"
import axios from "axios"
import toast from "react-hot-toast"
import { assets, type Contract, type SignatureRole } from "../assets/data"

const ContractDetails = () => {
    const { id } = useParams<{ id: string }>()
    const navigate = useNavigate()
    const { getToken } = useAuth()
    const { user } = useUser()

    const [contract, setContract] = useState<Contract | null>(null)
    const [loading, setLoading] = useState(true)
    const [signing, setSigning] = useState(false)
    const [expandedClauses, setExpandedClauses] = useState<Set<string>>(new Set())

    const fetchContract = useCallback(async () => {
        if (!id) return
        setLoading(true)
        try {
            const token = await getToken()
            const { data } = await axios.get(`/api/contracts/${id}`, {
                headers: { Authorization: `Bearer ${token}` }
            })
            setContract(data)
        } catch (error: any) {
            const message = error.response?.data?.message || error.message
            if (error.response?.status === 403) {
                toast.error("You don't have access to this contract")
                navigate("/my-bookings")
            } else if (error.response?.status === 404) {
                toast.error("Contract not found")
                navigate("/my-bookings")
            } else {
                toast.error(message)
            }
        } finally {
            setLoading(false)
        }
    }, [id, getToken, navigate])

    useEffect(() => {
        if (user && id) {
            fetchContract()
        }
    }, [user, id, fetchContract])

    const handleSign = async (role: SignatureRole) => {
        if (!id || !contract) return
        setSigning(true)
        try {
            const token = await getToken()
            const { data } = await axios.post(
                `/api/contracts/${id}/sign`,
                { role },
                { headers: { Authorization: `Bearer ${token}` } }
            )
            setContract(data)
            if (data.status === "SIGNED") {
                toast.success("Contract signed by both parties. Successfully signed!")
            } else {
                toast.success("Signature registered successfully")
            }
        } catch (error: any) {
            const message = error.response?.data?.message || error.message
            if (error.response?.status === 409) {
                toast.error("You have already signed this contract")
                fetchContract()
            } else {
                toast.error(message)
            }
        } finally {
            setSigning(false)
        }
    }

    const toggleClause = (clauseId: string) => {
        setExpandedClauses(prev => {
            const next = new Set(prev)
            if (next.has(clauseId)) { next.delete(clauseId) } else { next.add(clauseId) }
            return next
        })
    }

    const expandAllClauses = () => {
        if (!contract) return
        if (expandedClauses.size === contract.clauses.length) {
            setExpandedClauses(new Set())
        } else {
            setExpandedClauses(new Set(contract.clauses.map(c => c.id)))
        }
    }

    const getStatusConfig = (status: string) => {
        switch (status) {
            case "DRAFT":
                return { label: "Draft", bgColor: "bg-gray-100", textColor: "text-gray-600", dotColor: "bg-gray-400" }
            case "PENDING_SIGNATURE":
                return { label: "Pending Signatures", bgColor: "bg-yellow-50", textColor: "text-yellow-700", dotColor: "bg-yellow-500" }
            case "SIGNED":
                return { label: "Signed", bgColor: "bg-green-50", textColor: "text-green-700", dotColor: "bg-green-500" }
            case "EXPIRED":
                return { label: "Expired", bgColor: "bg-red-50", textColor: "text-red-700", dotColor: "bg-red-500" }
            default:
                return { label: status, bgColor: "bg-gray-100", textColor: "text-gray-600", dotColor: "bg-gray-400" }
        }
    }

    const getContractTypeLabel = (type: string) => {
        switch (type) {
            case "RENTAL": return "Rental Contract"
            case "PURCHASE": return "Purchase Contract"
            default: return type
        }
    }

    const isUserSigned = (): boolean => {
        if (!contract || !user) return false
        return contract.signatures.some(s => s.userId === user.id)
    }

    const getUserRole = (): SignatureRole | null => {
        if (!user) return null
        // Determinar el rol basado en las firmas existentes
        const tenantSig = contract?.signatures.find(s => s.role === "TENANT")
        const agencySig = contract?.signatures.find(s => s.role === "AGENCY_OWNER")

        // Si ya hay una firma, determinar quién falta
        if (!tenantSig) return "TENANT"
        if (!agencySig) return "AGENCY_OWNER"
        return null
    }

    if (loading) {
        return (
            <div className='bg-linear-to-r from-[#F0FDF4] to-white py-16 pt-28 w-full min-h-screen'>
                <div className='max-padd-container'>
                    <p className="text-gray-500 text-center py-10">Loading contract...</p>
                </div>
            </div>
        )
    }

    if (!contract) {
        return (
            <div className='bg-linear-to-r from-[#F0FDF4] to-white py-16 pt-28 w-full min-h-screen'>
                <div className='max-padd-container'>
                    <p className="text-gray-500 text-center py-10">Contract not found</p>
                </div>
            </div>
        )
    }

    const statusConfig = getStatusConfig(contract.status)
    const userSigned = isUserSigned()
    const canSign = contract.status === "PENDING_SIGNATURE" && !userSigned
    const userRole = getUserRole()

    return (
        <div className='bg-linear-to-r from-[#F0FDF4] to-white py-16 pt-28 w-full min-h-screen'>
            <div className='max-w-4xl mx-auto px-4'>
                {/* Back Button */}
                <button
                    onClick={() => { navigate('/my-bookings'); scrollTo(0, 0) }}
                    className='flex items-center gap-2 text-sm text-gray-500 hover:text-gray-700 mb-6 transition-colors'
                >
                    <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                        <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M15 19l-7-7 7-7" />
                    </svg>
                    Back to My Bookings
                </button>

                {/* Header Card */}
                <div className='bg-white rounded-xl border border-slate-900/10 p-6 mb-6'>
                    <div className='flex flex-col sm:flex-row sm:items-center justify-between gap-4'>
                        <div>
                            <h2 className='h2 mb-1'>Rental Contract</h2>
                            <p className='text-sm text-gray-500'>
                                {getContractTypeLabel(contract.contractType)}
                            </p>
                        </div>
                        <div className={`flex items-center gap-2 px-4 py-2 rounded-full ${statusConfig.bgColor}`}>
                            <span className={`w-2.5 h-2.5 rounded-full ${statusConfig.dotColor}`} />
                            <span className={`text-sm font-medium ${statusConfig.textColor}`}>
                                {statusConfig.label}
                            </span>
                        </div>
                    </div>

                    {/* Contract Meta */}
                    <div className='grid grid-cols-2 sm:grid-cols-4 gap-4 mt-6 pt-4 border-t border-slate-900/10'>
                        <div>
                            <p className='text-xs text-gray-400 mb-1'>Contract ID</p>
                            <p className='text-sm font-medium break-all'>{contract.id}</p>
                        </div>
                        <div>
                            <p className='text-xs text-gray-400 mb-1'>Booking ID</p>
                            <p className='text-sm font-medium break-all'>{contract.bookingId}</p>
                        </div>
                        <div>
                            <p className='text-xs text-gray-400 mb-1'>Generated</p>
                            <p className='text-sm font-medium'>
                                {contract.generatedAt
                                    ? new Date(contract.generatedAt).toLocaleDateString('en-US', {
                                        year: 'numeric', month: 'long', day: 'numeric',
                                        hour: '2-digit', minute: '2-digit'
                                    })
                                    : '—'}
                            </p>
                        </div>
                        <div>
                            <p className='text-xs text-gray-400 mb-1'>Created</p>
                            <p className='text-sm font-medium'>
                                {new Date(contract.createdAt).toLocaleDateString('en-US', {
                                    year: 'numeric', month: 'long', day: 'numeric'
                                })}
                            </p>
                        </div>
                    </div>
                </div>

                {/* Clauses Section */}
                <div className='bg-white rounded-xl border border-slate-900/10 p-6 mb-6'>
                    <div className='flex items-center justify-between mb-4'>
                        <h3 className='h3'>Contract Clauses</h3>
                        <button
                            onClick={expandAllClauses}
                            className='text-sm text-secondary hover:text-secondary/80 transition-colors'
                        >
                            {expandedClauses.size === contract.clauses.length
                                ? 'Collapse all'
                                : 'Expand all'}
                        </button>
                    </div>
                    <div className='space-y-3'>
                        {contract.clauses.map((clause) => {
                            const isExpanded = expandedClauses.has(clause.id)
                            return (
                                <div
                                    key={clause.id}
                                    className='border border-slate-900/10 rounded-lg overflow-hidden'
                                >
                                    <button
                                        onClick={() => toggleClause(clause.id)}
                                        className='w-full flex items-center justify-between p-4 text-left hover:bg-gray-50 transition-colors'
                                    >
                                        <div className='flex items-center gap-3'>
                                            <span className='flex items-center justify-center w-7 h-7 rounded-full bg-secondary/10 text-secondary text-sm font-bold'>
                                                {clause.sortOrder}
                                            </span>
                                            <span className='font-medium text-sm'>
                                                {clause.title}
                                            </span>
                                        </div>
                                        <svg
                                            className={`w-5 h-5 text-gray-400 transition-transform ${isExpanded ? 'rotate-180' : ''
                                                }`}
                                            fill="none"
                                            stroke="currentColor"
                                            viewBox="0 0 24 24"
                                        >
                                            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M19 9l-7 7-7-7" />
                                        </svg>
                                    </button>
                                    {isExpanded && (
                                        <div className='px-4 pb-4 pt-0'>
                                            <div className='pl-10 text-sm text-gray-600 leading-relaxed whitespace-pre-line'>
                                                {clause.content}
                                            </div>
                                        </div>
                                    )}
                                </div>
                            )
                        })}
                    </div>
                </div>

                {/* Signatures Section */}
                <div className='bg-white rounded-xl border border-slate-900/10 p-6 mb-6'>
                    <h3 className='h3 mb-4'>Digital Signatures</h3>

                    {contract.signatures.length === 0 ? (
                        <p className='text-sm text-gray-500 py-4'>
                            No signatures have been registered yet.
                        </p>
                    ) : (
                        <div className='space-y-3'>
                            {contract.signatures.map((signature) => (
                                <div
                                    key={signature.id}
                                    className='flex items-center gap-4 p-4 rounded-lg bg-green-50/50 border border-green-200/50'
                                >
                                    <div className='flex items-center justify-center w-10 h-10 rounded-full bg-green-100'>
                                        <svg className="w-5 h-5 text-green-600" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                                            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M5 13l4 4L19 7" />
                                        </svg>
                                    </div>
                                    <div className='flex-1'>
                                        <div className='flex items-center gap-2'>
                                            <span className='font-medium text-sm'>{signature.userName}</span>
                                            <span className={`text-xs px-2 py-0.5 rounded-full ${signature.role === 'TENANT'
                                                    ? 'bg-blue-100 text-blue-700'
                                                    : 'bg-purple-100 text-purple-700'
                                                }`}>
                                                {signature.role === 'TENANT' ? 'Tenant' : 'Agency'}
                                            </span>
                                        </div>
                                        <p className='text-xs text-gray-500 mt-0.5'>
                                            Signed on {new Date(signature.signedAt).toLocaleDateString('en-US', {
                                                year: 'numeric', month: 'long', day: 'numeric',
                                                hour: '2-digit', minute: '2-digit'
                                            })}
                                        </p>
                                    </div>
                                </div>
                            ))}
                        </div>
                    )}

                    {/* Signing Progress */}
                    {contract.status !== "DRAFT" && (
                        <div className='mt-4 pt-4 border-t border-slate-900/10'>
                            <div className='flex items-center gap-4'>
                                <div className='flex items-center gap-2'>
                                    <span className={`w-3 h-3 rounded-full ${contract.signatures.some(s => s.role === 'TENANT')
                                            ? 'bg-green-500'
                                            : 'bg-gray-300'
                                        }`} />
                                    <span className='text-sm'>
                                        Tenant: {contract.signatures.some(s => s.role === 'TENANT')
                                            ? 'Signed'
                                            : 'Pending'}
                                    </span>
                                </div>
                                <div className='flex items-center gap-2'>
                                    <span className={`w-3 h-3 rounded-full ${contract.signatures.some(s => s.role === 'AGENCY_OWNER')
                                            ? 'bg-green-500'
                                            : 'bg-gray-300'
                                        }`} />
                                    <span className='text-sm'>
                                        Agency: {contract.signatures.some(s => s.role === 'AGENCY_OWNER')
                                            ? 'Signed'
                                            : 'Pending'}
                                    </span>
                                </div>
                            </div>
                        </div>
                    )}
                </div>

                {/* Sign Button */}
                {canSign && userRole && (
                    <div className='bg-white rounded-xl border border-slate-900/10 p-6 mb-6'>
                        <div className='text-center'>
                            <div className='flex items-center justify-center w-16 h-16 rounded-full bg-secondary/10 mx-auto mb-4'>
                                <img src={assets.signature} alt="" width={32} className="opacity-70" />
                            </div>
                            <h4 className='h4 mb-2'>Signature Pending</h4>
                            <p className='text-sm text-gray-500 mb-6 max-w-md mx-auto'>
                                {userRole === 'TENANT'
                                    ? 'As a tenant, your signature is necessary to complete this contract.'
                                    : 'As a representative of the agency, your signature is necessary to complete this contract.'}
                            </p>
                            <button
                                onClick={() => handleSign(userRole)}
                                disabled={signing}
                                className='btn-secondary px-8 py-3 text-base disabled:opacity-50'
                            >
                                {signing ? (
                                    <span className='flex items-center gap-2'>
                                        <svg className="animate-spin w-4 h-4" viewBox="0 0 24 24">
                                            <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4" fill="none" />
                                            <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z" />
                                        </svg>
                                        Signing...
                                    </span>
                                ) : (
                                    'Sign Contract'
                                )}
                            </button>
                        </div>
                    </div>
                )}

                {/* Already Signed Message */}
                {userSigned && contract.status !== "SIGNED" && (
                    <div className='bg-green-50 rounded-xl border border-green-200 p-6 mb-6 text-center'>
                        <p className='text-sm text-green-700'>
                            You have already signed this contract. Waiting for the signature of the other party.
                        </p>
                    </div>
                )}

                {/* Fully Signed */}
                {contract.status === "SIGNED" && (
                    <div className='bg-green-50 rounded-xl border border-green-200 p-6 mb-6 text-center'>
                        <div className='flex items-center justify-center w-16 h-16 rounded-full bg-green-100 mx-auto mb-4'>
                            <svg className="w-8 h-8 text-green-600" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9 12l2 2 4-4m6 2a9 9 0 11-18 0 9 9 0 0118 0z" />
                            </svg>
                        </div>
                        <h4 className='h4 text-green-800 mb-2'>Contract Completed</h4>
                        <p className='text-sm text-green-700'>
                            Both parties have signed. This contract is officially formalized.
                        </p>
                    </div>
                )}

                {/* Cash on Delivery Payment Info */}
                <div className='bg-white rounded-xl border border-slate-900/10 p-6'>
                    <h3 className='h3 mb-4'>Payment Method</h3>
                    <div className='flex items-center gap-4 p-4 bg-secondary/5 rounded-lg'>
                        <div className='flex items-center justify-center w-12 h-12 rounded-full bg-secondary/10'>
                            <svg className="w-6 h-6 text-secondary" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M17 9V7a2 2 0 00-2-2H5a2 2 0 00-2 2v6a2 2 0 002 2h2m2 4h10a2 2 0 002-2v-6a2 2 0 00-2-2H9a2 2 0 00-2 2v6a2 2 0 002 2zm7-5a2 2 0 11-4 0 2 2 0 014 0z" />
                            </svg>
                        </div>
                        <div>
                            <h5 className='font-medium text-sm'>Cash on Delivery</h5>
                            <p className='text-xs text-gray-500'>
                                The payment will be made in cash at the time of delivery.
                                Coming soon: payment gateway with Stripe.
                            </p>
                        </div>
                    </div>
                </div>
            </div>
        </div>
    )
}

export default ContractDetails