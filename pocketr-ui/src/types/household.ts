export type HouseholdRole = 'OWNER' | 'ADMIN' | 'MEMBER'
export type MembershipStatus = 'INVITED' | 'ACTIVE'

export interface Household {
  id: string
  name: string
  createdAt: string
  members: HouseholdMember[]
}

export interface HouseholdSummary {
  id: string
  name: string
  role: HouseholdRole
  status: MembershipStatus
  createdAt: string
}

export interface HouseholdMember {
  userId: number
  email: string
  firstName: string | null
  lastName: string | null
  role: HouseholdRole
  status: MembershipStatus
  joinedAt: string | null
}

export interface HouseholdAccountShare {
  accountId: string
  accountName: string
  ownerEmail: string
  ownerFirstName: string | null
  ownerLastName: string | null
  sharedAt: string
}

export interface CreateHouseholdRequest {
  name: string
}

export interface InviteMemberRequest {
  email: string
}

export interface ShareAccountRequest {
  accountId: string
}
