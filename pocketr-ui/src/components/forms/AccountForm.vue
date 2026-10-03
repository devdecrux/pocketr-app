<script setup lang="ts">
/**
 * Create-account form, shared by the desktop modal and the mobile drawer. A segmented control
 * switches between the four account types; the draft keeps its name, currency and opening balance
 * while the user switches. Only Asset and Liability accounts take an opening balance (negative
 * only for Asset), and the opening date applies only to a non-zero balance. The submit button
 * lives in the overlay footer and targets this form through `id`; it is enabled through `valid`.
 *
 * The draft starts empty each time the form mounts, so the page remounts it for every new account.
 */
import { computed, reactive, watch, watchEffect } from 'vue'
import { useI18n } from 'vue-i18n'
import AppDateField from '@/components/forms/AppDateField.vue'
import AppTypeTabs from '@/components/forms/AppTypeTabs.vue'
import FormMessage from '@/components/forms/FormMessage.vue'
import MoneyInput from '@/components/forms/MoneyInput.vue'
import { FIELD_BASE_CLASS, FIELD_SELECT_UI } from '@/components/forms/fieldStyles'
import { useCurrencyStore } from '@/stores/currency'
import type { AccountType, CreateAccountRequest } from '@/types/ledger'
import { APP_ICONS } from '@/utils/appIcons'
import { todayIso } from '@/utils/dates'

type CreatableAccountType = Exclude<AccountType, 'EQUITY'>

const props = defineProps<{ id: string; serverError?: string | null }>()

const valid = defineModel<boolean>('valid', { default: false })

const emit = defineEmits<{ submit: [request: CreateAccountRequest] }>()

const { t } = useI18n()
const currencyStore = useCurrencyStore()

const ACCOUNT_TYPES: CreatableAccountType[] = ['ASSET', 'EXPENSE', 'INCOME', 'LIABILITY']

const NAME_HINT_KEYS: Record<CreatableAccountType, string> = {
  ASSET: 'assetName',
  EXPENSE: 'expenseName',
  INCOME: 'incomeName',
  LIABILITY: 'liabilityName',
}

const draft = reactive({
  type: 'ASSET' as CreatableAccountType,
  name: '',
  currency: 'EUR',
  openingBalanceMinor: 0,
  openingDate: todayIso(),
})

const supportsOpeningBalance = computed(() => draft.type === 'ASSET' || draft.type === 'LIABILITY')
const minorUnit = computed(() => currencyStore.getMinorUnit(draft.currency))

// A liability opening amount is a debt, so it cannot stay negative after switching to it.
watch(
  () => draft.type,
  (type) => {
    if (type === 'LIABILITY' && draft.openingBalanceMinor < 0) {
      draft.openingBalanceMinor = Math.abs(draft.openingBalanceMinor)
    }
  },
)

watchEffect(() => {
  valid.value = draft.name.trim().length > 0
})

const tabItems = ACCOUNT_TYPES.map((type) => ({
  value: type,
  label: t(`display.accountTypes.${type}`),
}))

const currencyItems = computed(() =>
  currencyStore.currencies.map((currency) => ({
    value: currency.code,
    label: `${currency.code} — ${currency.name}`,
  })),
)

function onSubmit(): void {
  const name = draft.name.trim()
  if (!name) return

  const request: CreateAccountRequest = { name, type: draft.type, currency: draft.currency }
  if (supportsOpeningBalance.value && draft.openingBalanceMinor !== 0) {
    request.openingBalanceMinor = draft.openingBalanceMinor
    request.openingBalanceDate = draft.openingDate || todayIso()
  }
  emit('submit', request)
}

const fieldId = (name: string) => `${props.id}-${name}`

const labelUi = { label: 'text-sm font-medium text-highlighted', container: 'mt-1.5' }
</script>

<template>
  <form :id="id" novalidate class="flex flex-col gap-4" @submit.prevent="onSubmit">
    <FormMessage v-if="serverError" tone="error" :message="serverError" />

    <AppTypeTabs
      v-model="draft.type"
      :items="tabItems"
      :group-label="t('views.accounts.create.typeLabel')"
    >
      <template #content>
        <div class="flex flex-col gap-3 lg:gap-5">
          <UFormField :label="t('common.fields.name')" :for="fieldId('name')" :ui="labelUi">
            <UInput
              :id="fieldId('name')"
              v-model="draft.name"
              :icon="APP_ICONS.name"
              autocomplete="off"
              size="lg"
              class="w-full"
              :placeholder="t(`views.accounts.create.formHints.${NAME_HINT_KEYS[draft.type]}`)"
              :ui="{ base: FIELD_BASE_CLASS, leadingIcon: 'size-5 text-default' }"
            />
          </UFormField>

          <UFormField :label="t('common.fields.currency')" :for="fieldId('currency')" :ui="labelUi">
            <USelect
              :id="fieldId('currency')"
              v-model="draft.currency"
              :items="currencyItems"
              size="lg"
              class="w-full"
              :ui="FIELD_SELECT_UI"
            />
          </UFormField>

          <template v-if="supportsOpeningBalance">
            <UFormField
              :label="t('views.accounts.create.fields.openingDate')"
              :for="fieldId('opening-date')"
              :ui="labelUi"
            >
              <AppDateField
                :id="fieldId('opening-date')"
                v-model="draft.openingDate"
                :disabled="draft.openingBalanceMinor === 0"
              />
            </UFormField>

            <div class="flex flex-col gap-2">
              <UFormField
                :label="t('views.accounts.create.fields.initialBalance')"
                :for="fieldId('initial-balance')"
                :ui="labelUi"
              >
                <MoneyInput
                  :id="fieldId('initial-balance')"
                  v-model="draft.openingBalanceMinor"
                  :minor-unit="minorUnit"
                  :currency-code="draft.currency"
                  :allow-negative="draft.type === 'ASSET'"
                />
              </UFormField>
              <p class="text-xs text-muted">
                {{
                  t(
                    draft.type === 'ASSET'
                      ? 'views.accounts.create.descriptions.assetOpeningBalance'
                      : 'views.accounts.create.descriptions.liabilityOpeningBalance',
                  )
                }}
              </p>
            </div>
          </template>
        </div>
      </template>
    </AppTypeTabs>
  </form>
</template>
