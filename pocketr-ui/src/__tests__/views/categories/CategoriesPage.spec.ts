import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createCategory, deleteCategory, listCategories, updateCategory } from '@/api/categories'
import type { CategoryTag, CreateCategoryRequest, UpdateCategoryRequest } from '@/types/ledger'
import CategoriesPage from '@/views/categories/CategoriesPage.vue'
import { buildCategory, httpError, signIn } from '@/__tests__/support/fixtures'
import {
  alertText,
  buttonIn,
  click,
  dialogByTitle,
  dialogs,
  rowNames,
  typeInto,
} from '@/__tests__/support/dom'
import { mountRoute } from '@/__tests__/support/mount'

const CATEGORIES = [
  buildCategory({ id: 'c2', name: 'Transport', color: '#8391a5' }),
  buildCategory({ id: 'c1', name: 'Groceries', color: '#62a864' }),
  buildCategory({ id: 'c3', name: 'Health', color: '#e45a77' }),
]

async function mountPage() {
  const { wrapper } = await mountRoute(CategoriesPage, { path: '/categories' })
  return wrapper
}

const nameInput = (root: ParentNode) =>
  root.querySelector<HTMLInputElement>('input:not([type="radio"])')!

async function openCreate() {
  await click(buttonIn(document.body, 'New category'))
  return dialogByTitle('New category')
}

describe('CategoriesPage', () => {
  beforeEach(() => {
    signIn()
    vi.mocked(listCategories).mockResolvedValue([...CATEGORIES])
    vi.mocked(createCategory).mockImplementation(async (req: CreateCategoryRequest) =>
      buildCategory({ id: 'c9', ...req }),
    )
    vi.mocked(updateCategory).mockImplementation(
      async (id: string, req: UpdateCategoryRequest): Promise<CategoryTag> =>
        buildCategory({ id, ...req }),
    )
  })

  describe('list', () => {
    it('lists the categories alphabetically', async () => {
      const wrapper = await mountPage()

      expect(rowNames(wrapper)).toEqual(['Groceries', 'Health', 'Transport'])
    })
  })

  describe('create', () => {
    it('creates a category', async () => {
      const wrapper = await mountPage()
      const dialog = await openCreate()

      await typeInto(nameInput(dialog), '  Dining out ')
      await click(buttonIn(dialog, 'Create'))

      expect(createCategory).toHaveBeenCalledExactlyOnceWith(
        expect.objectContaining({ name: 'Dining out' }),
      )
      expect(rowNames(wrapper)).toContain('Dining out')
      expect(dialogs()).toHaveLength(0)
    })

    it('rejects a duplicate name case-insensitively without calling the API', async () => {
      await mountPage()
      const dialog = await openCreate()

      await typeInto(nameInput(dialog), 'groceries')
      await click(buttonIn(dialog, 'Create'))

      expect(createCategory).not.toHaveBeenCalled()
    })
  })

  describe('edit', () => {
    it('edits a category and re-sorts the list', async () => {
      const wrapper = await mountPage()

      await click(buttonIn(document.body, 'Edit Health'))
      const dialog = dialogByTitle('Edit category')

      await typeInto(nameInput(dialog), 'Wellbeing')
      await click(buttonIn(dialog, 'Save'))

      expect(updateCategory).toHaveBeenCalledExactlyOnceWith(
        'c3',
        expect.objectContaining({ name: 'Wellbeing' }),
      )
      expect(dialogs()).toHaveLength(0)
      expect(rowNames(wrapper)).toEqual(['Groceries', 'Transport', 'Wellbeing'])
    })

    it('allows the edited category to keep its own name', async () => {
      await mountPage()

      await click(buttonIn(document.body, 'Edit Health'))
      const dialog = dialogByTitle('Edit category')
      await typeInto(nameInput(dialog), 'HEALTH')
      await click(buttonIn(dialog, 'Save'))

      expect(updateCategory).toHaveBeenCalledWith('c3', expect.objectContaining({ name: 'HEALTH' }))
    })
  })

  describe('delete', () => {
    it('deletes only after confirmation', async () => {
      const wrapper = await mountPage()

      await click(buttonIn(document.body, 'Delete Transport'))
      expect(deleteCategory).not.toHaveBeenCalled()
      await click(buttonIn(dialogByTitle('Delete category "Transport"?'), 'Delete category'))

      expect(deleteCategory).toHaveBeenCalledExactlyOnceWith('c2')
      expect(rowNames(wrapper)).toEqual(['Groceries', 'Health'])
    })

    it('does not delete when the confirmation is cancelled', async () => {
      const wrapper = await mountPage()

      await click(buttonIn(document.body, 'Delete Transport'))
      await click(buttonIn(dialogByTitle('Delete category "Transport"?'), 'Cancel'))

      expect(deleteCategory).not.toHaveBeenCalled()
      expect(rowNames(wrapper)).toContain('Transport')
    })
  })

  it('shows the load error instead of the list, the delete error above the list and the create error in the open form', async () => {
    vi.mocked(listCategories).mockRejectedValueOnce(httpError('Categories are unavailable', 500))
    const failed = await mountPage()
    expect(alertText(failed.element)).toContain('Categories are unavailable')
    expect(failed.find('main table').exists()).toBe(false)
    failed.unmount()

    vi.mocked(deleteCategory).mockRejectedValue(
      httpError('Category is in use and cannot be deleted'),
    )
    vi.mocked(createCategory).mockRejectedValue(httpError("Category 'Dining out' already exists"))
    const wrapper = await mountPage()

    await click(buttonIn(document.body, 'Delete Transport'))
    await click(buttonIn(dialogByTitle('Delete category "Transport"?'), 'Delete category'))
    expect(alertText(wrapper.get('main').element)).toContain(
      'Category is in use and cannot be deleted',
    )
    expect(rowNames(wrapper)).toEqual(['Groceries', 'Health', 'Transport'])

    const dialog = await openCreate()
    await typeInto(nameInput(dialog), 'Dining out')
    await click(buttonIn(dialog, 'Create'))
    expect(alertText(dialog)).toContain("Category 'Dining out' already exists")
    expect(dialogs()).toHaveLength(1)
    expect(rowNames(wrapper)).toEqual(['Groceries', 'Health', 'Transport'])
  })
})
