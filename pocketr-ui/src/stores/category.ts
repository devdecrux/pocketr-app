import { defineStore } from 'pinia'
import { ref } from 'vue'
import { listCategories } from '@/api/categories'
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
    } catch {
      error.value = 'Failed to load categories.'
    } finally {
      isLoading.value = false
    }
  }

  return {
    categories,
    isLoading,
    error,
    load,
  }
})
