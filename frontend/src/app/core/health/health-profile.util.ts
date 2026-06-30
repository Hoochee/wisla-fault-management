import {
  ConfigurationItem,
  Event,
  HealthLevel,
  ProductHealth,
  Severity,
} from '../api/api.models';
import { CiHealthProfile, HealthComponent, HealthTimelinePoint } from './health.models';

const SEVERITY_HEALTH: Record<Severity, { level: HealthLevel; percent: number }> = {
  fatal: { level: 'fatal', percent: 5 },
  critical: { level: 'critical', percent: 18 },
  major: { level: 'major', percent: 42 },
  minor: { level: 'major', percent: 58 },
  warning: { level: 'warning', percent: 72 },
  normal: { level: 'ok', percent: 96 },
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
  if (count === 0) return '#252830';
  const hex = HEAT_BASE[severity];
  const opacity = Math.min(0.5 + count * 0.12, 1);
  const r = parseInt(hex.slice(1, 3), 16);
  const g = parseInt(hex.slice(3, 5), 16);
  const b = parseInt(hex.slice(5, 7), 16);
  return `rgba(${r},${g},${b},${opacity})`;
}

function percentToLevel(percent: number): HealthLevel {
  if (percent <= 10) return 'fatal';
  if (percent <= 25) return 'critical';
  if (percent <= 45) return 'major';
  if (percent <= 70) return 'warning';
  return 'ok';
}

function buildTimeline(basePercent: number, level: HealthLevel): HealthTimelinePoint[] {
  const levels: HealthLevel[] = ['ok', 'ok', 'warning', 'major', 'critical', 'major', 'warning', 'ok', 'ok', 'warning', 'major', 'ok'];
  return Array.from({ length: 12 }, (_, i) => {
    const drift = (i % 3) * 4 - 4;
    const percent = Math.max(5, Math.min(100, basePercent + drift + (i > 6 ? 2 : 0)));
    return {
      time: `${String(i * 2).padStart(2, '0')}:00`,
      level: i === 4 ? level : levels[i],
      percent,
    };
  });
}

function defaultComponents(ci: ConfigurationItem, percent: number, level: HealthLevel): HealthComponent[] {
  const damage = 100 - percent;
  const names =
    ci.ciType === 'server'
      ? ['CPU / Load', 'Дисковая подсистема', 'Сетевой стек']
      : ci.ciType === 'network'
        ? ['Интерфейсы uplink', 'BGP / маршрутизация', 'Питание / PSU']
        : ['Основной компонент', 'Сервисный слой'];
  return names.map((name, idx) => ({
    id: `cmp-${ci.id}-${idx}`,
    name,
    health: idx === 0 ? level : percentToLevel(percent + idx * 12),
    healthPercent: Math.max(5, Math.min(100, percent + idx * 8)),
    damageFromComponents: Math.round(damage * (0.5 - idx * 0.12)),
    damageFromInfluence: idx === 0 ? Math.round(damage * 0.15) : 0,
  }));
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

  const timeline = buildTimeline(healthPercent, currentHealth);
  const percents = timeline.map((p) => p.percent);

  return {
    ciId: ci.id,
    currentHealth,
    healthPercent,
    minToday: percentToLevel(Math.min(...percents)),
    minTodayPercent: Math.min(...percents),
    maxToday: percentToLevel(Math.max(...percents)),
    maxTodayPercent: Math.max(...percents),
    components: defaultComponents(ci, healthPercent, currentHealth),
    dependents: [],
    signalsBySeverity,
    timeline,
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
