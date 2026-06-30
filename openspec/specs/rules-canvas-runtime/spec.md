# rules-canvas-runtime

## Purpose

Execute saved visual rule canvas (nodes/edges) when processing raw events into console events, including conditions, switch branching, dedup, threshold, and simple correlation.

## Requirements

### Requirement: Canvas-driven rule execution

The system SHALL interpret the saved `canvas` JSON of each enabled processing rule when converting raw events to console events.

#### Scenario: Condition filters event

- **WHEN** a rule canvas has trigger → condition (severity eq critical) → dedup
- **AND** raw event severity is warning
- **THEN** the rule is not applied to that raw event

#### Scenario: Dedup parameters from canvas

- **WHEN** dedup block config specifies merge key fields (source, title, ci)
- **AND** a matching active console event exists
- **THEN** repeat_count increments instead of creating a new console event

#### Scenario: Threshold parameters from canvas

- **WHEN** threshold block config specifies count=3 and windowMin=5
- **AND** 3 critical events occur within 5 minutes for same source and CI
- **THEN** a synthetic fatal threshold event is created

### Requirement: Switch branching

The system SHALL follow one branch from a switch block based on the first matching condition on outgoing edges.

#### Scenario: Switch selects dedup branch

- **WHEN** switch has two branches: condition severity=critical → dedup, default → threshold
- **AND** event severity is critical
- **THEN** only the dedup branch executes

### Requirement: Simple correlation

The system SHALL link events as root/child when a correlation block config is satisfied within a time window.

#### Scenario: Two events correlate

- **WHEN** correlation block specifies count=2 and windowMin=10
- **AND** two matching events arrive within 10 minutes for same source and CI
- **THEN** the first becomes root and the second has root_event_id pointing to it

### Requirement: Fallback for legacy rules

The system SHALL apply legacy ruleType-based behavior when canvas nodes are empty.

#### Scenario: Empty canvas

- **WHEN** rule has ruleType dedup, enabled=true, and canvas nodes=[]
- **THEN** dedup behavior matches pre-change hardcoded logic

### Requirement: Rule validation on save

The system SHALL validate canvas structure when creating or updating a rule.

#### Scenario: Invalid canvas rejected

- **WHEN** user saves a rule without a stream trigger or without an action block
- **THEN** API returns 400 with validation message

### Requirement: Last run timestamp

The system SHALL update `last_run_at` when a rule's action block executes for a raw event.

#### Scenario: Rule applied

- **WHEN** dedup, threshold, or correlation action runs
- **THEN** `last_run_at` is set to current time
