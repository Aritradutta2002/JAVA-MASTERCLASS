import typography from '@tailwindcss/typography';

/** @type {import('tailwindcss').Config} */
export default {
  content: ['./src/**/*.{astro,html,js,jsx,md,mdx,ts,tsx}'],
  darkMode: ['class', '[data-theme="dark"]'],
  theme: {
    extend: {
      colors: {
        obs: {
          canvas: 'var(--obs-canvas)',
          'canvas-soft': 'var(--obs-canvas-soft)',
          sidebar: 'var(--obs-sidebar)',
          surface: 'var(--obs-surface)',
          hover: 'var(--obs-surface-hover)',
          border: 'var(--obs-border)',
          'border-subtle': 'var(--obs-border-subtle)',
          'text-primary': 'var(--obs-text-primary)',
          'text-secondary': 'var(--obs-text-secondary)',
          'text-muted': 'var(--obs-text-muted)',
          accent: {
            DEFAULT: 'var(--obs-accent)',
            hover: 'var(--obs-accent-hover)',
            strong: 'var(--obs-accent-strong)',
            subtle: 'var(--obs-accent-subtle)',
          },
          gold: 'var(--obs-gold)',
          rose: 'var(--obs-rose)',
          emerald: 'var(--obs-emerald)',
          tag: 'var(--obs-tag-bg)',
          code: 'var(--obs-code-bg)',
        },
      },
      fontFamily: {
        sans: [
          'Inter Variable',
          'Inter',
          '-apple-system',
          'BlinkMacSystemFont',
          'Segoe UI',
          'Roboto',
          'sans-serif',
          'Apple Color Emoji',
          'Segoe UI Emoji',
          'Noto Color Emoji',
        ],
        serif: [
          'Literata Variable',
          'Literata',
          'Georgia',
          'Cambria',
          'serif',
          'Apple Color Emoji',
          'Segoe UI Emoji',
          'Noto Color Emoji',
        ],
        mono: [
          'JetBrains Mono Variable',
          'JetBrains Mono',
          'ui-monospace',
          'SFMono-Regular',
          'Menlo',
          'Monaco',
          'Consolas',
          'monospace',
        ],
      },
      maxWidth: {
        reading: '46rem',
        wide: '80rem',
      },
      spacing: {
        header: 'var(--obs-header-height)',
        sidebar: 'var(--obs-sidebar-width)',
        toc: 'var(--obs-toc-width)',
      },
      boxShadow: {
        obs: 'var(--obs-shadow)',
        'obs-lg': 'var(--obs-shadow-lg)',
      },
      borderRadius: {
        xl2: '0.9rem',
      },
      keyframes: {
        fadeIn: {
          '0%': { opacity: '0' },
          '100%': { opacity: '1' },
        },
        slideUp: {
          '0%': { opacity: '0', transform: 'translateY(6px)' },
          '100%': { opacity: '1', transform: 'translateY(0)' },
        },
      },
      animation: {
        'fade-in': 'fadeIn 140ms ease-out',
        'slide-up': 'slideUp 180ms ease-out',
      },
      typography: {
        DEFAULT: {
          css: {
            maxWidth: '100%',
            color: 'var(--obs-text-secondary)',
            a: {
              color: 'var(--obs-accent)',
              textDecoration: 'none',
              '&:hover': { textDecoration: 'underline' },
            },
            strong: { color: 'var(--obs-text-primary)' },
            h1: { color: 'var(--obs-text-primary)' },
            h2: { color: 'var(--obs-text-primary)' },
            h3: { color: 'var(--obs-text-primary)' },
            h4: { color: 'var(--obs-text-primary)' },
            code: {
              color: 'var(--obs-accent)',
              backgroundColor: 'var(--obs-tag-bg)',
              paddingLeft: '0.25rem',
              paddingRight: '0.25rem',
              paddingTop: '0.125rem',
              paddingBottom: '0.125rem',
              borderRadius: '0.25rem',
              fontWeight: '400',
            },
            'code::before': { content: '""' },
            'code::after': { content: '""' },
            blockquote: {
              color: 'var(--obs-text-secondary)',
              borderLeftColor: 'var(--obs-accent)',
            },
            hr: { borderColor: 'var(--obs-border)' },
            th: {
              color: 'var(--obs-text-primary)',
              borderColor: 'var(--obs-border)',
            },
            td: { borderColor: 'var(--obs-border)' },
          },
        },
      },
    },
  },
  plugins: [typography],
};
