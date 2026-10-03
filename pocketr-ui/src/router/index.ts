import { createRouter, createWebHistory } from 'vue-router'
import { authGuard } from '@/router/guards'

const AccountsPage = () => import('@/views/AccountsPage.vue')
const CategoriesPage = () => import('@/views/CategoriesPage.vue')
const DashboardPage = () => import('@/views/DashboardPage.vue')
const HouseholdSettingsPage = () => import('@/views/HouseholdSettingsPage.vue')
const LoginPage = () => import('@/views/auth/LoginPage.vue')
const NotFoundPage = () => import('@/views/NotFoundPage.vue')
const RegistrationPage = () => import('@/views/auth/RegistrationPage.vue')
const SettingsPage = () => import('@/views/SettingsPage.vue')
const TransactionsPage = () => import('@/views/TransactionsPage.vue')

const routes = [
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
    meta: { requiresAuth: true },
  },
  {
    path: '/transactions',
    name: 'transactions',
    component: TransactionsPage,
    meta: { requiresAuth: true },
  },
  {
    path: '/accounts',
    name: 'accounts',
    component: AccountsPage,
    meta: { requiresAuth: true },
  },
  {
    path: '/categories',
    name: 'categories',
    component: CategoriesPage,
    meta: { requiresAuth: true },
  },
  {
    path: '/settings',
    name: 'settings',
    component: SettingsPage,
    meta: { requiresAuth: true },
  },
  {
    path: '/household/:householdId/settings',
    name: 'household-settings',
    component: HouseholdSettingsPage,
    meta: { requiresAuth: true },
  },
  {
    path: '/:pathMatch(.*)*',
    name: 'not-found',
    component: NotFoundPage,
    meta: { requiresAuth: true },
  },
]

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes,
})

router.beforeEach(authGuard)

export default router
