import { createApp } from 'vue'
import { createPinia } from 'pinia'

import './main.css'
import App from './App.vue'
import { primeCsrfToken } from './api/csrf'
import router from './router'

const app = createApp(App)

app.use(createPinia())
app.use(router)

app.mount('#app')

void primeCsrfToken()
