## ADDED Requirements

### Requirement: Console applies severity query param as query-bar chip

When the operator opens `/console` with a `severity` query parameter, `ConsolePageComponent` SHALL read it from `ActivatedRoute.queryParamMap` and SHALL set the existing query-bar chip `severity=eq` to that value before the first `GET /api/v1/events` from that view. The chip MUST be visible and clearable. The list MUST load through existing `FmApiService.listEvents` with `severity` in the request params. Dashboard severity-card links MUST stay `/console?severity=<key>` (critical, major, minor, warning). Unknown or blank `severity` MUST NOT add a chip. `mapId` and `ci` query params MUST remain ignored in this change. `GET /api/v1/dashboard/summary` and on-screen severity counts MUST NOT change.

#### Scenario: Dashboard Critical opens filtered console

- **GIVEN** the dashboard `/` shows a Critical severity card linking to `/console?severity=critical`
- **WHEN** the operator clicks that card
- **THEN** the browser location is `/console?severity=critical`
- **AND** the query-bar shows a chip `severity = critical`
- **AND** `listEvents` is called with `severity=critical`
- **AND** the events table shows only critical events

#### Scenario: Dashboard Major Minor Warning open filtered console

- **GIVEN** the dashboard `/` shows Major, Minor, and Warning severity cards
- **WHEN** the operator clicks Major (or Minor, or Warning)
- **THEN** the location is `/console?severity=major` (or `minor`, or `warning`)
- **AND** the query-bar chip is `severity =` that key
- **AND** `listEvents` is called with that `severity`
- **AND** the table shows only events of that severity

#### Scenario: Direct URL applies severity without dashboard click

- **GIVEN** the operator is not coming from a dashboard click
- **WHEN** they open `/console?severity=major` directly
- **THEN** the query-bar shows a chip `severity = major`
- **AND** `listEvents` is called with `severity=major`
- **AND** the table shows only major events

#### Scenario: Clearing the severity chip restores the unfiltered list

- **GIVEN** `/console` with chip `severity = major` and a list filtered to major
- **WHEN** the operator clicks the chip remove control
- **THEN** the severity chip is gone
- **AND** a later `listEvents` in this view is called without a `severity` param
- **AND** the table shows the unfiltered list for the current map and polling

#### Scenario: Dashboard severity counts stay unchanged

- **GIVEN** dashboard `/` displays Critical, Major, Minor, and Warning counts from `GET /api/v1/dashboard/summary`
- **WHEN** the operator uses a severity card to open the filtered console and returns to `/`
- **THEN** the four severity cards are still shown
- **AND** counts still come from `severityCounts` on that summary endpoint (no new widgets, no moved counters)
