import { afterEach, describe, expect, it } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { h } from 'vue'
import ui from '@nuxt/ui/vue-plugin'
import UApp from '@nuxt/ui/components/App.vue'
import USelect from '@nuxt/ui/components/Select.vue'
import { i18n } from '@/i18n'
import AppDataTable from '@/components/shared/AppDataTable.vue'
import type { AppTableColumn, AppTablePagination } from '@/types/dataTable'

interface Item extends Record<string, unknown> {
  id: string
  name: string
  amount: number
}

const ITEMS: Item[] = [
  { id: 'a', name: 'Alpha', amount: 10 },
  { id: 'b', name: 'Beta', amount: 20 },
]

const COLUMNS: AppTableColumn<Item>[] = [
  { accessorKey: 'name', header: 'Name' },
  {
    accessorKey: 'amount',
    header: 'Amount',
    meta: { align: 'end', class: { td: 'tabular-nums' } },
  },
]

function mountTable(
  props: Record<string, unknown> = {},
  slots: Record<string, (scope: never) => unknown> = {},
) {
  const events: { name: string; args: unknown[] }[] = []
  const listeners = Object.fromEntries(
    ['onRowClick', 'onUpdate:page', 'onUpdate:pageSize'].map((key) => [
      key,
      (...args: unknown[]) => events.push({ name: key, args }),
    ]),
  )
  const wrapper = mount(
    {
      render: () =>
        h(UApp, null, {
          default: () =>
            h(
              AppDataTable,
              { data: ITEMS, columns: COLUMNS, ...listeners, ...props } as never,
              slots as never,
            ),
        }),
    },
    { attachTo: document.body, global: { plugins: [i18n, ui] } },
  )
  return { wrapper, events }
}

const PAGINATION: AppTablePagination = { page: 0, pageSize: 10, totalPages: 3, totalElements: 25 }

describe('AppDataTable', () => {
  afterEach(() => {
    document.body.innerHTML = ''
  })

  it('renders headers and rows', () => {
    const { wrapper } = mountTable()

    expect(wrapper.findAll('th').map((th) => th.text())).toEqual(['Name', 'Amount'])
    expect(wrapper.findAll('tbody tr').map((tr) => tr.text())).toEqual(['Alpha10', 'Beta20'])
    expect(wrapper.find('tbody tr').attributes('tabindex')).toBeUndefined()

    wrapper.unmount()
  })

  it('aligns a column header and its cells through meta.align', () => {
    const { wrapper } = mountTable()

    const amountHeader = wrapper.findAll('th')[1]!
    const amountCell = wrapper.findAll('tbody tr')[0]!.findAll('td')[1]!
    expect(amountHeader.classes()).toContain('text-end')
    expect(amountCell.classes()).toContain('text-end')
    expect(amountCell.classes()).toContain('tabular-nums')
    expect(wrapper.findAll('th')[0]!.classes()).not.toContain('text-end')

    wrapper.unmount()
  })

  it('shows the empty text, or the empty slot', () => {
    const fallback = mountTable({ data: [], emptyText: 'Nothing here yet.' })
    expect(fallback.wrapper.find('tbody').text()).toBe('Nothing here yet.')
    fallback.wrapper.unmount()

    const custom = mountTable({ data: [] }, { empty: () => h('strong', 'Custom empty') })
    expect(custom.wrapper.find('tbody strong').text()).toBe('Custom empty')
    custom.wrapper.unmount()
  })

  it('passes cell and header slots through', () => {
    const { wrapper } = mountTable({}, {
      'name-cell': ({ row }: { row: { original: Item } }) => h('em', `name:${row.original.name}`),
      'amount-header': () => h('span', 'Custom amount'),
    } as never)

    expect(wrapper.findAll('tbody em').map((em) => em.text())).toEqual(['name:Alpha', 'name:Beta'])
    expect(wrapper.findAll('th')[1]!.text()).toBe('Custom amount')

    wrapper.unmount()
  })

  it('emits row-click on click and on Enter or Space when clickable', async () => {
    const { wrapper, events } = mountTable({ clickable: true })

    const row = wrapper.findAll('tbody tr')[1]!
    expect(row.attributes('tabindex')).toBe('0')
    await row.trigger('click')
    await row.trigger('keydown', { key: 'Enter' })
    await row.trigger('keydown', { key: ' ' })

    const clicks = events.filter((event) => event.name === 'onRowClick')
    expect(clicks).toHaveLength(3)
    expect((clicks[0]!.args[0] as { original: Item }).original.id).toBe('b')

    wrapper.unmount()
  })

  it('does not emit row-click for rows that are not clickable', async () => {
    const { wrapper, events } = mountTable()

    await wrapper.find('tbody tr').trigger('click')
    expect(events).toHaveLength(0)

    wrapper.unmount()
  })

  it('hides pagination without the prop', () => {
    const { wrapper } = mountTable()

    expect(wrapper.text()).not.toContain('Rows per page')
    expect(wrapper.findComponent(USelect).exists()).toBe(false)

    wrapper.unmount()
  })

  it('emits zero-based page changes and page-size changes', async () => {
    const { wrapper, events } = mountTable({ pagination: PAGINATION, pageSizeOptions: [10, 25] })

    expect(wrapper.text()).toContain('1\u201310 of 25')

    await wrapper.find('button[aria-label="Next page"]').trigger('click')
    await flushPromises()
    expect(events.find((event) => event.name === 'onUpdate:page')?.args).toEqual([1])

    wrapper.findComponent(USelect).vm.$emit('update:modelValue', 25)
    await flushPromises()
    expect(events.find((event) => event.name === 'onUpdate:pageSize')?.args).toEqual([25])

    wrapper.unmount()
  })
})
