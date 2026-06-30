import type { Severity } from '../data/mock';
import { severityLabels } from '../data/mock';

const severityColors: Record<Severity, string> = {
  fatal: 'bg-[#dc2626] text-white',
  critical: 'bg-[#dc2626] text-white',
  major: 'bg-[#ea580c] text-white',
  minor: 'bg-[#ca8a04] text-white',
  warning: 'bg-[#eab308] text-[#1a1d23]',
  normal: 'bg-[#3b82f6] text-white',
};

const severityDots: Record<Severity, string> = {
  fatal: 'bg-[#dc2626]',
  critical: 'bg-[#dc2626]',
  major: 'bg-[#ea580c]',
  minor: 'bg-[#ca8a04]',
  warning: 'bg-[#eab308]',
  normal: 'bg-[#3b82f6]',
};

interface Props {
  severity: Severity;
  compact?: boolean;
}

export function SeverityBadge({ severity, compact }: Props) {
  if (compact) {
    return (
      <span className="inline-flex items-center gap-1.5">
        <span className={`w-2 h-2 rounded-full ${severityDots[severity]}`} />
        <span className="text-xs">{severityLabels[severity]}</span>
      </span>
    );
  }
  return (
    <span className={`inline-flex items-center px-2 py-0.5 rounded text-xs font-medium ${severityColors[severity]}`}>
      {severityLabels[severity]}
    </span>
  );
}
