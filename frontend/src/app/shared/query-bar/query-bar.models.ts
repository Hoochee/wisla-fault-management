export type QueryField = 'severity' | 'status' | 'title' | 'productId' | 'ciId';
export type QueryOperator = 'eq' | 'ne';

export interface QueryChip {
  id: string;
  field: QueryField;
  operator: QueryOperator;
  value: string;
}

export interface QueryFilterState {
  chips: QueryChip[];
  textSearch: string;
  from?: string;
  to?: string;
}

export const QUERY_FIELD_LABELS: Record<QueryField, string> = {
  severity: 'severity',
  status: 'status',
  title: 'title',
  productId: 'productId',
  ciId: 'ciId',
};

export const SEVERITY_OPTIONS = ['fatal', 'critical', 'major', 'minor', 'warning', 'normal'] as const;
export const STATUS_OPTIONS = ['new', 'in_progress', 'closed', 'archived', 'maintenance', 'deferred'] as const;
