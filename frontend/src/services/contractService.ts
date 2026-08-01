import axios from "axios"
import { useAuth } from "@clerk/react"
import type { Contract, ContractSummary, ContractType, SignatureRole } from "../assets/data"

const API_BASE = import.meta.env.VITE_BACKEND_URL

export function useContractService() {
    const { getToken } = useAuth()

    async function createContract(bookingId: string, contractType: ContractType): Promise<Contract> {
        const token = await getToken()
        const { data } = await axios.post(
            `${API_BASE}/api/contracts`,
            { bookingId, contractType },
            { headers: { Authorization: `Bearer ${token}` } }
        )
        return data
    }

    async function getContract(contractId: string): Promise<Contract> {
        const token = await getToken()
        const { data } = await axios.get(
            `${API_BASE}/api/contracts/${contractId}`,
            { headers: { Authorization: `Bearer ${token}` } }
        )
        return data
    }

    async function signContract(contractId: string, role: SignatureRole): Promise<Contract> {
        const token = await getToken()
        const { data } = await axios.post(
            `${API_BASE}/api/contracts/${contractId}/sign`,
            { role },
            { headers: { Authorization: `Bearer ${token}` } }
        )
        return data
    }

    async function getUserContracts(): Promise<ContractSummary[]> {
        const token = await getToken()
        const { data } = await axios.get(
            `${API_BASE}/api/contracts/me`,
            { headers: { Authorization: `Bearer ${token}` } }
        )
        return data
    }

    async function getAgencyContracts(): Promise<ContractSummary[]> {
        const token = await getToken()
        const { data } = await axios.get(
            `${API_BASE}/api/contracts/agency`,
            { headers: { Authorization: `Bearer ${token}` } }
        )
        return data
    }

    return { createContract, getContract, signContract, getUserContracts, getAgencyContracts }
}