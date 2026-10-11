import { createApp } from 'vue'
import { createPinia } from 'pinia'
import ui from '@nuxt/ui/vue-plugin'

import './styles/main.css'
import App from './App.vue'
import { primeCsrfToken } from './api/csrf'
import { i18n, initLocale } from './i18n'
import router from './router'

const app = createApp(App)

app.use(createPinia())
app.use(i18n)
app.use(router)
app.use(ui)

// Mount once the active locale's messages are loaded, so the first render never shows raw keys.
// No top-level `await` here: the locale and route chunks import this entry module, so awaiting
// them while the entry is still evaluating would deadlock the dynamic imports (blank page).
void initLocale().then(() => {
  app.mount('#app')
  void primeCsrfToken()
})
