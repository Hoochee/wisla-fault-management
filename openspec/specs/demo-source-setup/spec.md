# demo-source-setup

## Purpose

End-to-end UI flow for creating and managing signal sources for demo and production setup, including credentials, lifecycle controls, and connectivity testing.

## Requirements

### Requirement: Credentials after source creation

The system SHALL show a one-time credentials dialog after creating a source with full Webhook URL and API key and copy actions.

#### Scenario: Create Push REST source

- **WHEN** user submits the new source form
- **THEN** UI displays unmasked API key and webhook URL before navigating away

### Requirement: Source lifecycle controls in UI

The system SHALL allow activate/deactivate, edit, delete, and regenerate API key from source detail and list views.

#### Scenario: Activate source

- **WHEN** user toggles source to active on detail page
- **THEN** fm-module updates status and triggers adapter config sync

#### Scenario: Delete source without events

- **WHEN** user confirms delete and source has no dependent events
- **THEN** source is removed from registry

#### Scenario: Delete blocked by events

- **WHEN** delete returns 409
- **THEN** UI offers deactivate instead

### Requirement: Real connectivity test

The system SHALL run adapter probe when user clicks «Тест подключения» and requires ingest API key from session or prompt.

#### Scenario: Probe success

- **WHEN** source is active and adapter forwards probe to fm-module
- **THEN** UI shows success with delivery status
