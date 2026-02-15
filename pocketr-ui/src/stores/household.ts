import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import {
  acceptInvite as apiAcceptInvite,
  createHousehold as apiCreateHousehold,
  getHousehold as apiGetHousehold,
  inviteMember as apiInviteMember,
  leaveHousehold as apiLeaveHousehold,
  listHouseholds as apiListHouseholds,
  listSharedAccounts as apiListSharedAccounts,
  shareAccount as apiShareAccount,
  unshareAccount as apiUnshareAccount,
} from '@/api/households'
import type {
  CreateHouseholdRequest,
  Household,
  HouseholdAccountShare,
  HouseholdSummary,
  InviteMemberRequest,
} from '@/types/household'

export const useHouseholdStore = defineStore('household', () => {
  const households = ref<HouseholdSummary[]>([])
  const currentHousehold = ref<Household | null>(null)
  const sharedAccounts = ref<HouseholdAccountShare[]>([])
  const isLoading = ref(false)
  const error = ref<string | null>(null)

  const hasHousehold = computed(() => households.value.length > 0)

  const activeHouseholds = computed(() => households.value.filter((h) => h.status === 'ACTIVE'))

  const pendingInvites = computed(() => households.value.filter((h) => h.status === 'INVITED'))

  const currentMembership = computed(() => {
    if (!currentHousehold.value) return null
    return households.value.find((h) => h.id === currentHousehold.value!.id) ?? null
  })

  const isOwnerOrAdmin = computed(() => {
    const membership = currentMembership.value
    return membership?.role === 'OWNER' || membership?.role === 'ADMIN'
  })

  async function loadHouseholds(): Promise<void> {
    isLoading.value = true
    error.value = null

    try {
      households.value = await apiListHouseholds()
    } catch {
      error.value = 'Failed to load households.'
    } finally {
      isLoading.value = false
    }
  }

  async function loadHousehold(id: string): Promise<void> {
    isLoading.value = true
    error.value = null

    try {
      currentHousehold.value = await apiGetHousehold(id)
    } catch {
      error.value = 'Failed to load household details.'
    } finally {
      isLoading.value = false
    }
  }

  async function createHousehold(req: CreateHouseholdRequest): Promise<Household | null> {
    isLoading.value = true
    error.value = null

    try {
      const household = await apiCreateHousehold(req)
      currentHousehold.value = household
      await loadHouseholds()
      return household
    } catch {
      error.value = 'Failed to create household.'
      return null
    } finally {
      isLoading.value = false
    }
  }

  async function inviteMember(householdId: string, req: InviteMemberRequest): Promise<boolean> {
    error.value = null

    try {
      await apiInviteMember(householdId, req)
      await loadHousehold(householdId)
      return true
    } catch {
      error.value = 'Failed to invite member.'
      return false
    }
  }

  async function acceptInvite(householdId: string): Promise<boolean> {
    error.value = null

    try {
      await apiAcceptInvite(householdId)
      await loadHouseholds()
      return true
    } catch {
      error.value = 'Failed to accept invite.'
      return false
    }
  }

  async function leaveHousehold(householdId: string): Promise<boolean> {
    error.value = null

    try {
      await apiLeaveHousehold(householdId)
      currentHousehold.value = null
      sharedAccounts.value = []
      await loadHouseholds()
      return true
    } catch {
      error.value = 'Failed to leave household.'
      return false
    }
  }

  async function loadSharedAccounts(householdId: string): Promise<void> {
    error.value = null

    try {
      sharedAccounts.value = await apiListSharedAccounts(householdId)
    } catch {
      error.value = 'Failed to load shared accounts.'
    }
  }

  async function shareAccount(householdId: string, accountId: string): Promise<boolean> {
    error.value = null

    try {
      await apiShareAccount(householdId, { accountId })
      await loadSharedAccounts(householdId)
      return true
    } catch {
      error.value = 'Failed to share account.'
      return false
    }
  }

  async function unshareAccount(householdId: string, accountId: string): Promise<boolean> {
    error.value = null

    try {
      await apiUnshareAccount(householdId, accountId)
      await loadSharedAccounts(householdId)
      return true
    } catch {
      error.value = 'Failed to unshare account.'
      return false
    }
  }

  function $reset(): void {
    households.value = []
    currentHousehold.value = null
    sharedAccounts.value = []
    isLoading.value = false
    error.value = null
  }

  return {
    households,
    currentHousehold,
    sharedAccounts,
    isLoading,
    error,
    hasHousehold,
    activeHouseholds,
    pendingInvites,
    currentMembership,
    isOwnerOrAdmin,
    loadHouseholds,
    loadHousehold,
    createHousehold,
    inviteMember,
    acceptInvite,
    leaveHousehold,
    loadSharedAccounts,
    shareAccount,
    unshareAccount,
    $reset,
  }
})
