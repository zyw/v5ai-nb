# v5ai-nb Promotional Website Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a standalone responsive one-page promotional website for v5ai-nb in `v5ai-website`, presenting its self-hosted Agent runtime, model/RAG/MCP/Skill capabilities, workflow direction, and GitHub entry point.

**Architecture:** Create an isolated Vite + vanilla HTML/CSS/JS microsite under `v5ai-website`. Keep the page content and visual primitives in `index.html`, the design tokens/layout/motion in `src/style.css`, and small interaction behavior in `src/main.js`; do not alter the existing admin or chat frontends.

**Tech Stack:** Vite 7, vanilla HTML/CSS/JavaScript, Google Fonts with system fallbacks, inline SVG icons, CSS media queries and `prefers-reduced-motion`.

**Spec:** Confirmed in chat on 2026-09-23: “运行时控制台” visual direction, dark ink/navy palette with violet/cyan accents, fixed top navigation, hero runtime console, capability matrix, runtime flow, security/observability section, GitHub CTA, responsive mobile navigation.

## Global Constraints

- The site lives only in `v5ai-website` and must not change the behavior of `v5ai-ui` or `v5ai-ui-chat`.
- Use the project’s actual differentiators: published snapshots, streaming events, RAG, MCP, Skill, quota/audit, and Workflow.
- The navigation’s rightmost action is a GitHub link with an explicit accessible label.
- Avoid copying the reference site’s copy, illustrations, or exact composition.
- Use semantic headings, visible keyboard focus, touch-sized controls, and reduced-motion support.
- Keep body text readable on mobile and prevent horizontal overflow at 375px.

## Review Focus

- At 375px wide, the nav collapses without horizontal overflow and the primary CTAs remain reachable; cover in Task 2 responsive CSS and Task 3 smoke check.
- With `prefers-reduced-motion: reduce`, the page does not run looping ambient or reveal animations; cover in Task 2 motion rules and Task 3 manual verification.
- Keyboard users can reach the mobile menu and GitHub CTA with visible focus; cover in Task 3 accessibility smoke check.
- The GitHub URL is centralized and easy to replace rather than duplicated across unrelated markup; cover in Task 1 content structure.
- Empty or missing JavaScript APIs do not prevent the static page from rendering; cover in Task 3 build verification.

### Task 1: Scaffold the standalone website and content structure

**Files:**
- Create: `v5ai-website/package.json`
- Create: `v5ai-website/index.html`
- Create: `v5ai-website/src/main.js`

**Interfaces:**
- Produces a Vite entry point with semantic sections and data attributes used by Task 2 styling and Task 3 interactions.
- `src/main.js` owns the mobile navigation toggle and the current year text only; the page remains usable if JavaScript fails.

- [ ] **Step 1: Add the Vite package manifest** with `dev`, `build`, and `preview` scripts and no dependencies beyond Vite.
- [ ] **Step 2: Add semantic page markup** for skip link, header/nav, hero runtime console, capabilities, runtime flow, trust/engineering section, final CTA, and footer. Include the GitHub URL in the top nav’s last action and repeat it in the CTA.
- [ ] **Step 3: Add progressive enhancement hooks**: `aria-expanded`, `aria-controls`, a mobile menu button, `data-reveal` attributes, and a current-year placeholder.
- [ ] **Step 4: Add the minimal interaction script** to toggle the mobile menu, close it on link activation/Escape, set `aria-expanded`, and populate the current year.

### Task 2: Implement the distinctive visual system and responsive behavior

**Files:**
- Create: `v5ai-website/src/style.css`

**Interfaces:**
- Consumes the semantic classes and data attributes from `index.html`.
- Produces the complete desktop/mobile visual treatment, including CSS custom properties, console animation, focus states, reduced-motion fallback, and responsive layout.

- [ ] **Step 1: Define design tokens** for ink/navy surfaces, violet/cyan accents, text contrast, borders, spacing, radii, shadows, and typography using Space Grotesk + DM Sans with fallbacks.
- [ ] **Step 2: Style the header and hero** with a restrained grid/noise background, asymmetric two-column layout, clear CTA hierarchy, and the runtime console as the memorable product-specific visual.
- [ ] **Step 3: Style capabilities and runtime flow** using varied but related blocks rather than identical SaaS cards; use inline icon containers, event states, code-like labels, and connector lines that collapse cleanly on mobile.
- [ ] **Step 4: Add responsive breakpoints** for desktop, tablet, and 375px mobile; collapse the nav, stack hero/console, and preserve readable line lengths and touch target sizes.
- [ ] **Step 5: Add motion and accessibility rules** for reveal transitions, event pulses, visible focus rings, `prefers-reduced-motion`, selection colors, and `color-scheme` metadata.

### Task 3: Verify the website and polish any issues

**Files:**
- Modify: `v5ai-website/index.html` only if build or accessibility verification exposes a markup issue.
- Modify: `v5ai-website/src/style.css` only if responsive or focus verification exposes a visual issue.
- Modify: `v5ai-website/src/main.js` only if interaction verification exposes a behavior issue.

**Interfaces:**
- Validates the Vite build artifact and the static page’s responsive/accessibility behavior.

- [ ] **Step 1: Install the standalone site dependencies** with `npm install` inside `v5ai-website`.
- [ ] **Step 2: Run `npm run build`** and confirm Vite emits a production bundle without errors.
- [ ] **Step 3: Run a local preview or static inspection** at 375px and desktop widths, checking no horizontal overflow, mobile menu behavior, CTA links, and GitHub link placement.
- [ ] **Step 4: Review keyboard focus and reduced-motion behavior** and fix any issue found.
- [ ] **Step 5: Report the created files and verification result** with a clickable link to `v5ai-website/index.html`.
