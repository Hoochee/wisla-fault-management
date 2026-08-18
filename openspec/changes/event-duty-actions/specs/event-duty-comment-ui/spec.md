## ADDED Requirements

### Requirement: Comment button uses existing event action API

The Angular console SHALL expose a «Комментарий» control on the event card (`/console/:eventId`) and on the selected-row bottom panel (`/console`). Submitting a non-blank comment MUST call the existing `POST /api/v1/events/{id}/actions` with `{ "action": "comment", "comment": "<text>" }`. Comment MUST NOT change status or assignee. An empty or blank comment MUST NOT be sent; if posted, the API MUST return 400 as today. `take` and `close` MUST continue to work.

#### Scenario: Operator comments from the card

- **GIVEN** an authenticated operator viewing `/console/:eventId`
- **WHEN** the operator enters a non-blank comment and confirms
- **THEN** the client posts `{ "action": "comment", "comment": "<text>" }` to `/api/v1/events/{id}/actions`
- **AND** the response is 200
- **AND** the journal tab shows a new `comment` row

#### Scenario: Operator comments from the console bottom panel

- **GIVEN** a selected row on `/console`
- **WHEN** the operator submits a non-blank comment from the bottom panel
- **THEN** the same `POST .../actions` with `action=comment` is sent for that event id
- **AND** the response is 200

#### Scenario: Blank comment is rejected

- **GIVEN** the comment API
- **WHEN** a client posts `action=comment` with a missing or blank `comment`
- **THEN** the API returns 400
- **AND** no journal row is written

#### Scenario: Take and close still work after comment UI

- **GIVEN** the event card with comment, take, and close controls
- **WHEN** the operator runs `take` or `close`
- **THEN** those actions succeed as before (`take` → `in_progress` and self-assign; `close` → `closed`)
