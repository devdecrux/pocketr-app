import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router'
import { authGuard } from '@/router/guards'

const AccountsPage = () => import('@/views/accounts/AccountsPage.vue')
const CategoriesPage = () => import('@/views/categories/CategoriesPage.vue')
const DashboardPage = () => import('@/views/dashboard/DashboardPage.vue')
const HouseholdSettingsPage = () => import('@/views/household/HouseholdSettingsPage.vue')
const LoginPage = () => import('@/views/auth/LoginPage.vue')
const NotFoundPage = () => import('@/views/not-found/NotFoundPage.vue')
const RegistrationPage = () => import('@/views/auth/RegistrationPage.vue')
const SettingsPage = () => import('@/views/settings/SettingsPage.vue')
const TransactionsPage = () => import('@/views/transactions/TransactionsPage.vue')

declare module 'vue-router' {
  interface RouteMeta {
    layout?: 'auth'
    guestOnly?: boolean
    requiresAuth?: boolean
    /** i18n key of the page title shown in the browser tab, before the app name. */
    titleKey?: string
  }
}

const routes: RouteRecordRaw[] = [
  {
    path: '/',
    redirect: '/dashboard',
  },
  {
    path: '/login',
    name: 'login',
    component: LoginPage,
    meta: { layout: 'auth', guestOnly: true },
  },
  {
    path: '/registration',
    name: 'registration',
    component: RegistrationPage,
    meta: { layout: 'auth', guestOnly: true },
  },
  {
    path: '/dashboard',
    name: 'dashboard',
    component: DashboardPage,
    meta: { requiresAuth: true, titleKey: 'views.dashboard.title' },
  },
  {
    path: '/transactions',
    name: 'transactions',
    component: TransactionsPage,
    meta: { requiresAuth: true, titleKey: 'views.transactions.title' },
  },
  {
    path: '/accounts',
    name: 'accounts',
    component: AccountsPage,
    meta: { requiresAuth: true, titleKey: 'views.accounts.title' },
  },
  {
    path: '/categories',
    name: 'categories',
    component: CategoriesPage,
    meta: { requiresAuth: true, titleKey: 'views.categories.title' },
  },
  {
    path: '/settings',
    name: 'settings',
    component: SettingsPage,
    meta: { requiresAuth: true, titleKey: 'views.settings.title' },
  },
  {
    path: '/household/:householdId/settings',
    name: 'household-settings',
    component: HouseholdSettingsPage,
    meta: { requiresAuth: true, titleKey: 'views.householdSettings.title' },
  },
  {
    path: '/:pathMatch(.*)*',
    name: 'not-found',
    component: NotFoundPage,
    meta: { requiresAuth: true, titleKey: 'views.notFound.title' },
  },
]

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes,
})

router.beforeEach(authGuard)

export default router
