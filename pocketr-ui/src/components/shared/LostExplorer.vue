<script setup lang="ts">
/**
 * Decorative not-found scene: a small explorer sweeps a magnifying glass while the missing page
 * peeks out behind their back. With reduced motion it holds the peeking pose.
 */
</script>

<template>
  <svg
    viewBox="0 0 112 80"
    fill="none"
    stroke="currentColor"
    stroke-width="2.5"
    stroke-linecap="round"
    stroke-linejoin="round"
    aria-hidden="true"
    focusable="false"
    class="lost-explorer h-20 w-28 text-muted"
  >
    <clipPath id="lost-explorer-ground">
      <rect width="112" height="72" />
    </clipPath>
    <path d="M6 72h100" stroke="var(--ui-border)" />
    <g clip-path="url(#lost-explorer-ground)">
      <path
        class="lost-explorer__page"
        d="M16 72V52a2 2 0 0 1 2-2h8l6 6v16M26 50v6h6"
        stroke="var(--ui-secondary)"
      />
    </g>
    <g class="lost-explorer__body">
      <path d="M48 14.5h20M51 14a7 6 0 0 1 14 0" />
      <circle cx="58" cy="21" r="7" />
      <circle
        class="lost-explorer__eye"
        cx="61"
        cy="21"
        r="1.2"
        fill="currentColor"
        stroke="none"
      />
      <path d="M58 28v20M58 48l-6 23M58 48l6 23M58 34l-8 9" />
      <g class="lost-explorer__arm">
        <path d="M58 33l16 3" />
        <circle
          cx="80"
          cy="37"
          r="6"
          stroke="var(--ui-primary)"
          fill="var(--ui-primary)"
          fill-opacity="0.12"
        />
      </g>
    </g>
  </svg>
</template>

<style scoped>
.lost-explorer__body {
  animation: lost-explorer-bob 0.8s ease-in-out infinite alternate;
}

.lost-explorer__arm {
  transform-origin: 58px 33px;
  animation: lost-explorer-scan 3s ease-in-out infinite alternate;
}

.lost-explorer__page {
  animation: lost-explorer-peek 6s ease-in-out infinite;
}

.lost-explorer__eye {
  animation: lost-explorer-glance 6s ease-in-out infinite;
}

@keyframes lost-explorer-bob {
  to {
    transform: translateY(-1px);
  }
}

@keyframes lost-explorer-scan {
  from {
    transform: rotate(10deg);
  }
  to {
    transform: rotate(-16deg);
  }
}

@keyframes lost-explorer-peek {
  0%,
  45%,
  92%,
  100% {
    transform: translateY(24px);
  }
  55%,
  82% {
    transform: translateY(0);
  }
}

@keyframes lost-explorer-glance {
  0%,
  62%,
  80%,
  100% {
    transform: translateX(0);
  }
  66%,
  76% {
    transform: translateX(-4px);
  }
}

@media (prefers-reduced-motion: reduce) {
  .lost-explorer * {
    animation: none;
  }
}
</style>
