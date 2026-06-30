# push-toast-dismiss-all Specification

## Purpose
TBD - created by archiving change push-toast-dismiss-all. Update Purpose after archive.
## Requirements
### Requirement: Dismiss all push toasts

The client SHALL provide a control to close all visible push toast notifications at once.

#### Scenario: Button visible when multiple toasts

- **WHEN** two or more push toasts are visible in the toast stack
- **THEN** a «Закрыть все» control is shown

#### Scenario: Button hidden with single toast

- **WHEN** zero or one push toast is visible
- **THEN** the «Закрыть все» control is not shown

#### Scenario: Dismiss all clears stack

- **WHEN** operator clicks «Закрыть все»
- **THEN** all visible push toasts are removed from the UI immediately

#### Scenario: Dismissed toasts do not reappear

- **WHEN** operator dismisses all toasts
- **AND** polling returns the same notification ids again
- **THEN** those notifications are not shown again (existing seen-id behavior)

