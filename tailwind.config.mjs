/** @type {import('tailwindcss').Config} */
export default {
  content: ['./src/**/*.{astro,html,js,jsx,md,mdx,ts,tsx}'],
  darkMode: ['class', '[data-theme="dark"]'],
  theme: {
    extend: {
      colors: {
        obs: {
          canvas: 'var(--obs-canvas)',
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
            subtle: 'var(--obs-accent-subtle)',
          },
          tag: 'var(--obs-tag-bg)',
          code: 'var(--obs-code-bg)',
        },
      },
      fontFamily: {
        sans: [
          'Inter',
          '-apple-system',
          'BlinkMacSystemFont',
          'Segoe UI',
          'Roboto',
          'sans-serif',
        ],
        mono: [
          'JetBrains Mono',
          'Fira Code',
          'ui-monospace',
          'SFMono-Regular',
          'Menlo',
          'Monaco',
          'Consolas',
          'monospace',
        ],
      },
      typography: {
        DEFAULT: {
          css: {
            maxWidth: '100%',
            color: 'var(--obs-text-secondary)',
            a: {
              color: 'var(--obs-accent)',
              textDecoration: 'none',
              '&:hover': {
                textDecoration: 'underline',
              },
            },
            strong: {
              color: 'var(--obs-text-primary)',
            },
            h1: {
              color: 'var(--obs-text-primary)',
            },
            h2: {
              color: 'var(--obs-text-primary)',
            },
            h3: {
              color: 'var(--obs-text-primary)',
            },
            h4: {
              color: 'var(--obs-text-primary)',
            },
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
            'code::before': {
              content: '""',
            },
            'code::after': {
              content: '""',
            },
            blockquote: {
              color: 'var(--obs-text-secondary)',
              borderLeftColor: 'var(--obs-accent)',
            },
            hr: {
              borderColor: 'var(--obs-border)',
            },
            th: {
              color: 'var(--obs-text-primary)',
              borderColor: 'var(--obs-border)',
            },
            td: {
              borderColor: 'var(--obs-border)',
            },
          },
        },
      },
    },
  },
  plugins: [
    import('@tailwindcss/typography').then((m) => m.default || m),
  ],
};
