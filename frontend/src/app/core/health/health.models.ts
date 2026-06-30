import { ConfigurationItem, Event, HealthLevel, ProductHealth, Severity } from '../api/api.models';

export type InfluenceType = 'combo' | 'weighted' | 'critical';

export interface HealthComponent {
  id: string;
  name: string;
  health: HealthLevel;
  healthPercent: number;
  damageFromComponents: number;
  damageFromInfluence: number;
}

export interface HealthDependent {
  id: string;
  fqdn: string;
  health: HealthLevel;
  healthPercent: number;
  influenceType: InfluenceType;
  componentName?: string;
}

export interface HealthTimelinePoint {
  time: string;
  level: HealthLevel;
  percent: number;
}

export interface CiHealthProfile {
  ciId: string;
  currentHealth: HealthLevel;
  healthPercent: number;
  minToday: HealthLevel;
  minTodayPercent: number;
  maxToday: HealthLevel;
  maxTodayPercent: number;
  components: HealthComponent[];
  dependents: HealthDependent[];
  signalsBySeverity: Record<Severity, number>;
  timeline: HealthTimelinePoint[];
}

export type OperativeTab = 'signals' | 'health' | 'params' | 'events';

export interface HealthPageContext {
  product: ProductHealth;
  ciList: ConfigurationItem[];
  events: Event[];
  profiles: Record<string, CiHealthProfile>;
}
