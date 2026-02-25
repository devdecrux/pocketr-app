import { createPinia } from 'pinia'
import { describe, expect, it, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import { createMemoryHistory, createRouter } from 'vue-router'
import App from '../App.vue'

vi.mock('@vueuse/core', async () => {
  const actual = await vi.importActual<typeof import('@vueuse/core')>('@vueuse/core')
  return {
    ...actual,
    useColorMode: () => ({
      value: 'light',
    }),
  }
})

describe('App', () => {
  it('renders auth layout without sidebar on login route', async () => {
    const pinia = createPinia()

    const router = createRouter({
      history: createMemoryHistory(),
      routes: [
        {
          path: '/login',
          component: { template: '<div>Login screen</div>' },
          meta: { layout: 'auth' },
        },
      ],
    })

    await router.push('/login')
    await router.isReady()

    const wrapper = mount(App, {
      global: {
        plugins: [pinia, router],
      },
    })

    expect(wrapper.text()).toContain('Login screen')
    expect(wrapper.find('aside').exists()).toBe(false)
  })
})
