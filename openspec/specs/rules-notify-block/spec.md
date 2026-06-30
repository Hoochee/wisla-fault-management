# rules-notify-block

## Purpose

Notify and push action blocks in the rule canvas: notify (Telegram/Email stub) and in-app push via polling.

## Requirements

### Requirement: Notify block in rule canvas

The system SHALL support a `notify` action block in the rule canvas with channel configuration.

#### Scenario: Email channel requires address

- **WHEN** user configures notify block with channel email
- **AND** emailAddress is empty or invalid
- **THEN** API returns 400 on rule save

#### Scenario: Telegram channel is stub

- **WHEN** rule executes notify block with channel telegram
- **THEN** no external message is sent
- **AND** `last_run_at` is updated

#### Scenario: Email channel is stub

- **WHEN** rule executes notify block with channel email and valid emailAddress
- **THEN** no SMTP delivery occurs
- **AND** `last_run_at` is updated

### Requirement: Push block in rule canvas

The system SHALL support a `push` action block that delivers in-app notifications to operators.

#### Scenario: Push on rule match

- **WHEN** enabled rule canvas reaches push action for a processed event
- **THEN** a push notification record is created with title and message
- **AND** `last_run_at` is updated

#### Scenario: Client receives push via polling

- **WHEN** authenticated client calls `GET /api/v1/notifications/push?since={timestamp}`
- **THEN** response includes push records created after `since`

### Requirement: Notify and push as valid actions

The system SHALL accept `notify` and `push` as action blocks for canvas validation.

#### Scenario: Rule with push only

- **WHEN** canvas has stream trigger connected to push block
- **THEN** rule save succeeds
