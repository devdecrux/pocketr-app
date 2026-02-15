<script setup lang="ts">
import { HTTPError } from 'ky'
import { computed, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { useAccountStore } from '@/stores/account'
import { useAuthStore } from '@/stores/auth'
import { useHouseholdStore } from '@/stores/household'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Separator } from '@/components/ui/separator'
import type { HouseholdRole } from '@/types/household'
import type { AccountType } from '@/types/ledger'

const route = useRoute()
const householdStore = useHouseholdStore()
const accountStore = useAccountStore()
const authStore = useAuthStore()

const householdId = computed(() => route.params.householdId as string)

const inviteEmail = ref('')
const isInviting = ref(false)
const inviteError = ref('')
const inviteSuccess = ref('')

const sharingAccountId = ref<string | null>(null)

onMounted(async () => {
  await Promise.all([
    householdStore.loadHousehold(householdId.value),
    householdStore.loadSharedAccounts(householdId.value),
    accountStore.load(),
  ])
})

function roleBadgeVariant(role: HouseholdRole) {
  if (role === 'OWNER') return 'default' as const
  if (role === 'ADMIN') return 'secondary' as const
  return 'outline' as const
}

function statusBadgeVariant(status: string) {
  return status === 'ACTIVE' ? ('default' as const) : ('secondary' as const)
}

// Own accounts grouped by type for sharing toggles
const myAccounts = computed(() => {
  const userId = authStore.user?.id
  if (userId == null) return []
  return accountStore.activeAccounts.filter((a) => a.ownerUserId === userId)
})

const myAccountsByType = computed(() => {
  const map = new Map<AccountType, typeof myAccounts.value>()
  for (const a of myAccounts.value) {
    const list = map.get(a.type) ?? []
    list.push(a)
    map.set(a.type, list)
  }
  return map
})

const sharedAccountIds = computed(() => {
  return new Set(householdStore.sharedAccounts.map((s) => s.accountId))
})

function isAccountShared(accountId: string): boolean {
  return sharedAccountIds.value.has(accountId)
}

function sharedAtForAccount(accountId: string): string | null {
  const share = householdStore.sharedAccounts.find((s) => s.accountId === accountId)
  return share ? new Date(share.sharedAt).toLocaleDateString() : null
}

function displayPerson(
  firstName: string | null | undefined,
  lastName: string | null | undefined,
  email: string | null | undefined,
): string {
  const name = `${firstName?.trim() ?? ''} ${lastName?.trim() ?? ''}`.trim()
  return name || (email?.trim() ?? 'Unknown user')
}

async function toggleShareAccount(accountId: string): Promise<void> {
  sharingAccountId.value = accountId
  try {
    if (isAccountShared(accountId)) {
      await householdStore.unshareAccount(householdId.value, accountId)
    } else {
      await householdStore.shareAccount(householdId.value, accountId)
    }
  } finally {
    sharingAccountId.value = null
  }
}

async function handleInvite(): Promise<void> {
  if (!inviteEmail.value.trim()) {
    inviteError.value = 'Email is required.'
    return
  }

  isInviting.value = true
  inviteError.value = ''
  inviteSuccess.value = ''

  try {
    const success = await householdStore.inviteMember(householdId.value, {
      email: inviteEmail.value.trim(),
    })

    if (success) {
      inviteSuccess.value = `Invitation sent to ${inviteEmail.value}.`
      inviteEmail.value = ''
    } else {
      inviteError.value = householdStore.error ?? 'Failed to send invitation.'
    }
  } catch (error: unknown) {
    if (error instanceof HTTPError) {
      const payload = await error.response.json<{ message?: string }>().catch(() => null)
      inviteError.value = payload?.message?.trim() || 'Failed to send invitation.'
    } else {
      inviteError.value = 'Failed to send invitation.'
    }
  } finally {
    isInviting.value = false
  }
}
</script>

<template>
  <section class="grid w-full gap-4 lg:max-w-2xl">
    <Card v-if="householdStore.currentHousehold" class="w-full">
      <CardHeader>
        <CardTitle class="text-2xl">{{ householdStore.currentHousehold.name }}</CardTitle>
        <CardDescription>
          Created {{ new Date(householdStore.currentHousehold.createdAt).toLocaleDateString() }}
        </CardDescription>
      </CardHeader>
    </Card>

    <!-- Members list -->
    <!-- Note: Using a simple v-for list for v1. Consider tanstack/vue-table if the member list grows. -->
    <Card class="w-full">
      <CardHeader>
        <CardTitle>Members</CardTitle>
        <CardDescription>Manage household members and their roles.</CardDescription>
      </CardHeader>
      <CardContent>
        <div v-if="householdStore.isLoading" class="text-sm text-muted-foreground">
          Loading members...
        </div>
        <div v-else-if="householdStore.currentHousehold?.members.length" class="space-y-3">
          <div
            v-for="member in householdStore.currentHousehold.members"
            :key="member.userId"
            class="flex items-center justify-between rounded-md border border-border px-3 py-2"
          >
            <div class="flex flex-col gap-0.5">
              <span class="text-sm font-medium">
                {{ displayPerson(member.firstName, member.lastName, member.email) }}
              </span>
              <span class="text-xs text-muted-foreground">{{ member.email }}</span>
              <span v-if="member.joinedAt" class="text-xs text-muted-foreground">
                Joined {{ new Date(member.joinedAt).toLocaleDateString() }}
              </span>
            </div>
            <div class="flex items-center gap-2">
              <Badge :variant="roleBadgeVariant(member.role)">
                {{ member.role }}
              </Badge>
              <Badge :variant="statusBadgeVariant(member.status)">
                {{ member.status }}
              </Badge>
            </div>
          </div>
        </div>
        <p v-else class="text-sm text-muted-foreground">No members found.</p>
      </CardContent>
    </Card>

    <Card v-if="householdStore.isOwnerOrAdmin" class="w-full">
      <CardHeader>
        <CardTitle>Invite Member</CardTitle>
        <CardDescription> Send an invitation to join this household by email. </CardDescription>
      </CardHeader>
      <CardContent class="space-y-4">
        <div class="grid gap-2">
          <Label for="invite-email">Email</Label>
          <Input
            id="invite-email"
            v-model="inviteEmail"
            type="email"
            placeholder="name@example.com"
          />
        </div>
        <Button size="sm" :disabled="isInviting" @click="handleInvite">
          {{ isInviting ? 'Sending...' : 'Send Invitation' }}
        </Button>
        <p v-if="inviteSuccess" class="text-sm text-emerald-600">{{ inviteSuccess }}</p>
        <p v-if="inviteError" class="text-sm text-red-600">{{ inviteError }}</p>
      </CardContent>
    </Card>

    <Separator />

    <!-- Shared accounts (read-only list of all shared accounts) -->
    <Card class="w-full">
      <CardHeader>
        <CardTitle>Shared Accounts</CardTitle>
        <CardDescription>Accounts currently shared in this household.</CardDescription>
      </CardHeader>
      <CardContent>
        <div v-if="householdStore.sharedAccounts.length" class="space-y-3">
          <div
            v-for="share in householdStore.sharedAccounts"
            :key="share.accountId"
            class="flex items-center justify-between rounded-md border border-border px-3 py-2"
          >
            <div class="flex flex-col gap-0.5">
              <span class="text-sm font-medium">{{ share.accountName }}</span>
              <span class="text-xs text-muted-foreground">
                Shared by
                {{ displayPerson(share.ownerFirstName, share.ownerLastName, share.ownerEmail) }}
              </span>
            </div>
            <span class="text-xs text-muted-foreground">
              {{ new Date(share.sharedAt).toLocaleDateString() }}
            </span>
          </div>
        </div>
        <p v-else class="text-sm text-muted-foreground">No accounts are shared yet.</p>
      </CardContent>
    </Card>

    <!-- Share/unshare your own accounts -->
    <Card class="w-full">
      <CardHeader>
        <CardTitle>Your Accounts</CardTitle>
        <CardDescription> Toggle sharing for your own accounts in this household. </CardDescription>
      </CardHeader>
      <CardContent>
        <div v-if="myAccounts.length === 0" class="text-sm text-muted-foreground">
          You have no accounts to share. Create accounts first.
        </div>
        <div v-else class="space-y-4">
          <div v-for="[type, accounts] in myAccountsByType" :key="type">
            <p class="mb-2 text-xs font-medium text-muted-foreground uppercase">{{ type }}</p>
            <div class="space-y-2">
              <div
                v-for="account in accounts"
                :key="account.id"
                class="flex items-center justify-between rounded-md border border-border px-3 py-2"
              >
                <div class="flex flex-col gap-0.5">
                  <div class="flex items-center gap-2">
                    <span class="text-sm font-medium">{{ account.name }}</span>
                    <span class="text-xs text-muted-foreground">({{ account.currency }})</span>
                    <Badge v-if="isAccountShared(account.id)" variant="default" class="text-[10px]">
                      Shared
                    </Badge>
                  </div>
                  <span v-if="sharedAtForAccount(account.id)" class="text-xs text-muted-foreground">
                    Shared {{ sharedAtForAccount(account.id) }}
                  </span>
                </div>
                <Button
                  size="sm"
                  :variant="isAccountShared(account.id) ? 'destructive' : 'secondary'"
                  class="h-8 px-3 text-xs"
                  :disabled="sharingAccountId === account.id"
                  @click="toggleShareAccount(account.id)"
                >
                  {{
                    sharingAccountId === account.id
                      ? '...'
                      : isAccountShared(account.id)
                        ? 'Unshare'
                        : 'Share'
                  }}
                </Button>
              </div>
            </div>
          </div>
        </div>
        <p v-if="householdStore.error" class="mt-2 text-sm text-red-600">
          {{ householdStore.error }}
        </p>
      </CardContent>
    </Card>
  </section>
</template>
