# health-product-crud Specification

## Purpose
TBD - created by archiving change health-product-crud. Update Purpose after archive.
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

