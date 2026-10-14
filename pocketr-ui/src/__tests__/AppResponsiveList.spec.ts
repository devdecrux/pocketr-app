import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { h, ref } from 'vue'
import { mount } from '@vue/test-utils'
import ui from '@nuxt/ui/vue-plugin'
import UApp from '@nuxt/ui/components/App.vue'
import { i18n } from '@/i18n'
import AppResponsiveList from '@/components/lists/AppResponsiveList.vue'
import type { AppListGroup, AppTableColumn, AppTablePagination } from '@/components/lists/dataTable'

const desktop = vi.hoisted(() => ({ value: true }))

vi.mock('@/utils/useIsDesktop', async () => {
  const { ref: vueRef } = await vi.importActual<typeof import('vue')>('vue')
  return { useIsDesktop: () => vueRef(desktop.value) }
})

interface Item extends Record<string, unknown> {
  id: string
  name: string
}

const ITEMS: Item[] = [
  { id: 'a', name: 'Alpha' },
  { id: 'b', name: 'Beta' },
]
const COLUMNS: AppTableColumn<Item>[] = [{ accessorKey: 'name', header: 'Name' }]
const PAGINATION: AppTablePagination = { page: 0, pageSize: 10, totalPages: 3, totalElements: 25 }

function mountList(
  props: Record<string, unknown> = {},
  expandedModel = ref<Record<string, boolean>>({}),
) {
  const events: { name: string; args: unknown[] }[] = []
  const wrapper = mount(
    {
      render: () =>
        h(UApp, null, {
          default: () =>
            h(
              AppResponsiveList,
              {
                rows: ITEMS,
                columns: COLUMNS,
                rowKey: (row: Item) => row.id,
                panelIdPrefix: 'row-details',
                loadingText: 'Loading rows',
                emptyText: 'No rows',
                expanded: expandedModel.value,
                'onUpdate:expanded': (value: Record<string, boolean>) => {
                  expandedModel.value = value
                },
                'onUpdate:page': (page: number) => events.push({ name: 'page', args: [page] }),
                'onUpdate:pageSize': (size: number) => events.push({ name: 'size', args: [size] }),
                ...props,
              } as never,
              {
                row: ({ item }: { item: Item }) => h('span', { class: 'row-content' }, item.name),
                details: ({ item }: { item: Item }) =>
                  h('p', { class: 'row-details' }, `Details ${item.name}`),
                'name-cell': ({ row }: { row: { original: Item } }) =>
                  h('b', { class: 'cell' }, row.original.name),
              } as never,
            ),
        }),
    },
    { attachTo: document.body, global: { plugins: [i18n, ui] } },
  )
  return { wrapper, events, expandedModel }
}

describe('AppResponsiveList', () => {
  beforeEach(() => {
    desktop.value = true
  })
  afterEach(() => {
    document.body.innerHTML = ''
  })

  it('renders only the table on desktop', () => {
    const { wrapper } = mountList()

    expect(wrapper.find('table').exists()).toBe(true)
    expect(wrapper.findAll('.cell').map((cell) => cell.text())).toEqual(['Alpha', 'Beta'])
    expect(wrapper.find('ul').exists()).toBe(false)
    expect(wrapper.find('.row-content').exists()).toBe(false)
    wrapper.unmount()
  })

  it('renders only the expandable rows on a phone', () => {
    desktop.value = false
    const { wrapper } = mountList()

    expect(wrapper.find('table').exists()).toBe(false)
    expect(wrapper.find('.cell').exists()).toBe(false)
    expect(wrapper.findAll('li .row-content').map((row) => row.text())).toEqual(['Alpha', 'Beta'])
    expect(wrapper.find('h2').exists()).toBe(false)
    wrapper.unmount()
  })

  it('expands a phone row through the shared expanded state', async () => {
    desktop.value = false
    const { wrapper, expandedModel } = mountList()

    const button = wrapper.get('button')
    expect(button.attributes('aria-expanded')).toBe('false')
    expect(button.attributes('aria-controls')).toBe('row-details-a')
    await button.trigger('click')

    expect(expandedModel.value).toEqual({ a: true })
    expect(wrapper.get('#row-details-a').text()).toBe('Details Alpha')
    expect(wrapper.find('#row-details-b').exists()).toBe(false)
    wrapper.unmount()
  })

  it('shows skeletons only while loading without rows, on a phone', () => {
    desktop.value = false
    const loading = mountList({ rows: [], loading: true, skeletonClass: 'h-14' })
    expect(loading.wrapper.get('[aria-label="Loading rows"]').findAll('.h-14')).toHaveLength(4)
    expect(loading.wrapper.text()).not.toContain('No rows')
    loading.wrapper.unmount()

    const reload = mountList({ loading: true })
    expect(reload.wrapper.find('[aria-label="Loading rows"]').exists()).toBe(false)
    expect(reload.wrapper.findAll('li')).toHaveLength(2)
    expect(reload.wrapper.find('[aria-busy="true"]').exists()).toBe(true)
    reload.wrapper.unmount()
  })

  it('shows the empty text on both variants', () => {
    const table = mountList({ rows: [] })
    expect(table.wrapper.find('tbody').text()).toBe('No rows')
    table.wrapper.unmount()

    desktop.value = false
    const phone = mountList({ rows: [] })
    expect(phone.wrapper.find('p').text()).toBe('No rows')
    expect(phone.wrapper.find('table').exists()).toBe(false)
    phone.wrapper.unmount()
  })

  it('shows the loading text in the table while loading on desktop', () => {
    const { wrapper } = mountList({ rows: [], loading: true })
    expect(wrapper.text()).toContain('Loading rows')
    wrapper.unmount()
  })

  it('paginates on both variants and forwards page changes', async () => {
    desktop.value = false
    const phone = mountList({ pagination: PAGINATION })
    expect(phone.wrapper.text()).toContain('25')
    const next = phone.wrapper.get('button[aria-label="Next page"]')
    await next.trigger('click')
    expect(phone.events).toEqual([{ name: 'page', args: [1] }])
    phone.wrapper.unmount()

    desktop.value = true
    const table = mountList({ pagination: PAGINATION })
    await table.wrapper.get('button[aria-label="Next page"]').trigger('click')
    expect(table.events).toEqual([{ name: 'page', args: [1] }])
    table.wrapper.unmount()
  })

  it('has no pagination bar without pagination, and hides it while the skeleton shows', () => {
    desktop.value = false
    const none = mountList()
    expect(none.wrapper.find('button[aria-label="Next page"]').exists()).toBe(false)
    none.wrapper.unmount()

    const skeleton = mountList({ rows: [], loading: true, pagination: PAGINATION })
    expect(skeleton.wrapper.find('button[aria-label="Next page"]').exists()).toBe(false)
    skeleton.wrapper.unmount()
  })

  it('groups phone rows under headings and passes the group to the slots', () => {
    desktop.value = false
    const groups: AppListGroup<Item>[] = [
      { id: 'today', label: 'Today', items: [ITEMS[0]!] },
      { id: 'older', label: 'Earlier', items: [ITEMS[1]!] },
    ]
    const { wrapper } = mountList({ groups })

    expect(wrapper.findAll('section')).toHaveLength(2)
    expect(wrapper.findAll('h2').map((h2) => h2.text())).toEqual(['Today', 'Earlier'])
    expect(wrapper.findAll('ul')).toHaveLength(2)
    wrapper.unmount()
  })
})
