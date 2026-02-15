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
  username: string
  role: HouseholdRole
  status: MembershipStatus
  joinedAt: string | null
}

export interface HouseholdAccountShare {
  accountId: string
  accountName: string
  ownerUsername: string
  sharedAt: string
}

export interface CreateHouseholdRequest {
  name: string
}

export interface InviteMemberRequest {
  username: string
  role: 'ADMIN' | 'MEMBER'
}

export interface ShareAccountRequest {
  accountId: string
}
