import { defineConfig } from 'astro/config';
import react from '@astrojs/react';
import tailwind from '@astrojs/tailwind';
import sitemap from '@astrojs/sitemap';
import remarkMath from 'remark-math';
import rehypeKatex from 'rehype-katex';
import rehypeSlug from 'rehype-slug';

export const SITE_URL = 'https://java-masterclass.dev';

// https://astro.build/config
export default defineConfig({
  site: SITE_URL,
  output: 'static',
  trailingSlash: 'ignore',
  prefetch: {
    prefetchAll: false,
    defaultStrategy: 'hover',
  },
  integrations: [
    react(),
    tailwind({
      applyBaseStyles: false,
    }),
    sitemap({
      filter: (page) => {
        const pathname = new URL(page, SITE_URL).pathname;
        if (pathname === '/404' || pathname === '/search') return false;
        if (pathname.startsWith('/search-index') || pathname.startsWith('/graph-data')) return false;
        // /standards/<domain> mirrors the canonical domain standard document.
        // Keep the /standards index itself in the sitemap.
        if (pathname.startsWith('/standards') && pathname !== '/standards' && pathname !== '/standards/') {
          return false;
        }
        return true;
      },
      changefreq: 'weekly',
      priority: 0.7,
      lastmod: new Date(),
    }),
  ],
  markdown: {
    remarkPlugins: [remarkMath],
    rehypePlugins: [rehypeKatex, rehypeSlug],
    shikiConfig: {
      themes: {
        dark: 'one-dark-pro',
        light: 'github-light',
      },
      wrap: true,
    },
  },
  vite: {
    ssr: {
      noExternal: ['lucide-react'],
    },
    build: {
      cssCodeSplit: true,
    },
  },
});
