# Unified Admin Layout Preview

Development route: `/__preview/admin-login`, guarded by `NODE_ENV=development` and `REACT_APP_ENABLE_ADMIN_AUTH_PREVIEW=true`. Production authentication, Admin routes, stores and DefaultNavbar are unchanged.

## Navigation and integration

`AdminNavigation.config.ts` is the integration mapping for the eight business functions. No project/member/task-tracker views remain. The first five functions are placeholders because matching business components/routes were not found in this branch. Connect merged components in `AdminContentRouter.tsx`; do not implement their forms in the layout. All production paths in the map are proposals except the existing `/admin/mining-runs`. This file registers no production routes.

The preview uses a URL query selection, e.g. `/__preview/admin-login?section=mining-monitoring`. Selection uses React Router history; the sidebar remains mounted, so collapse state survives navigation and Back/Forward. F5 retains the URL selection but clears the intentionally ephemeral preview session; log in again to resume the selected function. Collapse resets on a full reload/logout, with no storage writes. Unknown sections fall back to data preparation. Future production nesting/Outlet can mount the same content slots after route/auth contracts are agreed.

The mobile menu has an explicit open/close control and closes on selection. Desktop uses the left sidebar; collapsed entries retain accessible names, icons and native title tooltips. Logout stays at the bottom and resets only local auth state.

## Reused TV3 screens

`AdminContentRouter` lazy-loads the existing Monitoring, History and Comparison preview components with `embedded=true`. This strips only the outer standalone shell. The same scenario controls, fixture adapters and content components are used; calculations, RUN contracts and fixture values are unchanged. The history view keeps its isolated QueryClient and fixture source, never its production API adapter. Monitoring's history link points to the in-layout history section when embedded. Standalone previews retain their original shells, flags and destinations.

The Admin preview flag authorizes these embedded fixture screens; their separate flags still control only their standalone URLs. No iframe, second Admin layout, production session, polling, HTTP, SSE or WebSocket is used. The existing Electro Navbar depends on legacy routing/auth stores, so it is not mounted or modified by this preview.

## Authentication boundary

Legacy `AdminSignin` calls the Electro `/api/auth/login` and `/api/auth/info` URLs at port 8085 and writes JWT/user into the production Zustand store. Existing `Admin.tsx` checks user presence; `ProtectedRoute` uses the separate customer store. No corresponding auth controller was found in the backend paths inspected. None of these mechanisms is changed here.

`AdminLoginAdapter` is an application seam, not a confirmed HTTP/session contract. Username is trimmed; password is sent unchanged to the selected synthetic adapter and cleared from form state on submit. It is never persisted/logged. Role states and identities are explicitly synthetic. Non-admin outcomes are denied. Logout and aborted submissions affect only local state.

Before real integration, confirm endpoints, role/permission claims, session/token transport, expiry, logout/revocation and safe 401/403 handling. Backend authorization must secure every Admin API; client guards provide UX only. Never use real credentials in this preview.
