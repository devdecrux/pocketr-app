import {themes as prismThemes} from 'prism-react-renderer';
import type {Config} from '@docusaurus/types';
import type * as Preset from '@docusaurus/preset-classic';

// This runs in Node.js - Don't use client-side code here (browser APIs, JSX...)

const config: Config = {
  title: 'Pocketr',
  tagline: 'Personal finance tracking for everyday decisions',
  favicon: 'img/favicon.ico',

  // Future flags, see https://docusaurus.io/docs/api/docusaurus-config#future
  future: {
    v4: true, // Improve compatibility with the upcoming Docusaurus v4
  },

  // Set the public URL of your site here.
  url: 'https://docs.pocketr.app',
  // Set the /<baseUrl>/ pathname under which your site is served
  // For GitHub pages deployment, it is often '/<projectName>/'
  baseUrl: '/',
  trailingSlash: false,

  // GitHub pages deployment config.
  // If you aren't using GitHub pages, you don't need these.
  organizationName: 'devdecrux', // Usually your GitHub org/user name.
  projectName: 'pocketr-app', // Usually your repo name.

  onBrokenLinks: 'throw',

  // Even if you don't use internationalization, you can use this field to set
  // useful metadata like html lang. For example, if your site is Chinese, you
  // may want to replace "en" with "zh-Hans".
  i18n: {
    defaultLocale: 'en',
    locales: ['en'],
  },

  presets: [
    [
      'classic',
      {
        docs: {
          routeBasePath: '/',
          sidebarPath: './sidebars.ts',
          // Please change this to your repo.
          // Remove this to remove the "edit this page" links.
          editUrl:
            'https://github.com/devdecrux/pocketr-app/tree/master/pocketr-docs/',
        },
        blog: {
          showReadingTime: true,
          feedOptions: {
            type: ['rss', 'atom'],
            xslt: true,
          },
          // Please change this to your repo.
          // Remove this to remove the "edit this page" links.
          editUrl:
            'https://github.com/devdecrux/pocketr-app/tree/master/pocketr-docs/',
          // Useful options to enforce blogging best practices
          onInlineTags: 'warn',
          onInlineAuthors: 'warn',
          onUntruncatedBlogPosts: 'warn',
        },
        theme: {
          customCss: './src/css/custom.css',
        },
      } satisfies Preset.Options,
    ],
  ],

  themeConfig: {
    // Replace with your project's social card
    image: 'img/docusaurus-social-card.jpg',
    colorMode: {
      respectPrefersColorScheme: true,
    },
    navbar: {
      title: 'Pocketr',
      logo: {
        alt: 'Pocketr Logo',
        src: 'img/logo.svg',
      },
      items: [
        {
          type: 'docSidebar',
          sidebarId: 'tutorialSidebar',
          position: 'left',
          label: 'Docs',
        },
        {to: '/blog', label: 'Blog', position: 'left'},
        {
          type: 'html',
          position: 'right',
          className: 'github-stars-navbar-item',
          value:
            '<a href="https://github.com/devdecrux/pocketr-app" target="_blank" rel="noopener noreferrer" aria-label="Pocketr GitHub repository stars"><img src="https://img.shields.io/github/stars/devdecrux/pocketr-app?style=social" alt="GitHub Repo stars" /></a>',
        },
        {
          href: 'https://github.com/devdecrux/pocketr-app',
          position: 'right',
          className: 'header-github-link',
          'aria-label': 'Pocketr on GitHub',
          title: 'GitHub',
        },
        {
          href: 'https://discord.gg/HtzYp9bM25',
          position: 'right',
          className: 'header-discord-link',
          'aria-label': 'Join Pocketr on Discord',
          title: 'Discord',
        },
      ],
    },
    footer: {
      style: 'dark',
      copyright: 'Copyright © 2026 Pocketr contributors. Built with Docusaurus.',
    },
    prism: {
      theme: prismThemes.github,
      darkTheme: prismThemes.dracula,
    },
  } satisfies Preset.ThemeConfig,
};

export default config;
