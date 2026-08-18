## ADDED Requirements

### Requirement: Assign sets assignee without in_progress

The system SHALL accept `POST /api/v1/events/{id}/actions` with `action` equal to `assign` and a required `assignedUserId`. The system MUST set `assignedUserId` (and expose `assignedUserName` on the DTO) to that user. Assign MUST NOT change `status` to `in_progress` solely because of assign. The system MUST append `event_action_logs` with `action` equal to `assign`. Existing `take` MUST still assign the current user, set `status` to `in_progress`, and set `takenAt` to now. The operator directory for assign MUST be `GET /api/v1/admin/users?active=true` (no new lookup resource).

#### Scenario: Assign colleague keeps status

- **GIVEN** an active event with status `new` and another active user in the directory
- **WHEN** the operator posts `{ "action": "assign", "assignedUserId": "<other-user-id>" }`
- **THEN** the response is 200
- **AND** `assignedUserId` / `assignedUserName` match the chosen user
- **AND** `status` remains `new` (not `in_progress`)
- **AND** a journal row with `action` `assign` is written

#### Scenario: Take still self-assigns and sets in_progress

- **GIVEN** an active event
- **WHEN** the operator posts `{ "action": "take" }`
- **THEN** `assignedUserId` is the current user
- **AND** `status` is `in_progress`
- **AND** `takenAt` is set to the current time

#### Scenario: Assign without assignedUserId is rejected

- **GIVEN** an active event
- **WHEN** the operator posts `{ "action": "assign" }` without `assignedUserId`
- **THEN** the API returns 400
- **AND** assignee is unchanged

#### Scenario: Unknown assignee is rejected

- **GIVEN** an active event
- **WHEN** the operator posts `action=assign` with an unknown user id
- **THEN** the API returns 404
- **AND** assignee is unchanged

#### Scenario: Inactive assignee is rejected

- **GIVEN** an active event and a user with `active=false`
- **WHEN** the operator posts `action=assign` with that user id
- **THEN** the API returns 400
- **AND** assignee is unchanged
