# zabbix-simulator-runtime

## Purpose

Runtime configuration and control of the zabbix-simulator from the source detail UI for demo scenarios without container restarts.

## Requirements

### Requirement: Runtime simulator binding

The zabbix-simulator SHALL accept runtime configuration of webhook target without container restart.

#### Scenario: Bind from source card

- **WHEN** admin clicks «Привязать эмулятор» with valid ingest API key
- **THEN** simulator updates target URL and key and reports status in UI

### Requirement: Test event from UI

The system SHALL allow sending one simulator tick from the source detail page after binding.

#### Scenario: Send test tick

- **WHEN** user clicks «Отправить тестовое событие»
- **THEN** fm-module proxies tick to simulator and user sees new event in console

### Requirement: Simulator auto-tick control

The UI SHALL allow enabling/disabling simulator auto-tick from source detail demo block.

#### Scenario: Pause auto-tick

- **WHEN** user disables auto-tick before demo setup
- **THEN** simulator stops scheduled sends until re-enabled
