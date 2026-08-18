## ADDED Requirements

### Requirement: Silence hides an event for N minutes without closing it

The system SHALL accept `POST /api/v1/events/{id}/actions` with `action` equal to `silence` and `silenceMinutes` greater than 0. The system MUST set `silencedUntil` to now plus N minutes and MUST NOT change `status` or `severity`, MUST NOT close the PROBLEM, and MUST NOT change acknowledgement. There is no `unsilence` action in this change. The UI MAY offer presets 15, 30, and 60 minutes; the API MUST accept any integer `silenceMinutes` > 0.

#### Scenario: Silence sets silencedUntil and keeps PROBLEM

- **GIVEN** an active event with status `new`
- **WHEN** the operator posts `{ "action": "silence", "silenceMinutes": 30 }`
- **THEN** the response is 200
- **AND** `status` and `severity` are unchanged
- **AND** `closedAt` remains null
- **AND** `silencedUntil` equals now plus 30 minutes
- **AND** a journal row with `action` `silence` is written

#### Scenario: SilenceMinutes must be positive

- **GIVEN** an active event
- **WHEN** the operator posts `action=silence` with missing `silenceMinutes` or a value ≤ 0
- **THEN** the API returns 400
- **AND** `silencedUntil` is unchanged

#### Scenario: Silence on closed or archived is rejected

- **GIVEN** an event with status `closed` or `archived`
- **WHEN** the operator posts `action=silence` with `silenceMinutes` > 0
- **THEN** the API returns 409

#### Scenario: Take and close remain allowed on a silenced event

- **GIVEN** a silenced active event
- **WHEN** the operator posts `take` or `close` (including from the card)
- **THEN** `take` sets `in_progress` and self-assign, or `close` closes the event, as today
- **AND** the response is not 409 merely because the event is silenced

### Requirement: Active list hides current silence; detail always returns

`GET /api/v1/events` SHALL omit events whose `silencedUntil` is in the future unless `includeSilenced=true`. The default MUST be `includeSilenced=false`. `GET /api/v1/events/{id}` MUST return the event even while silenced. Health and dashboard counters MUST NOT change in this capability. There is no separate hidden-events list.

#### Scenario: List excludes silenced events by default

- **GIVEN** an event with `silencedUntil` in the future
- **WHEN** a client calls `GET /api/v1/events` without `includeSilenced` or with `includeSilenced=false`
- **THEN** that event is absent from the page items

#### Scenario: includeSilenced returns silenced events

- **GIVEN** an event with `silencedUntil` in the future
- **WHEN** a client calls `GET /api/v1/events?includeSilenced=true`
- **THEN** that event is present in the page items
- **AND** the payload includes `silencedUntil`

#### Scenario: Detail by id always returns a silenced event

- **GIVEN** a silenced event
- **WHEN** a client calls `GET /api/v1/events/{id}`
- **THEN** the response is 200 with that event
- **AND** `silencedUntil` is present

#### Scenario: Expired silence returns to the active list

- **GIVEN** an event whose `silencedUntil` is in the past
- **WHEN** a client calls `GET /api/v1/events` with default `includeSilenced`
- **THEN** the event is present in the page items

### Requirement: Silenced repeats merge but do not notify or push

When a raw event merges into an existing event (dedup) whose `silencedUntil` is in the future, the system MUST increment `repeatCount` and set `lastRepeatAt`, and MUST NOT call `NotificationPort.notify` or `PushNotificationPort.createPush`. After `silencedUntil` expires, subsequent repeats MUST notify and push again according to matching rules.

#### Scenario: Repeat while silenced suppresses notify and push

- **GIVEN** an active duplicate event with `silencedUntil` in the future and enabled notify and push rule intents
- **WHEN** a matching raw event is processed
- **THEN** `repeatCount` increases by one and `lastRepeatAt` is updated
- **AND** `NotificationPort.notify` is not called
- **AND** `PushNotificationPort.createPush` is not called

#### Scenario: Repeat after silence expires notifies again

- **GIVEN** an active duplicate event whose `silencedUntil` is in the past
- **WHEN** a matching raw event is processed with notify and push intents
- **THEN** `NotificationPort.notify` is called
- **AND** `PushNotificationPort.createPush` is called
