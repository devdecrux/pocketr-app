import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import { HTTPError } from 'ky'
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
  updateHouseholdRolloverDay as apiUpdateHouseholdRolloverDay,
} from '@/api/households'
import type {
  CreateHouseholdRequest,
  Household,
  HouseholdAccountShare,
  HouseholdSummary,
  InviteMemberRequest,
} from '@/types/household'
import { useModeStore } from '@/stores/mode'
import { translate } from '@/i18n/translate'

export const useHouseholdStore = defineStore('household', () => {
  const households = ref<HouseholdSummary[]>([])
  const currentHousehold = ref<Household | null>(null)
  const sharedAccounts = ref<HouseholdAccountShare[]>([])
  const isLoading = ref(false)
  const error = ref<string | null>(null)
  const modeValidated = ref(false)
  let ensureModeValidationPromise: Promise<void> | null = null

  const activeHouseholds = computed(() => households.value.filter((h) => h.status === 'ACTIVE'))

  const hasHousehold = computed(() => activeHouseholds.value.length > 0)

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

      // If the persisted mode references a household the user is no longer an active member
      // of (e.g. stale localStorage after a DB reset or household leave), reset to INDIVIDUAL
      // so subsequent data loads don't get a 403.
      const modeStore = useModeStore()
      if (modeStore.isHousehold && modeStore.householdId) {
        const isValid = households.value.some(
          (h) => h.id === modeStore.householdId && h.status === 'ACTIVE',
        )
        if (!isValid) {
          modeStore.switchToIndividual()
        }
      }
      modeValidated.value = true
    } catch (nextError: unknown) {
      error.value = await resolveErrorMessage(nextError, translate('errors.households.load'))
    } finally {
      isLoading.value = false
    }
  }

  async function ensureModeValidated(): Promise<void> {
    if (modeValidated.value) {
      return
    }

    if (!ensureModeValidationPromise) {
      ensureModeValidationPromise = loadHouseholds().finally(() => {
        ensureModeValidationPromise = null
      })
    }

    await ensureModeValidationPromise
  }

  async function loadHousehold(id: string): Promise<void> {
    isLoading.value = true
    error.value = null

    try {
      currentHousehold.value = await apiGetHousehold(id)
    } catch (nextError: unknown) {
      error.value = await resolveErrorMessage(nextError, translate('errors.households.loadDetails'))
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
    } catch (nextError: unknown) {
      error.value = await resolveErrorMessage(nextError, translate('errors.households.create'))
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
    } catch (nextError: unknown) {
      error.value = await resolveErrorMessage(
        nextError,
        translate('errors.households.inviteMember'),
      )
      return false
    }
  }

  async function acceptInvite(householdId: string): Promise<boolean> {
    error.value = null

    try {
      await apiAcceptInvite(householdId)
      await loadHouseholds()
      return true
    } catch (nextError: unknown) {
      error.value = await resolveErrorMessage(
        nextError,
        translate('errors.households.acceptInvite'),
      )
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
    } catch (nextError: unknown) {
      error.value = await resolveErrorMessage(nextError, translate('errors.households.leave'))
      return false
    }
  }

  async function updateRolloverDay(householdId: string, rolloverDay: number): Promise<boolean> {
    error.value = null

    try {
      currentHousehold.value = await apiUpdateHouseholdRolloverDay(householdId, { rolloverDay })
      await loadHouseholds()
      return true
    } catch (nextError: unknown) {
      error.value = await resolveErrorMessage(
        nextError,
        translate('errors.households.updateRollover'),
      )
      return false
    }
  }

  async function loadSharedAccounts(householdId: string): Promise<void> {
    error.value = null

    try {
      sharedAccounts.value = await apiListSharedAccounts(householdId)
    } catch (nextError: unknown) {
      error.value = await resolveErrorMessage(
        nextError,
        translate('errors.households.loadSharedAccounts'),
      )
    }
  }

  async function shareAccount(householdId: string, accountId: string): Promise<boolean> {
    error.value = null

    try {
      await apiShareAccount(householdId, { accountId })
      await loadSharedAccounts(householdId)
      return true
    } catch (nextError: unknown) {
      error.value = await resolveErrorMessage(
        nextError,
        translate('errors.households.shareAccount'),
      )
      return false
    }
  }

  async function unshareAccount(householdId: string, accountId: string): Promise<boolean> {
    error.value = null

    try {
      await apiUnshareAccount(householdId, accountId)
      await loadSharedAccounts(householdId)
      return true
    } catch (nextError: unknown) {
      error.value = await resolveErrorMessage(
        nextError,
        translate('errors.households.unshareAccount'),
      )
      return false
    }
  }

  function $reset(): void {
    households.value = []
    currentHousehold.value = null
    sharedAccounts.value = []
    isLoading.value = false
    error.value = null
    modeValidated.value = false
    ensureModeValidationPromise = null
  }

  async function resolveErrorMessage(nextError: unknown, fallback: string): Promise<string> {
    if (nextError instanceof HTTPError) {
      const payload = await nextError.response.json<{ message?: string }>().catch(() => null)
      if (payload?.message?.trim()) {
        return payload.message.trim()
      }
    }
    return fallback
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
    ensureModeValidated,
    loadHousehold,
    createHousehold,
    inviteMember,
    acceptInvite,
    leaveHousehold,
    updateRolloverDay,
    loadSharedAccounts,
    shareAccount,
    unshareAccount,
    $reset,
  }
})
