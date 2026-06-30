import type { HealthLevel } from '../../data/mock';
import { healthLabels } from '../../data/mock';

const healthColors: Record<HealthLevel, string> = {
  fatal: 'bg-[#dc2626] text-white',
  critical: 'bg-[#dc2626] text-white',
  major: 'bg-[#ea580c] text-white',
  warning: 'bg-[#eab308] text-[#1a1d23]',
  ok: 'bg-[#22c55e] text-white',
  unknown: 'bg-[#6b7280] text-white',
};

const healthDots: Record<HealthLevel, string> = {
  fatal: 'bg-[#dc2626]',
  critical: 'bg-[#dc2626]',
  major: 'bg-[#ea580c]',
  warning: 'bg-[#eab308]',
  ok: 'bg-[#22c55e]',
  unknown: 'bg-[#6b7280]',
};

const healthText: Record<HealthLevel, string> = {
  fatal: 'text-[#dc2626]',
  critical: 'text-[#dc2626]',
  major: 'text-[#ea580c]',
  warning: 'text-[#eab308]',
  ok: 'text-[#22c55e]',
  unknown: 'text-[#6b7280]',
};

interface Props {
  level: HealthLevel;
  compact?: boolean;
  percent?: number;
}

export function HealthBadge({ level, compact, percent }: Props) {
  if (compact) {
    return (
      <span className="inline-flex items-center gap-1.5">
        <span className={`w-2 h-2 rounded-full ${healthDots[level]}`} />
        <span className={`text-xs ${healthText[level]}`}>
          {healthLabels[level]}
          {percent !== undefined && ` · ${percent}%`}
        </span>
      </span>
    );
  }
  return (
    <span className={`inline-flex items-center px-2 py-0.5 rounded text-xs font-medium ${healthColors[level]}`}>
      {healthLabels[level]}
      {percent !== undefined && ` · ${percent}%`}
    </span>
  );
}

export function healthBarColor(level: HealthLevel): string {
  return healthDots[level].replace('bg-', '');
}
