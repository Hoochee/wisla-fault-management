## 1. frontend/ — Vitest (red)

- [x] 1.1 Add `frontend/tests/unit/health-product-weight-modal.test.ts`: TestBed for `HealthProductPageComponent` with mocked `AuthService` (`isAdmin`, `currentUser` truthy so `ngOnInit` loads the card) / `FmApiService` (`getProduct`, `getProductHistory`, `getProductAdmin`, `patchProduct`) / `ActivatedRoute` `productId`; stub Sankey/heatmap/sidebar/operative children; keep live `ComponentWeightEditorComponent`. Covers **Inline weight editor is hidden** and **Non-admin has no weight editor**: admin sees «Веса компонентов»; specialist (`isAdmin=false`) does not; with modal closed `app-component-weight-editor` is not in the DOM; components table still shows «Вес» — red
- [x] 1.2 Same spec, **Admin opens modal and saves weights**: admin click opens overlay and shows `app-component-weight-editor`; save calls `patchProduct` with `components` and closes the modal (editor leaves the DOM) — red
- [x] 1.3 Same spec, **Admin cancels without saving**: change a draft weight then Cancel / overlay close does not call `patchProduct`; reopen shows last persisted weights — red

## 2. frontend/ — modal UX (green)

- [x] 2.1 Remove always-visible `<app-component-weight-editor>` from the product card; add admin-only «Веса компонентов» in `.top-actions` (or components-card header) that sets `weightModalOpen`
- [x] 2.2 Host the existing editor in the page overlay/modal (`@if (weightModalOpen())`, reuse `.overlay` / `.modal`, wider for the table); `[editable]="true"`; «Отмена» and overlay click close without PATCH; successful `saveComponentWeights()` closes the modal then `reloadCard()`; failed PATCH keeps the modal open and still sets `weightError`
- [x] 2.3 Run `cd frontend && npm test` — green for 1.1–1.3

## 3. docs

- [x] 3.1 Update `docs/pages-spec.md` `/health/:productId`: weights editor is admin button → modal, not an always-visible panel; non-admin keeps read-only «Вес» column. Do not change OpenAPI

## 4. frontend/ — Playwright e2e (backend up)

- [x] 4.1 Update `frontend/tests/e2e/health-graph.spec.ts` for **Inline hidden** + **Admin opens modal and saves**: on load `app-component-weight-editor` is not visible; admin opens «Веса компонентов», edits a number input, Save, wait for PATCH `/api/v1/admin/products/`; modal closes; after SPA reload (back + open product, not `page.goto('/health')`) the components table «Вес» shows the new weight; reopen the modal and assert the number input
- [x] 4.2 Run `cd frontend && npm run test:e2e` against running backend (`http://localhost:8080`) — green
