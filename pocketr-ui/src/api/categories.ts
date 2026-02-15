import { api } from '@/api/http'
import type { CategoryTag, CreateCategoryRequest, UpdateCategoryRequest } from '@/types/ledger'

const BASE = '/api/v1/categories'

export function listCategories(): Promise<CategoryTag[]> {
  return api.get(BASE).json<CategoryTag[]>()
}

export function createCategory(req: CreateCategoryRequest): Promise<CategoryTag> {
  return api.post(BASE, { json: req }).json<CategoryTag>()
}

export function updateCategory(id: string, req: UpdateCategoryRequest): Promise<CategoryTag> {
  return api.patch(`${BASE}/${id}`, { json: req }).json<CategoryTag>()
}

export function deleteCategory(id: string): Promise<void> {
  return api.delete(`${BASE}/${id}`).then(() => undefined)
}
