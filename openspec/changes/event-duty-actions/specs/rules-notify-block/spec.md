## MODIFIED Requirements

### Requirement: Push block in rule canvas

The system SHALL support a `push` action block that delivers in-app notifications to operators when the processed event is not currently silenced (`silencedUntil` is null or in the past). Canvas shape and polling delivery are unchanged.

#### Scenario: Push on rule match

- **WHEN** enabled rule canvas reaches push action for a processed event that is not silenced
- **THEN** a push notification record is created with title and message
- **AND** `last_run_at` is updated

#### Scenario: Client receives push via polling

- **WHEN** authenticated client calls `GET /api/v1/notifications/push?since={timestamp}`
- **THEN** response includes push records created after `since`

#### Scenario: Push skipped while event is silenced

- **GIVEN** a processed event whose `silencedUntil` is in the future and a matching push intent
- **WHEN** `ProcessRawEventBatchService` executes rule intents
- **THEN** `PushNotificationPort.createPush` is not called
- **AND** the canvas push block configuration is unchanged

## ADDED Requirements

### Requirement: Notify delivery is skipped while the event is silenced

When `ProcessRawEventBatchService` executes notify intents for an event whose `silencedUntil` is in the future, the system MUST NOT call `NotificationPort.notify`. Telegram/email channels remain stubs. Canvas validation is unchanged.

#### Scenario: Notify skipped while event is silenced

- **GIVEN** a processed event whose `silencedUntil` is in the future and a matching notify intent
- **WHEN** `ProcessRawEventBatchService` executes rule intents
- **THEN** `NotificationPort.notify` is not called
- **AND** the canvas notify block configuration is unchanged
