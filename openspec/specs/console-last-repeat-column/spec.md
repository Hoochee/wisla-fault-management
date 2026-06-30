# console-last-repeat-column

## Purpose

Show when the last duplicate signal was received for deduplicated events on the console grid.

## Requirements

### Requirement: Last repeat timestamp on console grid

The console events table SHALL display when the last duplicate signal was received for deduplicated events.

#### Scenario: Event with repeats shows last repeat time

- **WHEN** operator views `/console`
- **AND** an event has `repeatCount` greater than 1 and `lastRepeatAt` set
- **THEN** the row shows formatted `lastRepeatAt` in the «Последний повтор» column

#### Scenario: Event without repeats shows dash

- **WHEN** an event has `repeatCount` of 1 or no `lastRepeatAt`
- **THEN** the «Последний повтор» column shows «—»
