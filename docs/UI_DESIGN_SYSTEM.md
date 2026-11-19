# UI Design System

A concise contract for keeping this site consistent, accessible, and fast. The canonical tokens
live in `src/styles/global.css`; `tailwind.config.mjs` exposes them as utilities; the verification
scripts enforce them.

## Tokens (single source of truth)

- `global.css: :root` — dark palette + semantic + domain accents.
- `global.css: :root[data-theme='light']` — light palette (the default) + matching semantic and domain accents. Every token must exist in both.
- `scripts/verify-ui-quality.mjs` reads these variables directly from `global.css`. Do not
  duplicate color values in scripts or components.

Groups: `canvas`, `canvas-soft`, `sidebar`, `surface`, `surface-hover`, `border`, `border-subtle`,
`text-primary/secondary/muted`, `accent*`, `success/warning/error/info*`, and `domain-*`.

Stylesheets load in a deliberate order: canonical tokens and Tailwind primitives in `global.css`,
shared presentation primitives in `presentation.css`, then long-form reader styles in `markdown.css`.

## Typography

| Role | Utility | Size |
|---|---|---|
| Hero H1 | `text-display-1` (with `text-4xl` on small screens) | 48–64px desktop |
| Article title | `text-title-lg` | 32–40px |
| Prose headings | `text-h1`…`text-h4` / `.obsidian-prose h*` | 30–64px |
| Body | `text-body` / prose 17–18px | readable serif (Literata) for long form |
| Caption / micro | `text-caption` / `text-micro` | 14px / 12px — never for primary info |

UI uses Inter Variable; code uses JetBrains Mono Variable. All are self-hosted and the Inter
subset is preloaded in the layout.

## Spacing, radius, elevation

- 8px grid: 4 / 8 / 12 / 16 / 24 / 32 / 40 / 48 / 64 / 80 / 96. No arbitrary `p-[Npx]`, `m-[Npx]`,
  or `text-[Npx]` (the verifier forbids arbitrary text sizes).
- Radius: 8 / 12 / 16 / 20 / 24. `rounded-3xl` is forbidden.
- Elevation: `--obs-shadow-sm` (cards), `--obs-shadow` (raised), `--obs-shadow-lg` (popovers),
  `--obs-shadow-xl` (modals). Use tokens, not ad-hoc `shadow-[...]`.

## Components

Reuse existing classes before adding new ones: `.surface-card`, `.btn*` (hover/press/loading/
disabled), `.icon-btn`, `.badge*`, `.field*` (focus/error/success), `.shimmer`, `.skeleton`,
`.table-sort-btn`, `.app-status` (toast). Every interactive element needs hover, focus-visible,
press, and disabled states.

## Motion

- 120–180ms micro, 180–240ms component, 240–320ms page, ease `cubic-bezier(0.16, 1, 0.3, 1)`.
- All motion must respect `prefers-reduced-motion` (globally enforced).
- No perpetual animated paint (e.g. `animate-ping`) in performance-critical areas.
- Non-critical DOM enhancements are scheduled in idle slices (`requestIdleCallback`) so no single
  long task blocks the main thread on content-heavy reading pages.

## Accessibility

- WCAG AA floor, AAA where practical. Contrast pairs are verified in both themes.
- Skip link, focus trap for dialogs, ARIA live status, semantic headings in order (no skipped levels
  in card grids), labelled inputs, ≥44px touch targets on mobile.
- Mobile document context uses an inline disclosure (not a modal): `aria-expanded` stays synchronized,
  Escape closes it and restores focus to the toggle.
- Never remove `lang`, viewport meta, skip link, or ARIA control targets.

## Performance rules

- `output: 'static'`; keep React islands minimal and lazy (`client:media`, `client:idle`).
- `content-visibility: auto` is allowed only where the intrinsic size is predictable. The first
  home section, `#paths`, and `#domains` are exempt because placeholder geometry caused CLS and
  overlapping touch targets.
- Keep long-form pages free of synchronous reveal/attribute writes (see `setupReveals` guard).

## Quality gates

```bash
npm run build
npm run verify:ui          # contrast + static a11y + source patterns
npm run verify             # links and anchors (48k+ links)
npm run verify:mermaid     # 349 diagrams
npm run verify:responsive  # 11 routes x 6 viewports, axe, focus traps
npm run verify:lighthouse  # perf/a11y/BP/SEO; run with Chrome processes idle
```

On Windows, the Lighthouse gate resolves the CLI through `node` and discovers
`chrome-win64/chrome.exe` automatically. Lighthouse scores on a loaded host are noisy; run it with
other Chrome processes closed and treat the deterministic gates above as authoritative.
