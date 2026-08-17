# health-product-crud Specification

## Purpose
Admin CRUD for health products and CI membership, plus PATCH of component slots/weights and COMMON auto-bind for new CIs.
## Requirements
### Requirement: Admin product CRUD API

The system SHALL expose admin-only endpoints to create, read, update, and delete products.

#### Scenario: Create product

- **WHEN** an administrator sends POST `/api/v1/admin/products` with `name`, `code`, `tenant`, `site`, optional `tags`, optional `ciIds`
- **THEN** the system creates a product and returns 201 with the product DTO including `id`

#### Scenario: Update product attributes

- **WHEN** an administrator sends PATCH `/api/v1/admin/products/{id}` with any of `name`, `code`, `tenant`, `site`, `tags`
- **THEN** the system updates only provided fields and returns the updated product DTO

#### Scenario: Non-admin rejected

- **WHEN** a non-administrator calls POST, PATCH, or DELETE on `/api/v1/admin/products`
- **THEN** the API returns 403

#### Scenario: Duplicate code rejected

- **WHEN** POST or PATCH sets `code` that already exists on another product
- **THEN** the API returns 409

### Requirement: Product CI membership from product side

The system SHALL manage M:N links in `product_ci` via `ciIds` on product create and patch.

#### Scenario: Bind CIs on create

- **WHEN** POST includes `ciIds: [uuid, …]`
- **THEN** the system creates `product_ci` rows for each valid CI id

#### Scenario: Replace CI membership on patch

- **WHEN** PATCH includes `ciIds`
- **THEN** the system deletes existing `product_ci` rows for the product and inserts rows for the provided list (empty list clears all links)

#### Scenario: Unknown CI rejected

- **WHEN** `ciIds` contains an id that does not exist
- **THEN** the API returns 400 with validation error

### Requirement: Delete product with linked CIs

The system SHALL prevent accidental deletion of products that still have linked CIs unless membership is cleared first.

#### Scenario: Delete product without CIs

- **WHEN** administrator sends DELETE `/api/v1/admin/products/{id}` and product has no linked CIs
- **THEN** the system returns 204 and removes the product

#### Scenario: Delete product with CIs blocked

- **WHEN** administrator sends DELETE and product has one or more linked CIs
- **THEN** the API returns 409 with message indicating CIs must be unlinked first

### Requirement: Health panel product management UI

The administrator SHALL manage products from `/health` without editing seed data or database directly.

#### Scenario: Create product from health panel

- **WHEN** administrator clicks «+ Продукт» on `/health` and submits the form
- **THEN** a new product appears on the heatmap after reload

#### Scenario: Edit product from health panel

- **WHEN** administrator opens edit for a product and saves changes
- **THEN** the heatmap and detail reflect updated name, tenant, site, and tags

#### Scenario: Manage CI composition on product page

- **WHEN** administrator opens `/health/:productId` composition block
- **THEN** they can add existing CIs to the product and remove links; changes persist via PATCH `ciIds`

#### Scenario: Delete product from UI

- **WHEN** administrator confirms delete and product has no linked CIs
- **THEN** the product is removed from the heatmap

#### Scenario: Delete blocked in UI

- **WHEN** administrator attempts delete while CIs are linked
- **THEN** UI shows 409 message and offers to open composition to unlink CIs first

### Requirement: Read-only health aggregates

The system SHALL NOT allow manual edit of `maxSeverity` or `activeEventCount` on product write APIs; these remain computed from active events of linked CIs.

#### Scenario: Aggregates computed after CI bind

- **WHEN** product is linked to CIs with active events
- **THEN** GET `/api/v1/health/products/{id}` returns computed `maxSeverity` and `activeEventCount`

### Requirement: Admin PATCH product components and weights

An administrator SHALL create, replace, or update product health components via `PATCH /api/v1/admin/products/{id}` with optional `components: [{code, name, weight, influenceType, criticalThreshold, ciIds}]`. Existing product attribute and `ciIds` CRUD behaviour SHALL stay unchanged when `components` is omitted. Non-administrators SHALL receive 403. At least one component weight MUST be greater than 0; otherwise the API returns 400. A CI MUST belong to at most one component of the same product.

#### Scenario: Replace component slots

- **GIVEN** an administrator and a product
- **WHEN** they PATCH `/api/v1/admin/products/{id}` with `components` POWER (weight 20, critical), CPU (weight 40, weighted), HDD (weight 15, weighted), AVAILABILITY (weight 25, critical)
- **THEN** `product_component` rows match that list
- **AND** the response includes the saved components

#### Scenario: Omit components leaves slots unchanged

- **GIVEN** a product that already has components
- **WHEN** an administrator PATCHes only `name`
- **THEN** `product_component` rows are not deleted or replaced

#### Scenario: Zero total weight rejected

- **GIVEN** a PATCH whose component weights are all 0
- **WHEN** the request is processed
- **THEN** the API returns 400

#### Scenario: Non-admin cannot patch components

- **GIVEN** an authenticated non-administrator
- **WHEN** they PATCH `components`
- **THEN** the API returns 403

#### Scenario: CI cannot join two components of one product

- **GIVEN** a PATCH that lists the same `ciId` under POWER and CPU
- **WHEN** the request is processed
- **THEN** the API returns 400

### Requirement: New product CI membership binds to COMMON

When `PATCH` or `POST` includes `ciIds`, the system SHALL keep replacing `product_ci` as today and SHALL also bind each newly added CI to the product's `COMMON` component when that CI is not already in `product_component_ci` for the product. If `COMMON` does not exist, the system SHALL create it (`code=COMMON`, `weight=100`, `influenceType=weighted`). Existing CRUD scenarios for `product_ci` remain valid.

#### Scenario: New CI lands on COMMON

- **GIVEN** a product with COMMON and POWER slots and a CI not yet linked
- **WHEN** an administrator PATCHes `ciIds` including that CI
- **THEN** a `product_ci` row is created
- **AND** a `product_component_ci` row links the CI to COMMON

#### Scenario: Already slotted CI is not moved

- **GIVEN** a CI already linked to POWER
- **WHEN** `ciIds` is patched and still contains that CI
- **THEN** the CI remains on POWER
- **AND** it is not duplicated onto COMMON

#### Scenario: COMMON created on first CI bind

- **GIVEN** a product with no `product_component` rows
- **WHEN** POST or PATCH supplies `ciIds`
- **THEN** a COMMON component is created
- **AND** each new CI is linked to it

### Requirement: Health product UI edits component weights

On `/health/:productId` the always-visible inline component-weight editor panel MUST NOT appear on the product card. An administrator SHALL open a modal from a button labelled «Веса компонентов» on that page, edit component weights (0–100), influence type, and CI assignment, and persist them via PATCH `components`. Duty and specialist roles MUST NOT see the button or the editor; they MUST still see the read-only «Вес» column in the components table.

#### Scenario: Inline weight editor is hidden on the product card

- **GIVEN** an administrator on `/health/:productId` with the weight modal closed
- **WHEN** the page renders
- **THEN** `app-component-weight-editor` is not in the document

#### Scenario: Admin opens modal and saves weights

- **GIVEN** an administrator on `/health/:productId`
- **WHEN** they click «Веса компонентов», change a component weight to 30, and save
- **THEN** the UI PATCHes `components`
- **AND** the weight modal closes
- **AND** after reload the components table «Вес» column shows 30

#### Scenario: Admin cancels without saving

- **GIVEN** an administrator with the weight modal open and a changed draft weight
- **WHEN** they cancel or close the modal
- **THEN** the UI does not PATCH `components`
- **AND** when they reopen the modal the editor shows the last persisted weights

#### Scenario: Non-admin has no weight editor

- **GIVEN** a specialist on `/health/:productId` (`isAdmin` is false)
- **WHEN** the page renders
- **THEN** the components table shows the «Вес» column
- **AND** the «Веса компонентов» button and `app-component-weight-editor` are not in the document

