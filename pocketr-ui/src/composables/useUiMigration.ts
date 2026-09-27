/**
 * TEMPORARY per-route UI migration switch (Pocketr UI migration runbook, remove in Phase 10).
 *
 * A route opts into the Nuxt UI render path with `meta: { uiV2: true }`. Unflagged routes keep the
 * legacy shell, so legacy and Nuxt UI components never mix on one page.
 */
import { computed, watch } from 'vue'
import { type RouteLocationNormalizedLoaded, useRoute } from 'vue-router'

declare module 'vue-router' {
  interface RouteMeta {
    /** TEMPORARY: render this route through the Nuxt UI (`UApp`) path instead of the legacy shell. */
    uiV2?: boolean
  }
}

/** Set on <body> while a migrated route renders; scopes the bridge rules in `main.css`. */
export const UI_MIGRATION_ATTRIBUTE = 'data-ui-migration'
export const UI_MIGRATION_V2 = 'v2'

export function isUiV2Route(route: Pick<RouteLocationNormalizedLoaded, 'meta'>): boolean {
  return route.meta.uiV2 === true
}

function syncBodyAttribute(isUiV2: boolean): void {
  if (typeof document === 'undefined') return

  if (isUiV2) {
    document.body.setAttribute(UI_MIGRATION_ATTRIBUTE, UI_MIGRATION_V2)
  } else {
    document.body.removeAttribute(UI_MIGRATION_ATTRIBUTE)
  }
}

export function useUiMigration() {
  const route = useRoute()
  const isUiV2 = computed(() => isUiV2Route(route))

  watch(isUiV2, syncBodyAttribute, { immediate: true })

  return { isUiV2 }
}
