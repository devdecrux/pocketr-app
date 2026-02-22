<script setup lang="ts">
import { computed, h, onMounted, ref } from 'vue'
import { type ColumnDef, FlexRender, getCoreRowModel, useVueTable } from '@tanstack/vue-table'
import { Pencil, Plus, Trash2 } from 'lucide-vue-next'
import { useCategoryStore } from '@/stores/category'
import type { CategoryTag } from '@/types/ledger'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle, DialogTrigger } from '@/components/ui/dialog'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Skeleton } from '@/components/ui/skeleton'

const categoryStore = useCategoryStore()

const PRESET_COLORS = [
  '#ef4444',
  '#f97316',
  '#f59e0b',
  '#84cc16',
  '#22c55e',
  '#14b8a6',
  '#06b6d4',
  '#3b82f6',
  '#8b5cf6',
  '#ec4899',
  '#6b7280',
  '#0ea5e9',
]

const createDialogOpen = ref(false)
const createName = ref('')
const createColor = ref<string | null>(null)
const createError = ref('')
const isCreating = ref(false)

const renameDialogOpen = ref(false)
const renameTarget = ref<CategoryTag | null>(null)
const renameName = ref('')
const renameColor = ref<string | null>(null)
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
  renameColor.value = category.color ?? null
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

  const created = await categoryStore.create(trimmedName, createColor.value)
  if (created) {
    createDialogOpen.value = false
    createName.value = ''
    createColor.value = null
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

  const updated = await categoryStore.rename(target.id, trimmedName, renameColor.value)
  if (updated) {
    renameDialogOpen.value = false
    renameTarget.value = null
    renameName.value = ''
    renameColor.value = null
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

const columns: ColumnDef<CategoryTag>[] = [
  {
    accessorKey: 'name',
    header: 'Name',
    cell: ({ row }) => {
      const cat = row.original
      return h('div', { class: 'flex items-center justify-end gap-2' }, [
        cat.color
          ? h('span', {
              class: 'inline-block h-3.5 w-3.5 shrink-0 rounded-full border border-border',
              style: { backgroundColor: cat.color },
            })
          : null,
        h('span', {}, cat.name),
      ])
    },
  },
  {
    accessorKey: 'createdAt',
    header: 'Created',
    cell: ({ row }) => new Date(row.original.createdAt).toLocaleDateString(),
  },
  {
    id: 'actions',
    header: '',
    cell: ({ row }) =>
      h('div', { class: 'flex items-center justify-end gap-1' }, [
        h(
          Button,
          {
            variant: 'ghost',
            size: 'icon',
            class: 'size-8',
            onClick: () => startRename(row.original),
          },
          () => h(Pencil, { class: 'size-4' }),
        ),
        h(
          Button,
          {
            variant: 'ghost',
            size: 'icon',
            class: 'size-8 text-red-600 hover:text-red-700',
            disabled: deletingId.value === row.original.id,
            onClick: () => deleteCategory(row.original),
          },
          () => h(Trash2, { class: 'size-4' }),
        ),
      ]),
  },
]

const table = useVueTable({
  get data() {
    return sortedCategories.value
  },
  columns,
  getCoreRowModel: getCoreRowModel(),
})
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

            <div class="grid gap-4 py-4">
              <div class="grid gap-2">
                <Label for="create-category-name">Name</Label>
                <Input
                  id="create-category-name"
                  v-model="createName"
                  placeholder="e.g. Groceries, Utilities"
                />
              </div>
              <div class="grid gap-2">
                <Label>Color</Label>
                <div class="flex flex-wrap gap-2">
                  <button
                    v-for="color in PRESET_COLORS"
                    :key="color"
                    type="button"
                    class="h-6 w-6 rounded-full border-2 transition-transform hover:scale-110"
                    :style="{ backgroundColor: color }"
                    :class="
                      createColor === color ? 'border-foreground scale-110' : 'border-transparent'
                    "
                    @click="createColor = color"
                  />
                  <button
                    type="button"
                    class="flex h-6 w-6 items-center justify-center rounded-full border-2 border-dashed text-[10px] text-muted-foreground transition-colors hover:border-foreground"
                    :class="createColor === null ? 'border-foreground' : 'border-muted-foreground'"
                    title="No color"
                    @click="createColor = null"
                  >
                    ✕
                  </button>
                </div>
              </div>
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

        <div v-else class="overflow-auto rounded-md border">
          <table class="w-full text-sm">
            <thead>
              <tr
                v-for="headerGroup in table.getHeaderGroups()"
                :key="headerGroup.id"
                class="border-b bg-muted/50"
              >
                <th
                  v-for="header in headerGroup.headers"
                  :key="header.id"
                  class="px-4 py-2 text-right font-medium text-muted-foreground"
                >
                  <FlexRender
                    v-if="!header.isPlaceholder"
                    :render="header.column.columnDef.header"
                    :props="header.getContext()"
                  />
                </th>
              </tr>
            </thead>
            <tbody>
              <tr
                v-for="row in table.getRowModel().rows"
                :key="row.id"
                class="border-b last:border-0"
              >
                <td
                  v-for="cell in row.getVisibleCells()"
                  :key="cell.id"
                  class="px-4 py-2 text-right"
                >
                  <FlexRender :render="cell.column.columnDef.cell" :props="cell.getContext()" />
                </td>
              </tr>
              <tr v-if="table.getRowModel().rows.length === 0">
                <td :colspan="columns.length" class="px-4 py-8 text-center text-muted-foreground">
                  No categories found.
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

        <div class="grid gap-4 py-4">
          <div class="grid gap-2">
            <Label for="rename-category-name">Name</Label>
            <Input id="rename-category-name" v-model="renameName" />
          </div>
          <div class="grid gap-2">
            <Label>Color</Label>
            <div class="flex flex-wrap gap-2">
              <button
                v-for="color in PRESET_COLORS"
                :key="color"
                type="button"
                class="h-6 w-6 rounded-full border-2 transition-transform hover:scale-110"
                :style="{ backgroundColor: color }"
                :class="
                  renameColor === color ? 'border-foreground scale-110' : 'border-transparent'
                "
                @click="renameColor = color"
              />
              <button
                type="button"
                class="flex h-6 w-6 items-center justify-center rounded-full border-2 border-dashed text-[10px] text-muted-foreground transition-colors hover:border-foreground"
                :class="renameColor === null ? 'border-foreground' : 'border-muted-foreground'"
                title="No color"
                @click="renameColor = null"
              >
                ✕
              </button>
            </div>
          </div>
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
