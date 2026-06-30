import { Event, RawEvent } from '../../core/api/api.models';
import { QueryChip, QueryFilterState } from './query-bar.models';

function matchesText(value: string, search: string): boolean {
  return value.toLowerCase().includes(search.toLowerCase());
}

function matchesChipTitle(title: string, chip: QueryChip): boolean {
  const hit = matchesText(title, chip.value);
  return chip.operator === 'eq' ? hit : !hit;
}

function matchesChipField(actual: string, chip: QueryChip): boolean {
  const hit = actual === chip.value;
  return chip.operator === 'eq' ? hit : !hit;
}

function inTimeRange(iso: string, from?: string, to?: string): boolean {
  const ts = new Date(iso).getTime();
  if (from && ts < new Date(from).getTime()) return false;
  if (to && ts > new Date(to).getTime()) return false;
  return true;
}

function applyChipsClient<T extends { title: string; severity: string; status?: string }>(
  items: T[],
  chips: QueryChip[],
): T[] {
  return items.filter((item) =>
    chips.every((chip) => {
      if (chip.field === 'title') return matchesChipTitle(item.title, chip);
      if (chip.field === 'severity') return matchesChipField(item.severity, chip);
      if (chip.field === 'status' && item.status) return matchesChipField(item.status, chip);
      return true;
    }),
  );
}

export type EventSortField =
  | 'status'
  | 'severity'
  | 'title'
  | 'nodeFqdn'
  | 'systemName'
  | 'repeatCount'
  | 'lastRepeatAt'
  | 'createdAt';

export type SortDirection = 'asc' | 'desc';

export interface EventSortState {
  field: EventSortField;
  direction: SortDirection;
}

export function buildEventApiParams(
  filter: QueryFilterState,
  mapId?: string | null,
  sort?: EventSortState | null,
): Record<string, string> {
  const params: Record<string, string> = {};
  if (mapId) params['mapId'] = mapId;

  for (const chip of filter.chips) {
    if (chip.operator === 'eq' && chip.field === 'status') params['status'] = chip.value;
    if (chip.operator === 'eq' && chip.field === 'severity') params['severity'] = chip.value;
  }
  if (filter.textSearch) params['query'] = filter.textSearch;
  if (filter.from) params['from'] = filter.from;
  if (filter.to) params['to'] = filter.to;
  if (sort) params['sort'] = `${sort.field},${sort.direction}`;
  return params;
}

export function buildRawEventApiParams(
  filter: QueryFilterState,
): Record<string, string | number | boolean> {
  const params: Record<string, string | number | boolean> = { size: 200 };
  for (const chip of filter.chips) {
    if (chip.operator === 'eq' && chip.field === 'severity') params['severity'] = chip.value;
  }
  if (filter.textSearch) params['query'] = filter.textSearch;
  if (filter.from) params['from'] = filter.from;
  if (filter.to) params['to'] = filter.to;
  return params;
}

export function applyClientEventFilter(events: Event[], filter: QueryFilterState): Event[] {
  let result = events;
  const clientChips = filter.chips.filter((c) => c.operator === 'ne' || c.field === 'title');
  if (clientChips.length) result = applyChipsClient(result, clientChips);
  if (filter.textSearch) {
    result = result.filter(
      (e) => matchesText(e.title, filter.textSearch) || matchesText(e.description ?? '', filter.textSearch),
    );
  }
  if (filter.from || filter.to) {
    result = result.filter((e) => inTimeRange(e.createdAt, filter.from, filter.to));
  }
  return result;
}

export function applyClientRawEventFilter(events: RawEvent[], filter: QueryFilterState): RawEvent[] {
  let result = events;
  const clientChips = filter.chips.filter((c) => c.operator === 'ne' || c.field === 'title' || c.field === 'status');
  if (clientChips.length) {
    result = result.filter((item) =>
      clientChips.every((chip) => {
        if (chip.field === 'title') return matchesChipTitle(item.title, chip);
        if (chip.field === 'severity') return matchesChipField(item.severity, chip);
        return true;
      }),
    );
  }
  if (filter.textSearch) {
    result = result.filter((e) => matchesText(e.title, filter.textSearch));
  }
  if (filter.from || filter.to) {
    result = result.filter((e) => inTimeRange(e.occurredAt ?? e.createdAt, filter.from, filter.to));
  }
  return result;
}
