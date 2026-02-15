<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { Pencil, Plus, Trash2 } from 'lucide-vue-next'
import { useCategoryStore } from '@/stores/category'
import type { CategoryTag } from '@/types/ledger'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
  DialogTrigger,
} from '@/components/ui/dialog'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Skeleton } from '@/components/ui/skeleton'

const categoryStore = useCategoryStore()

const createDialogOpen = ref(false)
const createName = ref('')
const createError = ref('')
const isCreating = ref(false)

const renameDialogOpen = ref(false)
const renameTarget = ref<CategoryTag | null>(null)
const renameName = ref('')
const renameError = ref('')
const isRenaming = ref(false)

const deleteError = ref('')
const deletingId = ref<string | null>(null)

const sortedCategories = computed(() => {
  return [...categoryStore.categories].sort((a, b) => a.name.localeCompare(b.name))
})

function hasDuplicateName(name: string, excludeId?: string): boolean {
  const normalized = name.trim().toLowerCase()
  return categoryStore.categories.some((category) => {
    if (excludeId && category.id === excludeId) {
      return false
    }
    return category.name.trim().toLowerCase() === normalized
  })
}

onMounted(async () => {
  await categoryStore.load()
})

function startRename(category: CategoryTag): void {
  renameTarget.value = category
  renameName.value = category.name
  renameError.value = ''
  renameDialogOpen.value = true
}

async function submitCreate(): Promise<void> {
  const trimmedName = createName.value.trim()
  if (!trimmedName) {
    createError.value = 'Category name is required.'
    return
  }
  if (hasDuplicateName(trimmedName)) {
    createError.value = `Category '${trimmedName}' already exists.`
    return
  }

  createError.value = ''
  isCreating.value = true

  const created = await categoryStore.create(trimmedName)
  if (created) {
    createDialogOpen.value = false
    createName.value = ''
  } else {
    createError.value = categoryStore.error ?? 'Failed to create category.'
  }

  isCreating.value = false
}

async function submitRename(): Promise<void> {
  const target = renameTarget.value
  const trimmedName = renameName.value.trim()
  if (!target) return

  if (!trimmedName) {
    renameError.value = 'Category name is required.'
    return
  }
  if (hasDuplicateName(trimmedName, target.id)) {
    renameError.value = `Category '${trimmedName}' already exists.`
    return
  }

  renameError.value = ''
  isRenaming.value = true

  const updated = await categoryStore.rename(target.id, trimmedName)
  if (updated) {
    renameDialogOpen.value = false
    renameTarget.value = null
    renameName.value = ''
  } else {
    renameError.value = categoryStore.error ?? 'Failed to rename category.'
  }

  isRenaming.value = false
}

async function deleteCategory(category: CategoryTag): Promise<void> {
  deleteError.value = ''

  const confirmed = window.confirm(`Delete category "${category.name}"?`)
  if (!confirmed) return

  deletingId.value = category.id
  const success = await categoryStore.remove(category.id)
  if (!success) {
    deleteError.value = categoryStore.error ?? 'Failed to delete category.'
  }
  deletingId.value = null
}
</script>

<template>
  <section class="grid gap-4">
    <Card>
      <CardHeader class="flex flex-row items-center justify-between">
        <CardTitle class="text-2xl">Categories</CardTitle>

        <Dialog v-model:open="createDialogOpen">
          <DialogTrigger as-child>
            <Button size="sm">
              <Plus class="size-4" />
              New Category
            </Button>
          </DialogTrigger>
          <DialogContent>
            <DialogHeader>
              <DialogTitle>Create Category</DialogTitle>
              <DialogDescription> Add a category for transaction tagging. </DialogDescription>
            </DialogHeader>

            <div class="grid gap-2 py-4">
              <Label for="create-category-name">Name</Label>
              <Input
                id="create-category-name"
                v-model="createName"
                placeholder="e.g. Groceries, Utilities"
              />
              <p v-if="createError" class="text-sm text-red-600">{{ createError }}</p>
            </div>

            <DialogFooter>
              <Button :disabled="isCreating || !createName.trim()" @click="submitCreate">
                {{ isCreating ? 'Creating...' : 'Create' }}
              </Button>
            </DialogFooter>
          </DialogContent>
        </Dialog>
      </CardHeader>

      <CardContent>
        <div v-if="categoryStore.isLoading" class="space-y-3">
          <Skeleton class="h-10 w-full" />
          <Skeleton class="h-10 w-full" />
          <Skeleton class="h-10 w-full" />
        </div>

        <div v-else-if="categoryStore.error" class="text-sm text-red-600">
          {{ categoryStore.error }}
        </div>

        <div v-else-if="sortedCategories.length === 0" class="text-sm text-muted-foreground">
          No categories yet. Create your first one.
        </div>

        <div v-else class="overflow-auto rounded-md border">
          <table class="w-full text-sm">
            <thead class="bg-muted/30 text-left">
              <tr>
                <th class="px-3 py-2 font-medium">Name</th>
                <th class="px-3 py-2 font-medium">Created</th>
                <th class="w-24 px-3 py-2 font-medium text-right">Actions</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="category in sortedCategories" :key="category.id" class="border-t">
                <td class="px-3 py-2">{{ category.name }}</td>
                <td class="px-3 py-2 text-muted-foreground">
                  {{ new Date(category.createdAt).toLocaleDateString() }}
                </td>
                <td class="px-3 py-2">
                  <div class="flex items-center justify-end gap-1">
                    <Button
                      variant="ghost"
                      size="icon"
                      class="size-8"
                      @click="startRename(category)"
                    >
                      <Pencil class="size-4" />
                    </Button>
                    <Button
                      variant="ghost"
                      size="icon"
                      class="size-8 text-red-600 hover:text-red-700"
                      :disabled="deletingId === category.id"
                      @click="deleteCategory(category)"
                    >
                      <Trash2 class="size-4" />
                    </Button>
                  </div>
                </td>
              </tr>
            </tbody>
          </table>
        </div>

        <p v-if="deleteError" class="mt-3 text-sm text-red-600">{{ deleteError }}</p>
      </CardContent>
    </Card>

    <Dialog v-model:open="renameDialogOpen">
      <DialogContent>
        <DialogHeader>
          <DialogTitle>Rename Category</DialogTitle>
          <DialogDescription> Update the category name. </DialogDescription>
        </DialogHeader>

        <div class="grid gap-2 py-4">
          <Label for="rename-category-name">Name</Label>
          <Input id="rename-category-name" v-model="renameName" />
          <p v-if="renameError" class="text-sm text-red-600">{{ renameError }}</p>
        </div>

        <DialogFooter>
          <Button :disabled="isRenaming || !renameName.trim()" @click="submitRename">
            {{ isRenaming ? 'Saving...' : 'Save' }}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  </section>
</template>
