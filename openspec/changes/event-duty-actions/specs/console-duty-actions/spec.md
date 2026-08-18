## ADDED Requirements

### Requirement: Duty actions on event card and console selection panel

The Angular SPA SHALL expose **Подтвердить**, **Комментарий**, **Назначить**, and **Скрыть** (presets 15 / 30 / 60 minutes) on the full event card `/console/:eventId` next to existing **Принять в работу** / **Закрыть**, and on the bottom panel of the selected console row on `/console`. There MUST be no bulk actions and no context menu for these actions in this change. The journal MUST show human-readable labels for `ack`, `comment`, `assign`, and `silence`. The card MUST indicate acknowledgement (who/when) and an active silence until `silencedUntil`. Assign MUST load users from `GET /api/v1/admin/users?active=true`.

#### Scenario: Card shows duty action buttons

- **GIVEN** an authenticated operator on `/console/:eventId` for an active event
- **WHEN** the card is rendered
- **THEN** buttons for acknowledge, comment, assign, and silence presets 15/30/60 are visible alongside take and close
- **AND** there is no bulk action control

#### Scenario: Console bottom panel acts on the selected row

- **GIVEN** an authenticated operator on `/console` with a selected table row
- **WHEN** the bottom detail panel is shown
- **THEN** the same duty actions (ack, comment, assign, silence presets) are available for that event
- **AND** no context menu is required to run them
- **AND** no bulk selection toolbar is shown

#### Scenario: Journal labels are human-readable

- **GIVEN** journal rows with actions `ack`, `comment`, `assign`, and `silence`
- **WHEN** the operator opens the journal tab on the card
- **THEN** the UI shows localized labels (Подтверждено / Комментарий / Назначено / Скрыто), not raw enum leftovers only

#### Scenario: Card indicates ack and silence

- **GIVEN** an event that is acknowledged and currently silenced
- **WHEN** the operator opens the card
- **THEN** the UI shows who acknowledged and when
- **AND** the UI shows that the event is hidden until `silencedUntil`

#### Scenario: Assign picker uses active admin users

- **GIVEN** the assign control on the card or console panel
- **WHEN** the operator opens the user picker
- **THEN** the client calls `GET /api/v1/admin/users?active=true`
- **AND** choosing a user posts `action=assign` with that `assignedUserId`
