import type { CiHealthProfile } from '../../data/mock';
import { HealthBadge } from './HealthBadge';

interface Props {
  timeline: CiHealthProfile['timeline'];
}

export function HealthTimelineChart({ timeline }: Props) {
  const max = 100;
  const min = Math.min(...timeline.map((p) => p.percent));

  return (
    <div className="bg-[#252830] border border-[#3a3f4b] rounded-lg p-4">
      <div className="flex items-center justify-between mb-3">
        <h3 className="text-sm font-medium text-white">График здоровья за сутки</h3>
        <span className="text-xs text-gray-500">мин. {min}%</span>
      </div>
      <div className="relative h-32 flex items-end gap-1">
        {timeline.map((point, i) => {
          const height = (point.percent / max) * 100;
          const barColor =
            point.level === 'fatal' || point.level === 'critical' ? '#dc2626'
            : point.level === 'major' ? '#ea580c'
            : point.level === 'warning' ? '#eab308'
            : point.level === 'ok' ? '#22c55e'
            : '#6b7280';
          return (
            <div key={i} className="flex-1 flex flex-col items-center gap-1 min-w-0 group">
              <div
                className="w-full rounded-t transition-opacity group-hover:opacity-80 cursor-pointer"
                style={{ height: `${height}%`, backgroundColor: barColor, minHeight: 4 }}
                title={`${point.time}: ${point.percent}%`}
              />
              {i % 2 === 0 && (
                <span className="text-[9px] text-gray-600 truncate w-full text-center">{point.time}</span>
              )}
            </div>
          );
        })}
      </div>
      <div className="flex items-center gap-3 mt-3 pt-3 border-t border-[#3a3f4b]">
        <span className="text-xs text-gray-500">Текущее:</span>
        {timeline.length > 0 && (
          <HealthBadge level={timeline[timeline.length - 1].level} compact percent={timeline[timeline.length - 1].percent} />
        )}
      </div>
    </div>
  );
}
