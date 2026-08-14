## ADDED Requirements

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

On `/health/:productId` an administrator SHALL edit component weights, influence type, and CI assignment and persist them via PATCH `components`. Duty and specialist roles MUST see components read-only.

#### Scenario: Admin saves weights from the product card

- **GIVEN** an administrator on `/health/:productId`
- **WHEN** they change CPU weight to 30 and save
- **THEN** the UI PATCHes `components`
- **AND** after reload the card shows weight 30

#### Scenario: Non-admin has no weight editor

- **GIVEN** a specialist on `/health/:productId`
- **WHEN** the page renders
- **THEN** component weights are visible
- **AND** save controls for components are hidden
