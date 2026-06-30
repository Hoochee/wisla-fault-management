# adapter-config-sync

## Purpose

Automatic and on-demand synchronization of source configuration from fm-module to the adapter service, with a public webhook URL for UI display.

## Requirements

### Requirement: Manual and automatic config sync

The adapter SHALL expose an internal sync endpoint and fm-module SHALL call it after source create, status change, or API key regeneration.

#### Scenario: Sync after activation

- **WHEN** source status changes to active
- **THEN** fm-module calls adapter sync within the same request flow

#### Scenario: Public webhook URL

- **WHEN** UI displays webhook URL for a source
- **THEN** URL uses configured `ADAPTER_PUBLIC_URL` and source webhook path key
