# console-column-sort

## Purpose

Sortable columns on the console events grid via API and clickable headers.

## Requirements

### Requirement: Sort events list by column

The system SHALL support sorting processed events via `GET /api/v1/events?sort={field},{direction}`.

#### Scenario: Sort by created date descending

- **WHEN** client requests `sort=createdAt,desc`
- **THEN** events are ordered by `createdAt` newest first

#### Scenario: Sort by repeat count

- **WHEN** client requests `sort=repeatCount,desc`
- **THEN** events with higher `repeatCount` appear first

#### Scenario: Sort by severity rank

- **WHEN** client requests `sort=severity,asc`
- **THEN** events are ordered fatal → critical → … → normal, not alphabetically

#### Scenario: Invalid sort field rejected

- **WHEN** client requests `sort=unknownField,asc`
- **THEN** API returns 400

### Requirement: Console column header sorting

The console events table SHALL allow sorting by clicking column headers.

#### Scenario: Toggle sort direction

- **WHEN** operator clicks the same column header twice
- **THEN** sort direction toggles between asc and desc
- **AND** table reloads with updated order

#### Scenario: Sort persists during polling

- **WHEN** console polling refreshes events
- **THEN** the active sort parameter is included in the request
