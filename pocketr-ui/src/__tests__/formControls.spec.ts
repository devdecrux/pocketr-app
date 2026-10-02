import { createPinia, setActivePinia } from 'pinia'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { h, ref } from 'vue'
import ui from '@nuxt/ui/vue-plugin'
import UApp from '@nuxt/ui/components/App.vue'
import USelect from '@nuxt/ui/components/Select.vue'
import USelectMenu from '@nuxt/ui/components/SelectMenu.vue'
import UCalendar from '@nuxt/ui/components/Calendar.vue'
import AccountSelect from '@/components/forms/AccountSelect.vue'
import AppDateField from '@/components/forms/AppDateField.vue'
import AppDateRangePicker from '@/components/forms/AppDateRangePicker.vue'
import CategorySelect from '@/components/forms/CategorySelect.vue'
import FormMessage from '@/components/forms/FormMessage.vue'
import MoneyInput from '@/components/forms/MoneyInput.vue'
import { i18n } from '@/i18n'
import { useAccountStore } from '@/stores/account'
import { useCategoryStore } from '@/stores/category'
import { useModeStore } from '@/stores/mode'
import type { Account } from '@/types/ledger'

function mountInApp(render: () => ReturnType<typeof h>) {
  return mount(
    { render: () => h(UApp, null, { default: render }) },
    { attachTo: document.body, global: { plugins: [i18n, ui] } },
  )
}

function account(id: string, type: Account['type'], extra: Partial<Account> = {}): Account {
  return {
    id,
    ownerUserId: 1,
    name: `Account ${id}`,
    type,
    currency: 'EUR',
    createdAt: '2026-01-01T00:00:00Z',
    status: 'ACTIVE',
    archivedAt: null,
    ...extra,
  }
}

beforeEach(() => {
  setActivePinia(createPinia())
  vi.stubGlobal('localStorage', {
    getItem: vi.fn(() => null),
    setItem: vi.fn(),
    removeItem: vi.fn(),
  })
})

afterEach(() => {
  vi.unstubAllGlobals()
  document.body.innerHTML = ''
})

describe('MoneyInput', () => {
  function mountMoney(props: Record<string, unknown> = {}, initial = 0) {
    const model = ref(initial)
    const wrapper = mountInApp(() =>
      h(MoneyInput, {
        id: 'amount',
        modelValue: model.value,
        'onUpdate:modelValue': (value: number) => (model.value = value),
        currencyCode: 'EUR',
        ...props,
      }),
    )
    return { wrapper, model, input: () => wrapper.get('input') }
  }

  it('shows the currency symbol and the decimal amount of the minor-unit model', () => {
    const { wrapper, input } = mountMoney({}, 4680)

    expect(input().element.value).toBe('46.80')
    expect(wrapper.get('[data-testid="amount-symbol"]').text()).toBe('€')

    wrapper.unmount()
  })

  it('commits valid typing as minor units, accepts a comma and normalises on blur', async () => {
    const { wrapper, model, input } = mountMoney()

    await input().setValue('12,5')
    expect(model.value).toBe(1250)
    expect(input().element.value).toBe('12,5')

    await input().trigger('blur')
    expect(input().element.value).toBe('12.50')

    await input().setValue('abc')
    expect(model.value).toBe(1250)

    wrapper.unmount()
  })

  it('uses the minor unit of the currency (no decimals for JPY)', async () => {
    const { wrapper, model, input } = mountMoney({ minorUnit: 0, currencyCode: 'JPY' })

    await input().setValue('1200')
    expect(model.value).toBe(1200)

    wrapper.unmount()
  })

  it('accepts negative amounts only when allowed', async () => {
    const strict = mountMoney()
    await strict.input().setValue('-5')
    expect(strict.model.value).toBe(0)
    strict.wrapper.unmount()

    const signed = mountMoney({ allowNegative: true })
    await signed.input().setValue('-5')
    expect(signed.model.value).toBe(-500)
    signed.wrapper.unmount()
  })

  it('falls back to a generic icon without a currency', () => {
    const { wrapper } = mountMoney({ currencyCode: undefined })

    expect(wrapper.find('[data-testid="amount-symbol"]').exists()).toBe(false)

    wrapper.unmount()
  })
})

describe('AppDateField', () => {
  it('shows the formatted date and picks another day from the calendar', async () => {
    const model = ref('2026-09-20')
    const wrapper = mountInApp(() =>
      h(AppDateField, {
        id: 'date',
        modelValue: model.value,
        'onUpdate:modelValue': (value: string) => (model.value = value),
      }),
    )

    const trigger = wrapper.get('button#date')
    expect(trigger.text()).toBe('20/09/2026')

    await trigger.trigger('click')
    await flushPromises()
    const calendar = wrapper.findComponent(UCalendar)
    expect(calendar.exists()).toBe(true)

    calendar.vm.$emit('update:modelValue', { year: 2026, month: 9, day: 3, calendar: undefined })
    await flushPromises()
    expect(model.value).toBe('2026-09-03')

    wrapper.unmount()
  })

  it('shows the placeholder for an empty value', () => {
    const wrapper = mountInApp(() =>
      h(AppDateField, { id: 'date', modelValue: '', placeholder: 'Pick a date' }),
    )

    expect(wrapper.get('button#date').text()).toBe('Pick a date')

    wrapper.unmount()
  })
})

describe('AppDateRangePicker', () => {
  function mountRange(props: Record<string, unknown>) {
    const events: Record<string, unknown[]> = { from: [], to: [] }
    const wrapper = mountInApp(() =>
      h(AppDateRangePicker, {
        id: 'range',
        placeholder: 'All dates',
        'onUpdate:from': (value: unknown) => events.from!.push(value),
        'onUpdate:to': (value: unknown) => events.to!.push(value),
        ...props,
      }),
    )
    return { wrapper, events }
  }

  it('shows the placeholder or an example month, a lone start or end, and a full range', () => {
    const empty = mountRange({})
    expect(empty.wrapper.get('button#range').text()).toBe('All dates')
    expect(empty.wrapper.get('button#range').attributes('aria-label')).toBe('Date range')
    empty.wrapper.unmount()

    // Without a placeholder the empty trigger shows the current month as an example.
    const example = mountRange({ placeholder: undefined })
    expect(example.wrapper.get('button#range').text()).toMatch(
      /^01\/\d{2}\/\d{4} – \d{2}\/\d{2}\/\d{4}$/,
    )
    example.wrapper.unmount()

    const start = mountRange({ from: '2026-09-01' })
    expect(start.wrapper.get('button#range').text()).toBe('From 01/09/2026')
    start.wrapper.unmount()

    const end = mountRange({ to: '2026-09-30' })
    expect(end.wrapper.get('button#range').text()).toBe('Until 30/09/2026')
    end.wrapper.unmount()

    const range = mountRange({ from: '2026-09-01', to: '2026-09-30' })
    expect(range.wrapper.get('button#range').text()).toBe('01/09/2026 – 30/09/2026')
    range.wrapper.unmount()
  })

  it('emits the picked range as ISO dates and closes once both ends are set', async () => {
    const { wrapper, events } = mountRange({ from: '2026-09-01', to: '2026-09-30' })

    await wrapper.get('button#range').trigger('click')
    await flushPromises()
    wrapper.findComponent(UCalendar).vm.$emit('update:modelValue', {
      start: { year: 2026, month: 9, day: 5 },
      end: { year: 2026, month: 9, day: 12 },
    })
    await flushPromises()

    expect(events.from).toEqual(['2026-09-05'])
    expect(events.to).toEqual(['2026-09-12'])
    expect(wrapper.findComponent(UCalendar).exists()).toBe(false)

    wrapper.unmount()
  })

  it('clears both ends from the popover', async () => {
    const { wrapper, events } = mountRange({ from: '2026-09-01', to: '2026-09-30' })

    await wrapper.get('button#range').trigger('click')
    await flushPromises()
    const clear = [...document.body.querySelectorAll('button')].find(
      (button) => button.textContent?.trim() === 'Clear',
    )!
    clear.click()
    await flushPromises()

    expect(events.from).toEqual([undefined])
    expect(events.to).toEqual([undefined])

    wrapper.unmount()
  })
})

describe('AccountSelect', () => {
  beforeEach(() => {
    useAccountStore().accounts = [
      account('daily', 'ASSET'),
      account('card', 'LIABILITY'),
      account('living', 'EXPENSE'),
      account('equity', 'EQUITY'),
      account('old', 'ASSET', { status: 'ARCHIVED' }),
      account('jamie', 'ASSET', { ownerUserId: 2 }),
    ]
  })

  function itemsOf(wrapper: ReturnType<typeof mountInApp>) {
    return (
      wrapper.findComponent(USelect).props() as { items: Record<string, unknown>[][] }
    ).items.map((group) => group.map((item) => item.label))
  }

  it('lists active accounts of the allowed types grouped by type, never equity', () => {
    const wrapper = mountInApp(() => h(AccountSelect, { allowedTypes: ['ASSET', 'LIABILITY'] }))

    expect(itemsOf(wrapper)).toEqual([
      ['Asset', 'Account daily', 'Account jamie'],
      ['Liability', 'Account card'],
    ])

    wrapper.unmount()
  })

  it('includes archived accounts on request and marks them', () => {
    const wrapper = mountInApp(() => h(AccountSelect, { includeArchived: true }))

    const labels = itemsOf(wrapper).flat()
    expect(labels).toContain('Account old (Archived)')
    expect(labels).not.toContain('Account equity')

    wrapper.unmount()
  })

  it('groups by owner in household mode', () => {
    useModeStore().switchToHousehold('h1')
    const wrapper = mountInApp(() => h(AccountSelect, { allowedTypes: ['ASSET'] }))

    expect(itemsOf(wrapper)).toEqual([
      ['Owner: 1', 'Account daily'],
      ['Owner: 2', 'Account jamie'],
    ])

    wrapper.unmount()
  })

  it('filters by currency', () => {
    useAccountStore().accounts = [
      account('eur', 'ASSET'),
      account('usd', 'ASSET', { currency: 'USD' }),
    ]
    const wrapper = mountInApp(() => h(AccountSelect, { currency: 'USD' }))

    expect(itemsOf(wrapper).flat()).toEqual(['Asset', 'Account usd'])

    wrapper.unmount()
  })

  it('maps the "all" entry to an empty selection and back', async () => {
    const model = ref('daily')
    const wrapper = mountInApp(() =>
      h(AccountSelect, {
        modelValue: model.value,
        'onUpdate:modelValue': (value: string) => (model.value = value),
        allLabel: 'All accounts',
      }),
    )

    expect(itemsOf(wrapper)[0]).toEqual(['All accounts'])
    const select = wrapper.findComponent(USelect)
    select.vm.$emit('update:modelValue', '__all__')
    await flushPromises()
    expect(model.value).toBe('')
    expect((select.props() as { modelValue: string }).modelValue).toBe('__all__')

    select.vm.$emit('update:modelValue', 'card')
    await flushPromises()
    expect(model.value).toBe('card')

    wrapper.unmount()
  })
})

describe('CategorySelect', () => {
  it('lists the categories after the "none" entry and maps it to null', async () => {
    useCategoryStore().categories = [
      { id: 'c1', name: 'Groceries', color: '#62a864', createdAt: '' },
      { id: 'c2', name: 'Transport', color: null, createdAt: '' },
    ]
    const model = ref<string | null>('c1')
    const wrapper = mountInApp(() =>
      h(CategorySelect, {
        modelValue: model.value,
        'onUpdate:modelValue': (value: string | null) => (model.value = value),
        noneLabel: 'All categories',
      }),
    )

    const menu = wrapper.findComponent(USelectMenu)
    expect(
      (menu.props() as { items: { label: string }[] }).items.map((item) => item.label),
    ).toEqual(['All categories', 'Groceries', 'Transport'])

    menu.vm.$emit('update:modelValue', '__none__')
    await flushPromises()
    expect(model.value).toBeNull()
    expect((menu.props() as { modelValue: string }).modelValue).toBe('__none__')

    menu.vm.$emit('update:modelValue', 'c2')
    await flushPromises()
    expect(model.value).toBe('c2')

    wrapper.unmount()
  })
})

describe('FormMessage', () => {
  it('announces notices politely', () => {
    const wrapper = mountInApp(() => h(FormMessage, { tone: 'info', message: 'Heads up' }))

    expect(wrapper.get('[role="status"]').text()).toBe('Heads up')
    expect(wrapper.find('[role="alert"]').exists()).toBe(false)

    wrapper.unmount()
  })
})
