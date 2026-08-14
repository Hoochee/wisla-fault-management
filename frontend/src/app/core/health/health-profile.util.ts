import {
  ConfigurationItem,
  Event,
  HealthLevel,
  ProductHealth,
  Severity,
} from '../api/api.models';
import { CiHealthProfile } from './health.models';

const SEVERITY_HEALTH: Record<Severity, { level: HealthLevel; percent: number }> = {
  fatal: { level: 'fatal', percent: 0 },
  critical: { level: 'critical', percent: 25 },
  major: { level: 'major', percent: 50 },
  minor: { level: 'major', percent: 62 },
  warning: { level: 'warning', percent: 75 },
  normal: { level: 'ok', percent: 100 },
};

const HEAT_BASE: Record<Severity, string> = {
  fatal: '#dc2626',
  critical: '#dc2626',
  major: '#ea580c',
  minor: '#ca8a04',
  warning: '#eab308',
  normal: '#3b82f6',
};

export const HEALTH_LABELS: Record<HealthLevel, string> = {
  fatal: 'Авария',
  critical: 'Критично',
  major: 'Существенно',
  warning: 'Предупреждение',
  ok: 'Норма',
  unknown: 'Неизвестно',
};

export function getHeatColor(severity: Severity, count: number): string {
  if (count === 0) return '#e2e5ea';
  const hex = HEAT_BASE[severity];
  const opacity = Math.min(0.5 + count * 0.12, 1);
  const r = parseInt(hex.slice(1, 3), 16);
  const g = parseInt(hex.slice(3, 5), 16);
  const b = parseInt(hex.slice(5, 7), 16);
  return `rgba(${r},${g},${b},${opacity})`;
}

export function percentToLevel(percent: number): HealthLevel {
  if (percent <= 0) return 'fatal';
  if (percent <= 25) return 'critical';
  if (percent <= 50) return 'major';
  if (percent <= 75) return 'warning';
  return 'ok';
}

export function getHealthPercentColor(percent: number): string {
  const p = Math.max(0, Math.min(100, percent));
  const stops: { at: number; rgb: [number, number, number] }[] = [
    { at: 0, rgb: [220, 38, 38] },
    { at: 25, rgb: [220, 38, 38] },
    { at: 50, rgb: [234, 88, 12] },
    { at: 75, rgb: [234, 179, 8] },
    { at: 100, rgb: [34, 197, 94] },
  ];
  let lo = stops[0];
  let hi = stops[stops.length - 1];
  for (let i = 0; i < stops.length - 1; i++) {
    if (p >= stops[i].at && p <= stops[i + 1].at) {
      lo = stops[i];
      hi = stops[i + 1];
      break;
    }
  }
  const span = hi.at - lo.at || 1;
  const t = (p - lo.at) / span;
  const r = Math.round(lo.rgb[0] + (hi.rgb[0] - lo.rgb[0]) * t);
  const g = Math.round(lo.rgb[1] + (hi.rgb[1] - lo.rgb[1]) * t);
  const b = Math.round(lo.rgb[2] + (hi.rgb[2] - lo.rgb[2]) * t);
  const opacity = 0.35 + (1 - p / 100) * 0.5;
  return `rgba(${r},${g},${b},${opacity.toFixed(2)})`;
}

export function buildCiHealthProfile(ci: ConfigurationItem, events: Event[]): CiHealthProfile {
  const active = events.filter(
    (e) => e.ciId === ci.id && e.status !== 'closed' && e.status !== 'archived',
  );
  const signalsBySeverity: Record<Severity, number> = {
    fatal: 0,
    critical: 0,
    major: 0,
    minor: 0,
    warning: 0,
    normal: 0,
  };
  for (const e of active) {
    signalsBySeverity[e.severity] += 1;
  }

  let healthPercent = 100;
  let currentHealth: HealthLevel = 'ok';
  if (active.length > 0) {
    const worst = active.reduce((a, b) =>
      SEVERITY_HEALTH[a.severity].percent < SEVERITY_HEALTH[b.severity].percent ? a : b,
    );
    healthPercent = SEVERITY_HEALTH[worst.severity].percent;
    currentHealth = SEVERITY_HEALTH[worst.severity].level;
  }

  return {
    ciId: ci.id,
    currentHealth,
    healthPercent,
    minToday: currentHealth,
    minTodayPercent: healthPercent,
    maxToday: currentHealth,
    maxTodayPercent: healthPercent,
    components: [],
    dependents: [],
    signalsBySeverity,
    timeline: [],
  };
}

export function buildProfiles(
  ciList: ConfigurationItem[],
  events: Event[],
): Record<string, CiHealthProfile> {
  const profiles: Record<string, CiHealthProfile> = {};
  for (const ci of ciList) {
    profiles[ci.id] = buildCiHealthProfile(ci, events);
  }
  return profiles;
}

export function findWorstCi(
  ciList: ConfigurationItem[],
  profiles: Record<string, CiHealthProfile>,
): ConfigurationItem | null {
  if (ciList.length === 0) return null;
  return ciList.reduce((worst, ci) => {
    const h = profiles[ci.id]?.healthPercent ?? 100;
    const w = profiles[worst.id]?.healthPercent ?? 100;
    return h < w ? ci : worst;
  });
}

export function uniqueTenants(products: ProductHealth[]): string[] {
  return [...new Set(products.map((p) => p.tenant).filter(Boolean))];
}

export function uniqueSites(products: ProductHealth[]): string[] {
  return [...new Set(products.map((p) => p.site).filter(Boolean))];
}
