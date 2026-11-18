# PREPGURU_UI_TRANSFORMATION.md

## Role

Act simultaneously as a:

- Principal Product Designer
- Staff Frontend Engineer
- Design Systems Architect
- UX Accessibility Specialist
- Performance Engineer

Transform the existing PrepGuru application into a premium, production-grade **Java Knowledge Operating System**.

The final experience should combine:

- Obsidian's knowledge exploration and document-tree experience
- Blackbox.ai's technical, editorial, evidence-driven presentation
- Linear's interaction precision
- Raycast's keyboard-first navigation
- Vercel's restraint and production polish

Do not create a generic SaaS landing page.

Do not create an AI-generated-looking interface.

Do not create an animation-heavy portfolio.

The product must feel like a serious engineering tool built for developers, architects, interview candidates, and enterprise Java teams.

---

# 1. Prime Directive

Upgrade the entire UI and UX without changing the application's:

- Existing content
- Business logic
- Features
- Routes
- APIs
- Data structures
- Search behavior
- Knowledge graph behavior
- Learning paths
- Authentication
- Database integrations
- Existing user workflows

Layout and component composition may be improved when necessary, but existing functionality and behavior must remain intact.

Before writing code:

1. Inspect the complete repository.
2. Identify the framework, routing, styling, state management, and component architecture.
3. Run the existing application.
4. Review every route at desktop, tablet, and mobile sizes.
5. Create an inventory of reusable components.
6. Identify visual inconsistencies, accessibility failures, overflow issues, duplicated patterns, and performance bottlenecks.
7. Implement the redesign systematically rather than applying isolated cosmetic changes.

Do not blindly rewrite working code.

---

# 2. Desired Product Identity

PrepGuru should no longer feel like:

- A documentation template
- A course marketplace
- A generic Tailwind dashboard
- A purple-gradient SaaS landing page
- A collection of repeated rounded cards
- An AI-generated frontend

PrepGuru should feel like:

> A living Java engineering knowledge system where documents, source code, system-design concepts, interview material, learning paths, backlinks, and architecture diagrams are deeply connected.

The visual experience should communicate:

- Technical credibility
- Knowledge density
- Precision
- Trust
- Speed
- Searchability
- Connected information
- Enterprise readiness

---

# 3. Visual Direction

Use the following influence ratio:

- 45% Obsidian
- 35% Blackbox.ai
- 15% Linear
- 5% Raycast

## Extract principles, not branded assets

Do not directly copy:

- Logos
- Proprietary text
- Illustrations
- Exact page composition
- Source code
- Trademarked visual assets
- Exact animations

Use the references only for design principles:

### From Obsidian

- Knowledge explorer
- Nested document tree
- Backlinks
- Related concepts
- Graph navigation
- Workspace-style layout
- Resizable panels
- Contextual navigation
- Keyboard-first workflows

### From Blackbox.ai

- Monochrome technical presentation
- Oversized editorial typography
- Terminal-style product demonstrations
- Strong bordered grids
- Dense but controlled information
- Operational status indicators
- Numbered product sections
- Product evidence instead of decorative illustrations
- Restrained use of color
- Direct technical copy

### From Linear

- Precise spacing
- Fast transitions
- Clear active states
- Premium command interactions
- Consistent component behavior

### From Raycast

- Command palette
- Keyboard navigation
- Search result grouping
- Fast contextual actions

---

# 4. Core Design Principles

Every design decision must satisfy these principles:

1. Content remains the primary visual element.
2. Product capability must be demonstrated, not merely described.
3. Technical information should look credible and operational.
4. Color must communicate state or hierarchy, not act as decoration.
5. Typography must create hierarchy before shadows and gradients.
6. Borders and surface changes should create structure.
7. Motion must explain state changes.
8. Keyboard navigation must be a first-class experience.
9. Density must remain readable.
10. Mobile must receive an intentional interface, not a compressed desktop layout.

---

# 5. Color System

Replace the current purple-heavy decorative system with a restrained neutral palette.

## Light Theme

```css
:root {
  --background: #f4f3ef;
  --surface-0: #f8f7f3;
  --surface-1: #fbfaf7;
  --surface-2: #ffffff;
  --surface-hover: #f0eee8;

  --text-primary: #111111;
  --text-secondary: #4f4f4f;
  --text-muted: #747474;
  --text-disabled: #a3a3a3;

  --border-subtle: #e5e3dc;
  --border-default: #d6d3ca;
  --border-strong: #aaa69b;

  --accent: #6d3df5;
  --accent-hover: #5b2ce0;
  --accent-subtle: rgba(109, 61, 245, 0.08);

  --success: #087f5b;
  --success-subtle: rgba(8, 127, 91, 0.09);
  --warning: #b35309;
  --warning-subtle: rgba(179, 83, 9, 0.09);
  --danger: #c92a2a;
  --danger-subtle: rgba(201, 42, 42, 0.09);
  --info: #2563eb;
}