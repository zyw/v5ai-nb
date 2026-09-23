# v5ai UI Router Admin Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build the Phase 1 Vue frontend around `vue-router`, consuming the existing authentication, Provider/Model, Agent publish, and chat runtime APIs.

**Architecture:** Keep the frontend as a Vue 3 + Naive UI Vite app. `App.vue` becomes a shell with a routed content area; route pages own their forms/tables and call a typed API client. Admin JWT is stored in a tiny reactive session module backed by `localStorage`.

**Tech Stack:** Vue 3, vue-router 4, Naive UI, TypeScript, Vite.

## Global Constraints

- Project name remains `v5ai-nb`.
- Frontend routing must use `vue-router`.
- Do not change backend APIs for this slice.
- Consume existing endpoints under `/api`.
- Agent creation currently requires numeric `modelId`; the current model list response does not expose `id`, so the UI will accept `modelId` manually until the backend response is extended.

---

### Task 1: Router and Shell

**Files:**
- Modify: `v5ai-ui/package.json`
- Modify: `v5ai-ui/src/main.ts`
- Modify: `v5ai-ui/src/App.vue`
- Create: `v5ai-ui/src/router/index.ts`

**Interfaces:**
- Produces route names: `dashboard`, `models`, `applications`, `chat`, `settings`.

- [ ] Add `vue-router` dependency.
- [ ] Register router in `main.ts`.
- [ ] Replace internal `active` state in `App.vue` with router-backed navigation.
- [ ] Render route content through `<router-view />`.
- [ ] Verify TypeScript build catches route/component import errors.

### Task 2: API Client and Session

**Files:**
- Modify: `v5ai-ui/src/api/client.ts`
- Create: `v5ai-ui/src/stores/session.ts`

**Interfaces:**
- Produces typed functions: `login`, `listProviders`, `createProvider`, `listModels`, `createModel`, `listApplications`, `createApplication`, `publishApplication`, `chat`, `streamChat`.
- Produces reactive values: `adminToken`, `isLoggedIn`, `setAdminToken`, `clearAdminToken`.

- [ ] Add typed request/response models matching backend DTOs.
- [ ] Normalize admin and application API authorization headers.
- [ ] Parse non-2xx backend responses into useful `Error` messages.
- [ ] Keep SSE parsing for `event:` and `data:` lines.

### Task 3: Admin Views

**Files:**
- Create: `v5ai-ui/src/components/AdminLoginCard.vue`
- Create: `v5ai-ui/src/views/DashboardView.vue`
- Create: `v5ai-ui/src/views/ModelsView.vue`
- Create: `v5ai-ui/src/views/ApplicationsView.vue`
- Create: `v5ai-ui/src/views/SettingsView.vue`

**Interfaces:**
- Consumes API client and session module from Task 2.

- [ ] Provide admin login and token persistence.
- [ ] Build Provider create/list UI.
- [ ] Build Model create/list UI with credentials JSON textarea.
- [ ] Build Agent create/list/publish UI.
- [ ] Document Phase 1 runtime assumptions in Settings.

### Task 4: Chat Debug View

**Files:**
- Create: `v5ai-ui/src/views/ChatDebugView.vue`
- Modify: `v5ai-ui/src/styles/base.css`

**Interfaces:**
- Consumes `chat` and `streamChat` from Task 2.

- [ ] Add blocking chat call for `/api/v1/agents/{agentKey}/chat`.
- [ ] Add streaming chat call for `/api/v1/agents/{agentKey}/chat/stream`.
- [ ] Render accumulated answer and runtime events.
- [ ] Add layout CSS for cards, tables, forms, and chat output.

### Task 5: Verification

**Files:**
- No source files required.

- [ ] Run `npm run build` in `v5ai-ui`.
- [ ] If build fails, fix compile/type errors and rerun.
- [ ] Report fresh build result and any intentional frontend/backend API gaps.
