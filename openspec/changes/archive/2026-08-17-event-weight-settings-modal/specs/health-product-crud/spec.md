## MODIFIED Requirements

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
