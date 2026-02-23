import { defineStore } from 'pinia'
import { ref } from 'vue'
import { HTTPError } from 'ky'
import {
  createCategory as apiCreateCategory,
  deleteCategory as apiDeleteCategory,
  listCategories,
  updateCategory as apiUpdateCategory,
} from '@/api/categories'
import type { CategoryTag } from '@/types/ledger'

export const useCategoryStore = defineStore('category', () => {
  const categories = ref<CategoryTag[]>([])
  const isLoading = ref(false)
  const error = ref<string | null>(null)

  async function load(): Promise<void> {
    isLoading.value = true
    error.value = null
    try {
      categories.value = await listCategories()
    } catch (nextError: unknown) {
      error.value = await resolveErrorMessage(nextError, 'Failed to load categories.')
    } finally {
      isLoading.value = false
    }
  }

  async function create(name: string, color: string | null): Promise<CategoryTag | null> {
    error.value = null

    try {
      const created = await apiCreateCategory({ name, color })
      categories.value = [...categories.value, created]
      return created
    } catch (nextError: unknown) {
      error.value = await resolveErrorMessage(nextError, 'Failed to create category.')
      return null
    }
  }

  async function rename(
    id: string,
    name: string,
    color: string | null,
  ): Promise<CategoryTag | null> {
    error.value = null

    try {
      const updated = await apiUpdateCategory(id, { name, color })
      categories.value = categories.value.map((category) =>
        category.id === id ? updated : category,
      )
      return updated
    } catch (nextError: unknown) {
      error.value = await resolveErrorMessage(nextError, 'Failed to rename category.')
      return null
    }
  }

  async function remove(id: string): Promise<boolean> {
    error.value = null

    try {
      await apiDeleteCategory(id)
      categories.value = categories.value.filter((category) => category.id !== id)
      return true
    } catch (nextError: unknown) {
      error.value = await resolveErrorMessage(nextError, 'Failed to delete category.')
      return false
    }
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

  function $reset(): void {
    categories.value = []
    isLoading.value = false
    error.value = null
  }

  return {
    categories,
    isLoading,
    error,
    load,
    create,
    rename,
    remove,
    $reset,
  }
})
