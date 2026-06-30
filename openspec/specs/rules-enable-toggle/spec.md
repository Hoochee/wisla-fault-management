# rules-enable-toggle

## Purpose

Allow operators to enable or disable processing rules from the UI via PATCH API.

## Requirements

### Requirement: Toggle rule enabled via API

The system SHALL allow updating a processing rule's `enabled` flag via PATCH.

#### Scenario: Disable rule

- **WHEN** client sends PATCH with `enabled: false`
- **THEN** rule is disabled and excluded from event processing

#### Scenario: Enable valid rule

- **WHEN** client sends PATCH with `enabled: true`
- **AND** rule canvas is valid or empty (legacy fallback)
- **THEN** rule is enabled and participates in processing

#### Scenario: Enable invalid canvas rejected

- **WHEN** client sends PATCH with `enabled: true`
- **AND** canvas fails validation
- **THEN** API returns 400 with canvas_validation error

### Requirement: Enable toggle in rules UI

The operator SHALL toggle rule enabled state from the rules list and rule editor.

#### Scenario: Toggle from list

- **WHEN** operator clicks enable/disable on `/rules` row
- **THEN** rule state updates without leaving the page

#### Scenario: Toggle from editor

- **WHEN** operator clicks enable/disable on `/rules/:id`
- **THEN** displayed enabled status updates to match API response
