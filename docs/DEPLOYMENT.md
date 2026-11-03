# Deployment guide

This site is a fully static Astro build. There is no server, database, or runtime
dependency in production. The output in `dist/` can be served by any static host.

The only build-time requirements are Node.js, npm, and the repository content.

---

## 1. Requirements

| Requirement | Version |
| --- | --- |
| Node.js | 22 LTS or newer |
| npm | 10 or newer |
| Disk | ~1 GB for dependencies and the build |
| Build memory | 2 GB+ recommended |

The repository ships a `.nvmrc` pinned to Node 22, and `package.json` declares
`engines.node >= 22`. The content engine reads every Markdown file in the
repository during the build. A clean production build renders 400+ chapters and
takes about two to three minutes on a standard CI runner.

---

## 2. Build commands

```bash
# Install exact dependency versions
npm ci

# Type-check Astro and TypeScript files
npm run check

# Produce the static site and the Pagefind index
npm run build

# Validate every internal route and anchor in dist/
npm run verify

# Preview the production output locally
npm run preview
```

`npm run build` runs `astro build` and then the `postbuild` script, which:

1. Generates the Pagefind full-text index into `dist/pagefind/`.
2. Prints a summary of pages and index fragments.

If Pagefind is unavailable, the build still succeeds and the `/search` page
falls back to the instant command palette.

---

## 3. Hosting

The site uses absolute root paths (`/core-java/...`) and a single site origin.
Deploy it at a domain root, not under a sub-path.

### Vercel

- Framework preset: **Astro**
- Build command: `npm run build`
- Output directory: `dist`
- Node version: 22.x

Vercel runs `npm run build` including the `postbuild` lifecycle script, so
Pagefind is generated automatically.

### Netlify

- Build command: `npm run build`
- Publish directory: `dist`
- Environment: `NODE_VERSION=22`

`netlify.toml`:

```toml
[build]
  command = "npm run build"
  publish = "dist"

[build.environment]
  NODE_VERSION = "22"
```

### Cloudflare Pages

- Framework preset: **Astro**
- Build command: `npm run build`
- Build output directory: `dist`
- Node version: 22

### GitHub Pages

GitHub Pages needs a repository-relative base path unless a custom domain is
configured. If you deploy to `https://<user>.github.io/<repo>/`, set `base` in
`astro.config.mjs` and update the `SITE_URL` constant so canonical URLs and the
sitemap stay correct.

```js
export default defineConfig({
  site: 'https://<user>.github.io',
  base: '/<repo>',
  // ...
});
```

### Any static file server

Upload the contents of `dist/` to S3, Cloudflare R2, nginx, Caddy, or any other
static host. Configure:

- `404.html` as the not-found document.
- Long-lived caching for `/_astro/*` (content-hashed filenames).
- Short caching for HTML and `/pagefind/*` so new content is picked up.
- `sitemap-index.xml` and `robots.txt` served from the site root.

---

## 4. Search indexes

Pagefind builds a static, chunked index. It is loaded only on `/search` and it
does not require a search service.

- Index output: `dist/pagefind/`
- Search UI: `dist/pagefind/pagefind-ui.js` and `pagefind-ui.css`
- The instant command palette uses `dist/search-index.json` (MiniSearch)
- Pages marked `noindex` (search, 404) are excluded from the Pagefind body index

After deploying, search works immediately. If you change content, rebuild so the
index is regenerated.

---

## 5. Caching and headers

Recommended headers for the static host:

```text
/_astro/*
  Cache-Control: public, max-age=31536000, immutable

/pagefind/*
  Cache-Control: public, max-age=86400

/*.html
  Cache-Control: public, max-age=0, must-revalidate
```

Set `Content-Type: application/xml` for `sitemap-index.xml` and
`sitemap-0.xml` if your host does not infer it.

Netlify and Cloudflare Pages also read the bundled `public/_headers` file, which
is copied to `dist/_headers` and already contains the rules above.

---

## 6. Post-deploy checklist

1. `/` loads and the hero statistics are non-zero.
2. `/library` lists all domains and the filter works.
3. `/search` returns results for a known term such as `Kafka`, `JVM`, or `CAP`.
4. `/graph` renders nodes and the list view is reachable.
5. `/checklist` persists checks after a reload.
6. A deep chapter URL loads directly (not only via client-side navigation).
7. `sitemap-index.xml` resolves and contains chapter URLs.
8. `robots.txt` points at the sitemap.
9. Lighthouse: performance and accessibility above 90 on a mobile profile.
10. Keyboard: skip link, command palette, sidebar tree, and theme toggle all work.

---

## 7. Troubleshooting

| Symptom | Cause | Fix |
| --- | --- | --- |
| `Duplicate content slug` during build | Two Markdown files map to one URL | Rename one file or adjust the slug rules in `src/lib/content.ts` |
| Search page falls back to the palette | Pagefind did not run | Run `npm run build` (not `astro build` alone) |
| Mermaid diagrams render as source | Mermaid failed to load | Check the browser console; the source is always available in the diagram `<details>` |
| Fonts or KaTeX look unstyled | CSS import failed | Confirm `@fontsource-variable/*` and `katex/dist/katex.min.css` are installed |
| Sitemap contains JSON endpoints | Filter regression | Check the `sitemap` filter in `astro.config.mjs` |
| Build runs out of memory | Very large Markdown set | Raise the CI Node heap (`NODE_OPTIONS=--max-old-space-size=4096`) |