# PLAN.md — Premium UI / UX Upgrade

> **Status:** Planning document (Plan mode). No source files have been changed.
> **Scope:** UI, UX, accessibility, responsiveness, motion, and visual quality only.
> **Method:** Evidence-first. Every item below is tied to a real file, a measured baseline, or an
> explicit acceptance gate in `npm run verify:all`.
>
> **Note:** The harness restricts plan-file writes to `C:\Users\aritr\.opencode\plan`, so the
> workspace copy at `E:\JAVA_MASTERCLASS\Plan.md` could not be edited directly. Copy this file over
> it (or ask me to do so from an agent with write access to the workspace).

---

## Implementation status (updated after implementation)

Completed in the first implementation pass:

- [x] Hero `<h1>` desktop collapse fixed (`sm:text-display-1`) — `index.astro`.
- [x] Article title uses new `title-lg` token (32–40px) — `tailwind.config.mjs`, `ObsidianLayout.astro`.
- [x] CLS 0.23 → 0 via selective `content-visibility` (hero / `#paths` / `#domains` exempt).
- [x] Mobile touch targets ≥44px on home text links; heading order fixed on article/graph/search cards.
- [x] Long-task split: non-critical enhancements run in idle slices; reveals skipped on prose pages.
- [x] Lighthouse accessibility 100 on all 5 gated routes (was 96–98). Best practices and SEO remain 100.
- [x] Contrast verifier now derives tokens from `global.css` (no hardcoded color duplication).
- [x] Windows fixes in verification scripts (Lighthouse CLI via Node, `chrome-win64` discovery).
- [x] New `docs/UI_DESIGN_SYSTEM.md` handoff document.

Gate results: `verify:ui`, `verify`, `verify:mermaid`, `verify:responsive` all pass.
Lighthouse performance improved substantially in clean isolated runs (home 99–100, article 92) but
the batched gate is noisy on this Windows host (TBT swings 110ms → 1150ms between identical runs),
so the performance threshold needs a quiet-machine rerun in CI.

Deferred / not yet done: Phase 4 discoverable shortcut help and System/Light/Dark theme choice;
Phase 5 chart enrichment; full workspace-first home redesign beyond hierarchy fixes.

---

## Vision — what "Obsidian-style, production-ready" means here

**North star:** a calm, fast, keyboard-driven knowledge **workspace** — not a marketing site with
blog pages. It should feel like a polished Obsidian-style app that happens to live in the browser:
a persistent file tree on the left, the document in the center, contextual panels on the right,
a command palette at the fingertips, and a reading-progress/status bar — all in quiet, layered
dark surfaces with a single restrained violet accent.

### A. "Obsidian-style webapp looks" — defined and gap-checked

| Characteristic | Target | Current status |
|---|---|---|
| Three-pane workspace shell | Left file tree / center document / right outline + backlinks | ✅ Exists (`ObsidianLayout.astro`) |
| Command-driven interaction | `⌘K` command center, keyboard shortcuts, focus trap | ✅ Exists; make shortcuts discoverable (D10) |
| Connected-knowledge feel | Backlinks, outgoing links, graph view, wikilinks | ✅ Exists; surface more prominently |
| Quiet, layered surfaces | Thin borders, subtle elevation, no loud gradients in reading contexts | ⚠️ Over-decorated in several cards/banners |
| Compact, deliberate density | UI at 13–14px, prose at 17–18px, clear scanning | ⚠️ Inconsistent; article title only 18px (D2) |
| Focus / zen reading mode | Chrome disappears, measure narrows | ✅ Exists (`Z`) |
| Dual theme, dark-first | Obsidian-dark default + crisp light | ✅ Exists; add explicit System state (D11) |
| **Workspace-first home** | Home reads like an app overview / command deck, not a landing page | ❌ **Gap:** current home is marketing-forward (hero, glow banners, KPI strip) |
| Status / progress feedback | Reading progress + toast channel | ✅ Exists (progress bar, `.app-status`) |

**Consequence:** retain the Obsidian app shell and connected-knowledge features, but reframe the
home page from "marketing landing" toward "workspace cockpit" — a command deck that routes readers
into the library, paths, graph, and recently viewed pages — while keeping the useful curriculum
metrics as quiet, secondary telemetry rather than hero decoration.

### B. "Production ready" — defined

| Dimension | Concrete requirement |
|---|---|
| Performance | Lighthouse ≥95 on every app surface, including mobile; zero CLS regressions |
| Accessibility | WCAG AA floor / AAA where practical; Lighthouse a11y 100; axe 0 serious/critical; full keyboard operation; reduced-motion safe |
| Responsiveness | Flawless 320 / 390 / 768 / 1024 / 1440 / 1920px; no overflow; ≥44px touch targets |
| Completeness | Designed empty, loading, error, success, and not-found states on every surface |
| Reliability | Zero broken links/anchors; Mermaid and math render in both themes; search indexes valid |
| Consistency | One token set, one component vocabulary, no one-off overrides (§3) |
| Maintainability | Token contract documented; verifier derives from source; `docs/UI_DESIGN_SYSTEM.md` exists |
| Preservation | 100% of content, routes, and pipelines unchanged (§2 non-negotiables) |

These are not aspirations — each maps to a gate in §6.

---

## 0. Mission (preserved and sharpened)

Transform the existing application UI into a premium, enterprise-grade, production-ready
experience while preserving **100%** of current business functionality, content, routes, APIs,
data pipelines, and integrations.

Benchmark quality: **Linear, Stripe Dashboard, Vercel, Raycast, Resend, Clerk, Algoguru.**

The difference from the original brief: this site is **already** a mature Astro knowledge base
with a substantial design system. The job is not to "add a design system" but to:

1. **Fix the hierarchy defects** that undercut the existing polish.
2. **Make reading visibility measurable** and close the last contrast gaps.
3. **Consolidate duplicated tokens** so the system cannot drift.
4. **Close the performance and accessibility gaps** that remain (home-mobile 85 → 95; a11y 96/98 → 100).
5. **Elevate the few surfaces that still feel generic** (charts, dense card grids, mobile discovery).

---

## 1. Current State Audit (evidence)

### 1.1 Stack and architecture — do not change

| Concern | Current implementation | Constraint for this plan |
|---|---|---|
| Framework | Astro 5 static output (`astro.config.mjs`) | Keep `output: 'static'`; no SSR, no new runtime |
| UI islands | React 18 via `@astrojs/react` (Header/TOC/Sidebar/Backlinks/CommandPalette/Checklist/Graph) | Refine in place; no framework migration |
| Styling | Tailwind 3 + `@tailwindcss/typography`, CSS variables in `src/styles/global.css`, prose in `src/styles/markdown.css` | Extend tokens, do not replace them |
| Fonts | `@fontsource-variable/inter`, `jetbrains-mono`, `literata` (self-hosted, preloaded) | Already meets "Inter or Geist" + readable serif prose |
| Search | Pagefind + Minisearch command palette | Preserve query UX and index endpoints |
| Diagrams / math | Mermaid 12 (lazy, IntersectionObserver), KaTeX | Preserve lazy rendering and theme re-render |
| Quality tooling | `scripts/verify-{ui-quality,responsive,lighthouse,links,mermaid}.mjs` chained in `verify:all` | **This is the gate.** Strengthen, never bypass |

### 1.2 What already exists (explicitly do NOT rebuild)

- Full `obs-*` token system: dark + light palettes, semantic success/warning/error/info,
  domain accents, glass tokens, layered shadows (`global.css:1–287`).
- Type scale (`display-1/2`, `h1–h4`, `body-lg`, `body`, `caption`, `micro`) in `tailwind.config.mjs:142–183`.
- 8px radius scale (8/12/16/20/24) and spacing extensions (`tailwind.config.mjs:188–210`).
- Component primitives: `.surface-card`, `.btn*` (hover/active/loading/disabled), `.icon-btn`,
  `.badge*`, `.field*` (focus/error/success), `.shimmer`, `.skeleton`, `.table-sort-btn`,
  sticky table headers, `.app-status` toast (`global.css:466–997`).
- Strong a11y base: skip link, focus trap (`src/lib/focusTrap.ts`), ARIA live status,
  `prefers-reduced-motion`, semantic prose styles, sr-only data tables for charts.
- Full navigation: sidebar tree, mobile drawer, breadcrumbs, TOC, backlinks, prev/next,
  command palette (`⌘K`), reading mode (`Z`), width mode (`W`), sidebar toggle (`[`/`Ctrl+B`).
- Non-generic empty/error state: `src/pages/404.astro` with search, CTAs, and popular chapters.

### 1.3 Measured quality baseline (`.lighthouse/*.json`)

| Route | Mobile Perf | Mobile A11y | Desktop Perf | Desktop A11y | BP | SEO |
|---|---|---|---|---|---|---|
| `/` | **85** ⚠️ | **96** ⚠️ | 99 | 100 | 100 | 100 |
| `/library` | 99 | 100 | 100 | 100 | 100 | 100 |
| `/checklist` | 98 | 100 | 100 | 100 | 100 | 100 |
| `/graph` | 96 | **98** ⚠️ | 100 | **98** ⚠️ | 100 | 100 |
| `/core-java/.../01-oop-foundations...` | 91 | **98** ⚠️ | 99 | **98** ⚠️ | 100 | 100 |

**Gaps to close:** home-mobile performance (85 < the script's 95 app threshold), and every
accessibility score below 100. Best-practices and SEO are already 100 everywhere — protect them.

### 1.4 Concrete defects and improvement targets found in source

| # | Finding | Location | Priority |
|---|---|---|---|
| D1 | Hero `<h1>` collapses to **18px on desktop** via `lg:text-lg` (overrides `text-4xl`/`sm:text-5xl`) | `src/pages/index.astro:174` | **P0** |
| D2 | Article title `<h1>` is **18px** (`text-lg … sm:text-lg`) while prose `h1` is `clamp(3rem,5vw,4rem)` → inverted hierarchy; conflicts with the brief's H1 scale | `src/layouts/ObsidianLayout.astro:375` vs `src/styles/markdown.css:63–72` | **P0** |
| D3 | Color values are **duplicated** in `scripts/verify-ui-quality.mjs:47–138` instead of derived from `global.css` → verifier can pass while tokens drift | `scripts/verify-ui-quality.mjs` | P1 |
| D4 | Framer Motion requested in brief but **not installed**; motion is CSS keyframes (cheaper, already passes reduced-motion). Decision needed. | `package.json`, `global.css:899–921` | P1 (decision) |
| D5 | Charts are CSS bars only; no legends/interaction. Brief asks for Linear/Vercel/Stripe quality — but a chart library risks the already-fragile home-mobile score | `src/components/CurriculumHealthChart.astro` | P1 |
| D6 | Home-mobile 85: heavy blurred gradients, `backdrop-blur`, `animate-ping`, multiple islands above the fold | `index.astro:151–292`, `ObsidianLayout.astro` | **P0** |
| D7 | Accessibility 96/98: likely small-text contrast and tooltip/label issues; verifier only checks *serious/critical*, Lighthouse counts more | all routes | P1 |
| D8 | Dense micro-copy (`text-micro` 12px, muted) across cards; "zero low-contrast text" needs an explicit small-text audit in **both** themes | `index.astro`, `ObsidianLayout.astro`, `global.css` | P1 |
| D9 | Header quick links (Library/Paths/Graph/Checklist) hidden below `lg`/`xl`/`2xl`; mobile discovery relies entirely on the drawer | `src/components/Header.astro:161–216` | P2 |
| D10 | Keyboard shortcuts (`[`, `Ctrl+B`, `W`, `Z`, `⌘K`) exist but are **undiscoverable** — no in-app shortcut help | `ObsidianLayout.astro:1221+` | P2 |
| D11 | Theme toggle is binary (dark/light) although initial load already follows `prefers-color-scheme`; no explicit "System" state | `ObsidianLayout.astro:671–690`, `Header.astro:249–259` | P2 |
| D12 | Search empty state exists but is generic "no results"; brief wants guidance + CTA | `src/pages/search.astro:119, 265` | P2 |

---

## 2. Guiding principles and non-negotiables

### Principles
1. **Readability before decoration.** The site's core job is long-form reading — aim to
   WCAG AA as floor, AAA where practical, in both themes and at 320px.
2. **One source of truth for tokens.** CSS variables in `global.css` are canonical; Tailwind,
   verifiers, and docs consume them.
3. **Motion that feels expensive, not flashy.** Subtle, intentional, reduced-motion-safe.
   Prefer CSS/keyframes over a new animation dependency unless a specific interaction needs it.
4. **Never trade a Lighthouse point for a flourish.** Home-mobile is the binding constraint.
5. **Verify everything.** No phase is "done" until its gate command passes.

### Non-negotiables (hard stop — do not touch)
- Content files and the content pipeline: `src/lib/content.ts`, `markdownRenderer.ts`,
  `searchIndex.ts`, `graphBuilder.ts`, `linkResolver.ts`, `types.ts`.
- Routes and page structure: `src/pages/**` route names, `[...slug].astro`, and all generated
  JSON endpoints (`navigation.json.ts`, `search-index.json.ts`, `graph-data.json.ts`).
- Sitemap filter and SEO metadata in `astro.config.mjs` and `ObsidianLayout.astro` head/JSON-LD.
- Mermaid lazy-render logic, KaTeX rendering, Pagefind index contract.
- Any change to business logic, APIs, database/schema (N/A here), workflows, or feature removal.

> Allowed change surface: `src/styles/**`, `tailwind.config.mjs`, presentational parts of
> `src/components/**`, `src/layouts/ObsidianLayout.astro`, `src/pages/index.astro` markup/classNames,
> `scripts/verify-*`, and documentation.

---

## 3. Target design system (consolidate, do not reinvent)

### 3.1 Token governance
- Keep `global.css` as the single source of truth. Add a short **token contract** comment block
  documenting: name, purpose, dark value, light value, and required contrast pairing.
- Refactor `scripts/verify-ui-quality.mjs` to **parse the CSS variables from `global.css`** at
  runtime (regex over `:root` and `[data-theme='light']`) instead of hardcoding hex values (D3).
  This makes the contrast gate impossible to drift from the design.
- Add assertions that every new token has both dark and light definitions.

### 3.2 Typography
- Keep Inter Variable (UI) + Literata Variable (long-form prose) + JetBrains Mono (code).
- Fix the hierarchy contract explicitly (D1, D2):
  - **Display / hero H1:** 48–64px (`display-1`/`h1` tokens) for landing hero, reserved for `index.astro`.
  - **Article title:** introduce a distinct `title-lg` token (≈32–40px, tight tracking) for the
    document header card. Rationale: a 64px article title would break the compact header card and
    hurt scanning; the brief's "H1 48–64px" is applied to hero contexts, and article titles get a
    deliberate, documented scale instead of an accidental 18px.
  - Prose `h2` 30–36px, `h3` 22–26px, body 17–18px / line-height 1.75–1.82, caption 14px,
    micro 12px **only with sufficient contrast and never for primary information**.
- Add `text-balance`/`text-pretty` consistently to all headings; enforce a max measure
  (`maxWidth.reading` ≈ 46–52rem) for prose.

### 3.3 Color and contrast
- Make `verify-ui-quality.mjs` token-driven (3.1) and expand it to cover:
  - `text-micro` on `canvas`, `canvas-soft`, `surface`, `hover` in **both** themes (D8).
  - Link (`accent`) on all surfaces; semantic `*-text` tokens on their `*-subtle` backgrounds.
  - Mermaid node-label contrast in both themes.
- Target ≥4.5:1 for all text, ≥3:1 for large text/UI borders, ≥7:1 where practical for body copy.
- Any token failing the gate is corrected at the token layer, never with one-off overrides.

### 3.4 Spacing, radius, elevation
- 8px grid already present — enforce it: add a verifier rule flagging arbitrary spacing utilities
  (`p-[Npx]`, `m-[Npx]`) and undocumented spacing values in source.
- Radius scale locked to 8/12/16/20/24 (already in config); keep `rounded-3xl` forbidden.
- Elevation: use the existing 4 shadow tiers; define when to use each (card / hover / popover /
  modal). Remove ad-hoc `shadow-[...]` except the documented brand-glow usages.

### 3.5 Motion tokens
- Standardize durations: micro 120–180ms, component 180–240ms, page 240–320ms, ease
  `cubic-bezier(0.16, 1, 0.3, 1)` (already used). Spring only for button press feedback if added.
- All motion must degrade under `prefers-reduced-motion` (already globally enforced) — keep it that way.

---

## 4. Work streams (phased, each with an acceptance gate)

### Phase 0 — Baseline and guardrails
**Goal:** Lock the current state so improvements are provable.
- Run `npm run build && npm run verify:all`; archive the resulting `.lighthouse/*.json` as `baseline/`.
- Confirm Chromium path / `verify:*` scripts run locally; document one-command verification in `README.md`.
- Record current bundle sizes (`dist/**/*.{js,css}`) and the count of hydration islands.
- **Gate:** baseline report committed; no source changes yet.

### Phase 1 — Reader visibility and typography (CRITICAL)
**Goal:** Every reader-facing surface is legible, hierarchical, and WCAG-compliant.
- Fix D2: replace the 18px article title with the new `title-lg` scale; align breadcrumb/eyebrow/meta.
- Fix D1: restore the hero H1 to the display scale at all breakpoints.
- Audit D8: small-text contrast in both themes; adjust muted tokens or promote small text to caption.
- Tighten prose: consistent max measure, heading rhythm, callout legibility, table/code legibility,
  link distinction; verify Mermaid label contrast in both themes.
- **Gate:** `npm run verify:ui` passes; Lighthouse a11y ≥ 98 on all routes; visual diff reviewed at
  390px / 768px / 1440px in both themes.

### Phase 2 — High-traffic screen hierarchy
**Goal:** Home, article header, and catalog pages look deliberately composed.
- **Workspace-first home:** reframe `index.astro` from marketing landing to an app command deck —
  primary actions (resume reading, recent/pinned pages, jump to library/paths/graph) take the lead;
  curriculum metrics become quiet secondary telemetry; reduce glow banners and oversized decoration.
- Re-compose the home KPI strip with the corrected scale and clearer visual weight.
- Re-balance `ObsidianLayout.astro` doc header (domain pill → title → summary → meta → tags).
- Apply the same card/heading pattern to `library.astro`, `paths.astro`, `glossary.astro`,
  `index-all.astro`, `checklist.astro`.
- **Gate:** `npm run verify:ui && npm run verify:responsive` pass; no horizontal overflow at any
  of the 6 configured viewports.

### Phase 3 — Components and states
**Goal:** Every interactive element has deliberate hover/focus/press/loading/disabled states.
- Unify `.btn*`, `.icon-btn`, `.badge*`, `.field*`, `.surface-card` usage; remove one-off classes.
- Add explicit empty states (D12) for search and any filtered list: icon, guidance, and CTA.
- Elevate success/error/confirmation states; the `.app-status` toast becomes the single feedback channel.
- Ensure all focus states meet the global `:focus-visible` ring; no keyboard traps outside dialogs.
- **Gate:** `npm run verify:ui` + axe serious/critical = 0 across routes; keyboard-only smoke pass.

### Phase 4 — Navigation and discovery
**Goal:** Users find content faster on desktop and mobile.
- Improve active-state clarity in sidebar/TOC/breadcrumbs (current page unmistakable).
- Address D9: add a compact overflow/quick-nav for mobile so top-level tools are reachable without
  opening the drawer.
- Address D10: add a discoverable shortcut help affordance (e.g. `?` opens a shortcut sheet) listing
  the existing keyboard shortcuts — **documentation only, no new bindings required**.
- Address D11: add an explicit "System / Light / Dark" choice reusing the existing inline theme logic.
- **Gate:** focus-trap tests in `verify:responsive` still pass; command palette and drawer unaffected.

### Phase 5 — Data visualization / dashboard quality
**Goal:** `CurriculumHealthChart` reads like Linear/Vercel analytics without a heavy dependency.
- Improve legends, labels, spacing, and responsive behavior with lightweight semantic HTML/SVG/CSS
  (keep the sr-only data table). Add subtle hover emphasis and clear "current average" annotation.
- **Decision D5:** do **not** add a charting library unless the lightweight approach fails review —
  home-mobile performance is already the binding constraint.
- **Gate:** Lighthouse home-mobile performance ≥ 95; chart remains fully accessible.

### Phase 6 — Motion and microinteractions
**Goal:** Motion feels premium and intentional, never gimmicky.
- Decide D4: **recommended = stay CSS-based**, add Framer Motion only if a specific island needs
  gesture/spring behavior; if added, lazy-load it in that island only and re-measure the bundle.
- Apply staggered reveal to card grids, subtle hover elevations, spring press feedback on primary CTAs.
- Remove/replace any remaining `animate-ping`-style perpetual motion that hurts mobile paint (D6).
- **Gate:** `prefers-reduced-motion` disables all new motion; no CLS regression (verify-lighthouse).

### Phase 7 — Mobile parity
**Goal:** Mobile is equal to desktop, not a compromise.
- 320 / 390 / 768 / 1024 / 1440 / 1920: revisit touch targets (≥44px), spacing, sticky headers,
  table horizontal scroll, drawer ergonomics, and reading width.
- Reduce above-the-fold blur/backdrop cost on small screens (D6) — cap blur radius, avoid layered
  `backdrop-blur` where a solid translucent surface reads the same.
- **Gate:** `npm run verify:responsive` green; visually review screenshots at 320px and 390px.

### Phase 8 — Performance and accessibility hardening
**Goal:** Close the measured gaps, not just the source-pattern gates.
- Home-mobile 85 → ≥95: defer non-critical islands, reduce paint area of blurred gradients,
  audit LCP element, remove render-blocking work, keep content-visibility optimizations.
- Raise all a11y scores to 100: resolve every Lighthouse a11y audit item (names, contrast, roles),
  in addition to the existing axe serious/critical check.
- Protect 100s in Best Practices and SEO.
- **Gate:** `npm run verify:lighthouse` green with **app thresholds raised to 100 for accessibility**
  once achieved; article thresholds unchanged.

### Phase 9 — Documentation and handoff
**Goal:** The system is maintainable by the next contributor.
- Add a short `docs/UI_DESIGN_SYSTEM.md`: token contract, component usage, motion rules,
  accessibility checklist, and the verification workflow.
- Add a "quality gates" section to `README.md` with `npm run verify:all` and how to interpret reports.
- Final `verify:all` run; attach final Lighthouse table to this plan.

---

## 5. Decisions to confirm (with recommendations)

| # | Decision | Recommendation | Why |
|---|---|---|---|
| Q1 | Apply "H1 = 48–64px" literally to article titles? | **No.** Use display scale for hero H1, introduce `title-lg` (32–40px) for article headers. | Literal 64px article titles break the compact header and hurt scanning; a documented second scale is more deliberate. |
| Q2 | Add Framer Motion? | **Not by default.** Keep CSS motion; add only for a specific island if needed. | Bundle cost vs. fragile home-mobile score; existing CSS motion already meets the brief. |
| Q3 | Add a charting library? | **No.** Enhance the existing semantic CSS/SVG chart. | Same performance reasoning; accessibility is already strong. |
| Q4 | Scope of "premium" — all pages or high-traffic subset? | **All pages for tokens/components; deep redesign for home, article header, library, search, 404.** | Consistent system everywhere, effort concentrated where users land. |
| Q5 | Theme options | **Add explicit System / Light / Dark.** | Initial load already respects system preference; making it visible is a small, high-value UX win. |

---

## 6. Verification and Definition of Done

### 6.1 Quality gates (all must pass)

```bash
npm run build
npm run verify:all
# = verify:mermaid && build && verify-links && verify:ui && verify:responsive && verify:lighthouse
```

| Gate | Current | Target |
|---|---|---|
| WCAG contrast pairs (both themes) | Pass | Pass via **CSS-derived** tokens |
| Forbidden source patterns | Pass | Pass; add arbitrary-spacing rule |
| axe serious/critical across 11 route/viewport combos | Pass | Pass |
| Responsive overflow at 6 viewports | Pass | Pass |
| Lighthouse app surfaces (Perf/A11y/BP/SEO) | Perf 85–99, A11y 96–100 | **≥95 / 100 / 100 / 100** |
| Lighthouse long-form article | 91 / 98 / 100 / 100 | ≥85 / 100 / 100 / 100 |
| Routes / links / sitemap | Pass | **Unchanged** |

### 6.2 Definition of Done (matches the brief, made measurable)

- ✅ UI feels enterprise-grade **and Obsidian-native** — three-pane workspace shell, command palette,
  backlinks/graph, quiet layered surfaces, focused reading mode; reviewed at 390/768/1440px in both themes.
- ✅ Home reads as a workspace command deck, not a generic marketing landing page (Vision §A).
- ✅ No template-generated feel — one token set, one component vocabulary, no one-off overrides.
- ✅ Visual hierarchy significantly improved — D1 and D2 fixed and covered by a verifier rule
  (assert no `text-lg` on a page-level `<h1>`; assert article title uses `title-lg`).
- ✅ All screens follow one cohesive design system — token-driven verifier + design-system doc.
- ✅ Accessibility met — Lighthouse a11y 100 on all routes, axe 0 serious/critical.
- ✅ Mobile polished — `verify:responsive` green at 320–1920px, no overflow, ≥44px touch targets.
- ✅ Performance excellent — home-mobile ≥95, all other surfaces ≥95.
- ✅ Premium, intentional motion — reduced-motion safe, no perpetual animated paint on mobile.
- ✅ Benchmark parity — final Lighthouse table attached; side-by-side visual review completed.

---

## 7. Risk register

| Risk | Mitigation |
|---|---|
| Home-mobile performance regresses while adding polish | Phase 8 gate is binding; measure after every visual change; keep blur/backdrop budget capped |
| Token verifier refactor breaks the gate | Implement D3 in Phase 0 with a diff against current results; both must agree |
| "Premium" scope creep into business logic | Allowed-change surface in §2; any content/route change is out of scope |
| New dependency bloats bundle | D4/D5 decisions default to "no new library"; any addition requires a Lighthouse re-run |
| Dark/light drift | Token contract + runtime derivation in verifier |

---

## Appendix A — Primary file inventory

```
astro.config.mjs                         sitemap + integrations (do not change)
tailwind.config.mjs                      type scale, radius, shadows, keyframes
src/styles/global.css                    CANONICAL tokens + component primitives
src/styles/markdown.css                  reading/prose styles (visibility critical)
src/layouts/ObsidianLayout.astro         shell, doc header (D2), theme/motion logic
src/components/Header.astro              nav, quick links (D9), theme toggle (D11)
src/components/CurriculumHealthChart.astro  chart upgrade (D5)
src/pages/index.astro                    hero H1 (D1), KPI strip, home-mobile perf (D6)
src/pages/search.astro                   empty state (D12)
src/pages/404.astro                      already-strong error state — preserve
scripts/verify-ui-quality.mjs            token derivation (D3) + contrast expansion (D8)
scripts/verify-responsive.mjs            overflow, axe, focus-trap gates
scripts/verify-lighthouse.mjs            performance/accessibility gates
docs/UI_DESIGN_SYSTEM.md                 NEW — handoff documentation
```
