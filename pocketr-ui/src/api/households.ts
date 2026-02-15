import { api } from '@/api/http'
import type {
  CreateHouseholdRequest,
  Household,
  HouseholdAccountShare,
  HouseholdMember,
  HouseholdSummary,
  InviteMemberRequest,
  ShareAccountRequest,
} from '@/types/household'

const BASE = '/api/v1/households'

export function createHousehold(req: CreateHouseholdRequest): Promise<Household> {
  return api.post(BASE, { json: req }).json<Household>()
}

export function listHouseholds(): Promise<HouseholdSummary[]> {
  return api.get(BASE).json<HouseholdSummary[]>()
}

export function getHousehold(id: string): Promise<Household> {
  return api.get(`${BASE}/${id}`).json<Household>()
}

export function inviteMember(
  householdId: string,
  req: InviteMemberRequest,
): Promise<HouseholdMember> {
  return api.post(`${BASE}/${householdId}/invite`, { json: req }).json<HouseholdMember>()
}

export function acceptInvite(householdId: string): Promise<HouseholdMember> {
  return api.post(`${BASE}/${householdId}/accept-invite`).json<HouseholdMember>()
}

export function leaveHousehold(householdId: string): Promise<void> {
  return api.post(`${BASE}/${householdId}/leave`).then(() => undefined)
}

export function shareAccount(
  householdId: string,
  req: ShareAccountRequest,
): Promise<HouseholdAccountShare> {
  return api.post(`${BASE}/${householdId}/shares`, { json: req }).json<HouseholdAccountShare>()
}

export function unshareAccount(householdId: string, accountId: string): Promise<void> {
  return api.delete(`${BASE}/${householdId}/shares/${accountId}`).then(() => undefined)
}

export function listSharedAccounts(householdId: string): Promise<HouseholdAccountShare[]> {
  return api.get(`${BASE}/${householdId}/shares`).json<HouseholdAccountShare[]>()
}
