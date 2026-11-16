import typography from '@tailwindcss/typography';

/** @type {import('tailwindcss').Config} */
export default {
  content: ['./src/**/*.{astro,html,js,jsx,md,mdx,ts,tsx}'],
  darkMode: ['class', '[data-theme="dark"]'],
  theme: {
    extend: {
      colors: {
        // Deep obsidian dark palette tokens
        obsidian: {
          50: '#f6f6f8',
          100: '#eeeef1',
          200: '#dcdce2',
          300: '#bfbfc9',
          400: '#9b9aa8',
          500: '#7b798b',
          600: '#5c5a6c',
          700: '#434151',
          800: '#2e2e3c', // border
          850: '#22222d', // surface-hover
          900: '#18181f', // surface
          950: '#121216', // canvas-soft / sidebar
          975: '#09090b', // deep canvas
        },
        // Enterprise Neutral scale from 50 to 950
        neutral: {
          50: '#f8fafc',
          100: '#f1f5f9',
          200: '#e2e8f0',
          300: '#cbd5e1',
          400: '#94a3b8',
          500: '#64748b',
          600: '#475569',
          700: '#334155',
          800: '#1e293b',
          900: '#0f172a',
          950: '#020617',
        },
        obs: {
          // RGB channel tokens let Tailwind generate opacity modifiers such as
          // bg-obs-surface/60 and border-obs-accent/30.
          canvas: 'rgb(var(--obs-canvas-rgb) / <alpha-value>)',
          'canvas-soft': 'rgb(var(--obs-canvas-soft-rgb) / <alpha-value>)',
          sidebar: 'rgb(var(--obs-sidebar-rgb) / <alpha-value>)',
          surface: 'rgb(var(--obs-surface-rgb) / <alpha-value>)',
          hover: 'rgb(var(--obs-surface-hover-rgb) / <alpha-value>)',
          'surface-hover': 'rgb(var(--obs-surface-hover-rgb) / <alpha-value>)',
          base: 'rgb(var(--obs-canvas-rgb) / <alpha-value>)',
          border: 'rgb(var(--obs-border-rgb) / <alpha-value>)',
          'border-subtle': 'rgb(var(--obs-border-subtle-rgb) / <alpha-value>)',
          'text-primary': 'rgb(var(--obs-text-primary-rgb) / <alpha-value>)',
          'text-secondary': 'rgb(var(--obs-text-secondary-rgb) / <alpha-value>)',
          'text-muted': 'rgb(var(--obs-text-muted-rgb) / <alpha-value>)',
          accent: {
            DEFAULT: 'rgb(var(--obs-accent-rgb) / <alpha-value>)',
            hover: 'rgb(var(--obs-accent-hover-rgb) / <alpha-value>)',
            strong: 'rgb(var(--obs-accent-strong-rgb) / <alpha-value>)',
            solid: 'rgb(var(--obs-accent-solid-rgb) / <alpha-value>)',
            'solid-hover': 'rgb(var(--obs-accent-solid-hover-rgb) / <alpha-value>)',
            subtle: 'var(--obs-accent-subtle)',
          },
          gold: 'rgb(var(--obs-gold-rgb) / <alpha-value>)',
          rose: 'rgb(var(--obs-rose-rgb) / <alpha-value>)',
          emerald: 'rgb(var(--obs-emerald-rgb) / <alpha-value>)',
          tag: 'rgb(var(--obs-tag-bg-rgb) / <alpha-value>)',
          code: 'rgb(var(--obs-code-bg-rgb) / <alpha-value>)',
          // Semantic color tokens
          success: {
            DEFAULT: 'rgb(var(--obs-success-rgb) / <alpha-value>)',
            text: 'rgb(var(--obs-success-text-rgb) / <alpha-value>)',
            solid: 'rgb(var(--obs-success-solid-rgb) / <alpha-value>)',
            'solid-hover': 'rgb(var(--obs-success-solid-hover-rgb) / <alpha-value>)',
            subtle: 'var(--obs-success-subtle)',
          },
          warning: {
            DEFAULT: 'rgb(var(--obs-warning-rgb) / <alpha-value>)',
            text: 'rgb(var(--obs-warning-text-rgb) / <alpha-value>)',
            solid: 'rgb(var(--obs-warning-solid-rgb) / <alpha-value>)',
            'solid-hover': 'rgb(var(--obs-warning-solid-hover-rgb) / <alpha-value>)',
            subtle: 'var(--obs-warning-subtle)',
          },
          error: {
            DEFAULT: 'rgb(var(--obs-error-rgb) / <alpha-value>)',
            text: 'rgb(var(--obs-error-text-rgb) / <alpha-value>)',
            solid: 'rgb(var(--obs-error-solid-rgb) / <alpha-value>)',
            'solid-hover': 'rgb(var(--obs-error-solid-hover-rgb) / <alpha-value>)',
            subtle: 'var(--obs-error-subtle)',
          },
          info: {
            DEFAULT: 'rgb(var(--obs-info-rgb) / <alpha-value>)',
            text: 'rgb(var(--obs-info-text-rgb) / <alpha-value>)',
            solid: 'rgb(var(--obs-info-solid-rgb) / <alpha-value>)',
            'solid-hover': 'rgb(var(--obs-info-solid-hover-rgb) / <alpha-value>)',
            subtle: 'var(--obs-info-subtle)',
          },
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
        heading: [
          'Inter Variable',
          'Inter',
          '-apple-system',
          'BlinkMacSystemFont',
          'Segoe UI',
          'Roboto',
          'sans-serif',
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
      fontSize: {
        'display-1': [
          'clamp(3rem, 5vw, 4rem)',
          { lineHeight: '1.08', letterSpacing: '-0.025em', fontWeight: '800' },
        ],
        'display-2': [
          'clamp(2.25rem, 4vw, 3rem)',
          { lineHeight: '1.14', letterSpacing: '-0.02em', fontWeight: '750' },
        ],
        h1: [
          'clamp(3rem, 5vw, 4rem)',
          { lineHeight: '1.1', letterSpacing: '-0.024em', fontWeight: '750' },
        ],
        h2: [
          'clamp(2.25rem, 3.5vw, 3rem)',
          { lineHeight: '1.16', letterSpacing: '-0.02em', fontWeight: '700' },
        ],
        h3: [
          'clamp(1.5rem, 2.5vw, 2rem)',
          { lineHeight: '1.25', letterSpacing: '-0.015em', fontWeight: '650' },
        ],
        h4: [
          '1.25rem',
          { lineHeight: '1.35', letterSpacing: '-0.012em', fontWeight: '600' },
        ],
        // Article/document titles sit between h4 and h3. A literal 48-64px H1
        // would break the compact document header, so titles use a deliberate,
        // documented scale instead of an accidental utility class.
        'title-lg': [
          'clamp(2rem, 3vw, 2.5rem)',
          { lineHeight: '1.18', letterSpacing: '-0.02em', fontWeight: '700' },
        ],
        'body-lg': [
          '1.125rem',
          { lineHeight: '1.75', letterSpacing: '-0.005em' },
        ],
        body: [
          '1rem',
          { lineHeight: '1.65', letterSpacing: '-0.003em' },
        ],
        caption: [
          '0.875rem',
          { lineHeight: '1.5', letterSpacing: '0em' },
        ],
        micro: [
          '0.75rem',
          { lineHeight: '1.4', letterSpacing: '0.015em' },
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
        18: '4.5rem',
        88: '22rem',
        112: '28rem',
        128: '32rem',
      },
      // Strict 8px border radius system (sm: 8px, md: 12px, lg: 16px, xl: 20px, 2xl: 24px)
      borderRadius: {
        none: '0px',
        xs: '4px',
        sm: '8px',
        DEFAULT: '8px',
        md: '12px',
        lg: '16px',
        xl: '20px',
        '2xl': '24px',
        hero: '24px',
        full: '9999px',
        xl2: '12px', // backward compatibility
      },
      boxShadow: {
        xs: 'var(--obs-shadow-sm)',
        '2xs': 'var(--obs-shadow-sm)',
        'obs-sm': 'var(--obs-shadow-sm)',
        obs: 'var(--obs-shadow)',
        'obs-md': 'var(--obs-shadow)',
        'obs-lg': 'var(--obs-shadow-lg)',
        'obs-xl': 'var(--obs-shadow-xl)',
        'obs-glow': 'var(--obs-shadow-glow)',
        'obs-accent': 'var(--obs-shadow-glow)',
        'glow-emerald': '0 0 24px -4px rgba(16, 185, 129, 0.25)',
        'glow-rose': '0 0 24px -4px rgba(244, 63, 94, 0.25)',
        'glow-accent': '0 0 24px -4px rgba(139, 92, 246, 0.25)',
        'inner-highlight': 'inset 0 1px 0 0 rgba(255, 255, 255, 0.08)',
        'glass-border':
          'inset 0 1px 0 0 rgba(255, 255, 255, 0.1), 0 8px 32px 0 rgba(0, 0, 0, 0.36)',
      },
      keyframes: {
        fadeIn: {
          '0%': { opacity: '0' },
          '100%': { opacity: '1' },
        },
        slideUp: {
          '0%': { opacity: '0', transform: 'translateY(8px)' },
          '100%': { opacity: '1', transform: 'translateY(0)' },
        },
        slideDown: {
          '0%': { opacity: '0', transform: 'translateY(-8px)' },
          '100%': { opacity: '1', transform: 'translateY(0)' },
        },
        scaleIn: {
          '0%': { opacity: '0', transform: 'scale(0.96)' },
          '100%': { opacity: '1', transform: 'scale(1)' },
        },
        shimmer: {
          '100%': { transform: 'translateX(100%)' },
        },
        subtlePulse: {
          '0%, 100%': { opacity: '1' },
          '50%': { opacity: '0.6' },
        },
      },
      animation: {
        'fade-in': 'fadeIn 180ms cubic-bezier(0.16, 1, 0.3, 1)',
        'slide-up': 'slideUp 220ms cubic-bezier(0.16, 1, 0.3, 1)',
        'slide-down': 'slideDown 220ms cubic-bezier(0.16, 1, 0.3, 1)',
        'scale-in': 'scaleIn 180ms cubic-bezier(0.16, 1, 0.3, 1)',
        shimmer: 'shimmer 2s infinite',
        'pulse-subtle': 'subtlePulse 3s cubic-bezier(0.4, 0, 0.6, 1) infinite',
      },
      typography: {
        DEFAULT: {
          css: {
            maxWidth: '100%',
            color: 'var(--obs-text-secondary)',
            a: {
              color: 'var(--obs-accent)',
              textDecoration: 'none',
              fontWeight: '500',
              '&:hover': {
                color: 'var(--obs-accent-hover)',
                textDecoration: 'underline',
              },
            },
            strong: { color: 'var(--obs-text-primary)', fontWeight: '650' },
            h1: {
              color: 'var(--obs-text-primary)',
              fontWeight: '750',
              letterSpacing: '-0.022em',
            },
            h2: {
              color: 'var(--obs-text-primary)',
              fontWeight: '700',
              letterSpacing: '-0.02em',
            },
            h3: {
              color: 'var(--obs-text-primary)',
              fontWeight: '650',
              letterSpacing: '-0.015em',
            },
            h4: {
              color: 'var(--obs-text-primary)',
              fontWeight: '600',
            },
            code: {
              color: 'var(--obs-accent-hover)',
              backgroundColor: 'var(--obs-tag-bg)',
              paddingLeft: '0.35rem',
              paddingRight: '0.35rem',
              paddingTop: '0.15rem',
              paddingBottom: '0.15rem',
              borderRadius: '6px',
              fontWeight: '450',
            },
            'code::before': { content: '""' },
            'code::after': { content: '""' },
            blockquote: {
              color: 'var(--obs-text-secondary)',
              borderLeftColor: 'var(--obs-accent)',
              borderRadius: '0 8px 8px 0',
            },
            hr: { borderColor: 'var(--obs-border)' },
            th: {
              color: 'var(--obs-text-primary)',
              borderColor: 'var(--obs-border)',
              fontWeight: '650',
            },
            td: { borderColor: 'var(--obs-border)' },
          },
        },
      },
    },
  },
  plugins: [typography],
};
