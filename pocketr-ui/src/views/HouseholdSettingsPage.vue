<script setup lang="ts">
import { HTTPError } from 'ky'
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { useAccountStore } from '@/stores/account'
import { useAuthStore } from '@/stores/auth'
import { useHouseholdStore } from '@/stores/household'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { AppFormField, AppListItem, AppStateMessage, AppStatusText } from '@/components/app'
import type { HouseholdRole } from '@/types/household'
import type { AccountType } from '@/types/ledger'
import { translate } from '@/i18n/translate'

const route = useRoute()
const householdStore = useHouseholdStore()
const accountStore = useAccountStore()
const authStore = useAuthStore()

const householdId = computed(() => route.params.householdId as string)

const inviteEmail = ref('')
const isInviting = ref(false)
const inviteError = ref('')
const inviteSuccess = ref('')
const selectedRolloverDay = ref(1)
const isSavingRollover = ref(false)
const rolloverError = ref('')
const rolloverSuccess = ref('')

const sharingAccountId = ref<string | null>(null)

onMounted(async () => {
  await Promise.all([
    householdStore.loadHousehold(householdId.value),
    householdStore.loadSharedAccounts(householdId.value),
    accountStore.load(),
  ])
})

watch(
  () => householdStore.currentHousehold?.rolloverDay,
  (rolloverDay) => {
    if (rolloverDay) {
      selectedRolloverDay.value = rolloverDay
    }
  },
  { immediate: true },
)

const hasRolloverChanged = computed(() => {
  return selectedRolloverDay.value !== householdStore.currentHousehold?.rolloverDay
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
  return name || (email?.trim() ?? translate('common.states.unknownUser'))
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
    inviteError.value = translate('validation.household.emailRequired')
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
      inviteSuccess.value = translate('views.householdSettings.invite.success', {
        email: inviteEmail.value,
      })
      inviteEmail.value = ''
    } else {
      inviteError.value = householdStore.error ?? translate('errors.households.sendInvitation')
    }
  } catch (error: unknown) {
    if (error instanceof HTTPError) {
      const payload = await error.response.json<{ message?: string }>().catch(() => null)
      inviteError.value = payload?.message?.trim() || translate('errors.households.sendInvitation')
    } else {
      inviteError.value = translate('errors.households.sendInvitation')
    }
  } finally {
    isInviting.value = false
  }
}

async function saveRolloverDay(): Promise<void> {
  rolloverError.value = ''
  rolloverSuccess.value = ''

  if (selectedRolloverDay.value < 1 || selectedRolloverDay.value > 31) {
    rolloverError.value = translate('validation.rollover.dayRange')
    return
  }

  isSavingRollover.value = true
  try {
    const success = await householdStore.updateRolloverDay(
      householdId.value,
      selectedRolloverDay.value,
    )
    if (success) {
      selectedRolloverDay.value = householdStore.currentHousehold?.rolloverDay ?? 1
      rolloverSuccess.value = translate('views.householdSettings.rollover.updated')
    } else {
      rolloverError.value = householdStore.error ?? translate('errors.households.updateRollover')
    }
  } finally {
    isSavingRollover.value = false
  }
}
</script>

<template>
  <section class="flex w-full flex-col gap-4">
    <!-- Household header -->
    <Card v-if="householdStore.currentHousehold">
      <CardHeader>
        <CardTitle class="text-2xl">{{ householdStore.currentHousehold.name }}</CardTitle>
        <CardDescription>
          {{
            $t('views.householdSettings.header.created', {
              date: new Date(householdStore.currentHousehold.createdAt).toLocaleDateString(),
            })
          }}
        </CardDescription>
      </CardHeader>
      <CardContent class="space-y-3 border-t border-border pt-4">
        <div class="flex flex-col gap-3 sm:flex-row sm:items-end">
          <AppFormField :label="$t('views.householdSettings.rollover.day')" class="sm:max-w-56">
            <Input
              v-model.number="selectedRolloverDay"
              type="number"
              min="1"
              max="31"
              inputmode="numeric"
              :disabled="!householdStore.isOwnerOrAdmin"
            />
          </AppFormField>
          <Button
            v-if="householdStore.isOwnerOrAdmin"
            size="sm"
            class="w-fit sm:h-9"
            :disabled="isSavingRollover || !hasRolloverChanged"
            @click="saveRolloverDay"
          >
            {{ isSavingRollover ? $t('common.feedback.saving') : $t('common.actions.save') }}
          </Button>
        </div>
        <p class="text-xs text-muted-foreground">
          {{ $t('views.householdSettings.rollover.help') }}
        </p>
        <AppStatusText v-if="rolloverSuccess" variant="success">{{
          rolloverSuccess
        }}</AppStatusText>
        <AppStatusText v-if="rolloverError">{{ rolloverError }}</AppStatusText>
      </CardContent>
    </Card>

    <!-- Three-column grid -->
    <div class="grid grid-cols-1 gap-4 md:grid-cols-3">
      <!-- Column 1: Members + Invite -->
      <div class="flex flex-col gap-4">
        <!-- Members list -->
        <!-- Note: Using a simple v-for list for v1. Consider tanstack/vue-table if the member list grows. -->
        <Card :class="!householdStore.isOwnerOrAdmin ? 'flex-1' : ''">
          <CardHeader>
            <CardTitle>{{ $t('views.householdSettings.members.title') }}</CardTitle>
            <CardDescription>{{
              $t('views.householdSettings.members.description')
            }}</CardDescription>
          </CardHeader>
          <CardContent>
            <AppStateMessage v-if="householdStore.isLoading">
              {{ $t('views.householdSettings.members.loading') }}
            </AppStateMessage>
            <div v-else-if="householdStore.currentHousehold?.members.length" class="space-y-3">
              <AppListItem
                v-for="member in householdStore.currentHousehold.members"
                :key="member.userId"
              >
                <div class="flex flex-col gap-0.5">
                  <span class="text-sm font-medium">
                    {{ displayPerson(member.firstName, member.lastName, member.email) }}
                  </span>
                  <span class="text-xs text-muted-foreground">{{ member.email }}</span>
                  <span v-if="member.joinedAt" class="text-xs text-muted-foreground">
                    {{
                      $t('views.householdSettings.members.joined', {
                        date: new Date(member.joinedAt).toLocaleDateString(),
                      })
                    }}
                  </span>
                </div>
                <template #actions>
                  <div class="flex items-center gap-2">
                    <Badge :variant="roleBadgeVariant(member.role)">
                      {{ $t(`display.householdRoles.${member.role}`) }}
                    </Badge>
                    <Badge :variant="statusBadgeVariant(member.status)">
                      {{ $t(`display.memberStatuses.${member.status}`) }}
                    </Badge>
                  </div>
                </template>
              </AppListItem>
            </div>
            <AppStateMessage v-else>{{
              $t('views.householdSettings.members.empty')
            }}</AppStateMessage>
          </CardContent>
        </Card>

        <!-- Invite Member -->
        <Card v-if="householdStore.isOwnerOrAdmin" class="flex-1">
          <CardHeader>
            <CardTitle>{{ $t('views.householdSettings.invite.title') }}</CardTitle>
            <CardDescription>{{
              $t('views.householdSettings.invite.description')
            }}</CardDescription>
          </CardHeader>
          <CardContent class="space-y-4">
            <AppFormField :label="$t('common.fields.email')" control-id="invite-email">
              <Input
                id="invite-email"
                v-model="inviteEmail"
                type="email"
                :placeholder="$t('views.householdSettings.invite.placeholder')"
              />
            </AppFormField>
            <Button size="sm" :disabled="isInviting || !inviteEmail.trim()" @click="handleInvite">
              {{
                isInviting
                  ? $t('common.feedback.sending')
                  : $t('views.householdSettings.invite.sendAction')
              }}
            </Button>
            <AppStatusText v-if="inviteSuccess" variant="success">{{
              inviteSuccess
            }}</AppStatusText>
            <AppStatusText v-if="inviteError">{{ inviteError }}</AppStatusText>
          </CardContent>
        </Card>
      </div>

      <!-- Column 2: Shared Accounts -->
      <div class="flex flex-col gap-4">
        <Card class="flex-1">
          <CardHeader>
            <CardTitle>{{ $t('views.householdSettings.accounts.sharedTitle') }}</CardTitle>
            <CardDescription>{{
              $t('views.householdSettings.accounts.sharedDescription')
            }}</CardDescription>
          </CardHeader>
          <CardContent>
            <div v-if="householdStore.sharedAccounts.length" class="space-y-3">
              <AppListItem v-for="share in householdStore.sharedAccounts" :key="share.accountId">
                <div class="flex flex-col gap-0.5">
                  <span class="text-sm font-medium">{{ share.accountName }}</span>
                  <span class="text-xs text-muted-foreground">
                    {{
                      $t('views.householdSettings.accounts.sharedBy', {
                        name: displayPerson(
                          share.ownerFirstName,
                          share.ownerLastName,
                          share.ownerEmail,
                        ),
                      })
                    }}
                  </span>
                </div>
                <template #actions>
                  <span class="text-xs text-muted-foreground">
                    {{ new Date(share.sharedAt).toLocaleDateString() }}
                  </span>
                </template>
              </AppListItem>
            </div>
            <AppStateMessage v-else>{{
              $t('views.householdSettings.accounts.sharedEmpty')
            }}</AppStateMessage>
          </CardContent>
        </Card>
      </div>

      <!-- Column 3: Your Accounts -->
      <div class="flex flex-col gap-4">
        <Card class="flex-1">
          <CardHeader>
            <CardTitle>{{ $t('views.householdSettings.accounts.yoursTitle') }}</CardTitle>
            <CardDescription>{{
              $t('views.householdSettings.accounts.yoursDescription')
            }}</CardDescription>
          </CardHeader>
          <CardContent>
            <AppStateMessage v-if="myAccounts.length === 0">
              {{ $t('views.householdSettings.accounts.emptyMine') }}
            </AppStateMessage>
            <div v-else class="space-y-4">
              <div v-for="[type, accounts] in myAccountsByType" :key="type">
                <p class="mb-2 text-xs font-medium uppercase text-muted-foreground">
                  {{ $t(`display.accountTypes.${type}`) }}
                </p>
                <div class="space-y-2">
                  <AppListItem v-for="account in accounts" :key="account.id">
                    <div class="flex flex-col gap-0.5">
                      <div class="flex items-center gap-2">
                        <span class="text-sm font-medium">{{ account.name }}</span>
                        <span class="text-xs text-muted-foreground">({{ account.currency }})</span>
                        <Badge
                          v-if="isAccountShared(account.id)"
                          variant="default"
                          class="text-[10px]"
                        >
                          {{ $t('views.householdSettings.accounts.sharedBadge') }}
                        </Badge>
                      </div>
                      <span
                        v-if="sharedAtForAccount(account.id)"
                        class="text-xs text-muted-foreground"
                      >
                        {{
                          $t('views.householdSettings.accounts.sharedAt', {
                            date: sharedAtForAccount(account.id),
                          })
                        }}
                      </span>
                    </div>
                    <template #actions>
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
                              ? $t('common.actions.unshare')
                              : $t('common.actions.share')
                        }}
                      </Button>
                    </template>
                  </AppListItem>
                </div>
              </div>
            </div>
            <AppStatusText v-if="householdStore.error" class="mt-2">
              {{ householdStore.error }}
            </AppStatusText>
          </CardContent>
        </Card>
      </div>
    </div>
  </section>
</template>
