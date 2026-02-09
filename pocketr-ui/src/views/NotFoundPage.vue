<template>
  <section
    class="rounded-2xl border border-slate-200 bg-white p-6 shadow-sm dark:border-border dark:bg-card"
  >
    <h1 class="text-2xl font-semibold text-slate-900 dark:text-foreground">Page not found</h1>
    <p class="mt-2 text-sm text-slate-600 dark:text-muted-foreground">
      The page you requested does not exist.
    </p>
    <RouterLink
      to="/dashboard"
      class="mt-4 inline-flex rounded-lg bg-slate-900 px-4 py-2 text-sm font-medium text-white hover:bg-slate-700 dark:bg-primary dark:text-primary-foreground dark:hover:bg-primary/90"
    >
      Back to dashboard
    </RouterLink>
  </section>
</template>
