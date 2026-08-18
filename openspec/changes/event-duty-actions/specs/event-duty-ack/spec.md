## ADDED Requirements

### Requirement: Acknowledge does not change event status

The system SHALL accept `POST /api/v1/events/{id}/actions` with `action` equal to `ack` on an active event (`new` or `in_progress`). Acknowledge MUST NOT change `status`, MUST NOT set `closedAt`, and MUST NOT set silence. The system MUST persist `acknowledgedAt` (current time) and `acknowledgedByUserId` (authenticated user) and append an `event_action_logs` row with `action` equal to `ack`. There is no un-acknowledge action.

#### Scenario: Ack keeps status and writes audit columns

- **GIVEN** an active event with status `new` and empty acknowledgement fields
- **WHEN** an authenticated operator posts `{ "action": "ack" }` to `/api/v1/events/{id}/actions`
- **THEN** the response is 200
- **AND** `status` remains `new`
- **AND** `closedAt` is null
- **AND** `acknowledgedAt` and `acknowledgedByUserId` are set to the actor and current time
- **AND** `event_action_logs` contains a row with `action` `ack`
- **AND** the event still appears in `GET /api/v1/events` (silence is not applied)

#### Scenario: Repeat ack updates timestamp

- **GIVEN** an active event that is already acknowledged
- **WHEN** the operator posts `action=ack` again
- **THEN** the response is 200
- **AND** `acknowledgedAt` is updated
- **AND** `acknowledgedByUserId` is the latest actor
- **AND** a new journal row with `action` `ack` is appended

#### Scenario: Ack on closed or archived is rejected

- **GIVEN** an event with status `closed` or `archived`
- **WHEN** the operator posts `action=ack`
- **THEN** the API returns 409
- **AND** acknowledgement columns are unchanged
